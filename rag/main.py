"""
BIS RAG Service — FastAPI
=========================
Endpoints:
  POST /rag/query             — blocking RAG query
  POST /rag/stream            — streaming SSE tokens
  POST /rag/ingest/url        — ingest a single BIS URL (SSRF-protected)
  POST /rag/ingest/pdf        — upload and ingest a PDF (streamed, up to 50MB)
  POST /rag/crawl             — asynchronous crawl job trigger
  GET  /rag/crawl/{id}/status — crawl job status and counts
  GET  /health                — health check
"""

import asyncio
from contextlib import asynccontextmanager
import ipaddress
import socket
import tempfile
import uuid
from pathlib import Path
from urllib.parse import urlparse
from typing import Optional

import httpx
import structlog
from fastapi import FastAPI, UploadFile, File, Form, HTTPException, BackgroundTasks
from fastapi.responses import StreamingResponse
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

from models import RagRequest, RagResponse, IngestRequest
from pipeline import run_pipeline, stream_pipeline
from ingestion.ingestor import ingest_pdf, ensure_collection
from ingestion.crawler import crawl
from config import settings

structlog.configure(
    wrapper_class=structlog.make_filtering_bound_logger(
        __import__("logging").getLevelName(settings.log_level)
    )
)

log = structlog.get_logger()

@asynccontextmanager
async def lifespan(app: FastAPI):
    log.info("BIS RAG service starting up")
    try:
        loop = asyncio.get_event_loop()
        await loop.run_in_executor(None, ensure_collection)
        log.info("Qdrant collection ready")
    except Exception as e:
        log.warning("Could not ensure Qdrant collection on startup", error=str(e))
    yield


app = FastAPI(
    title="BIS RAG Service",
    description="Retrieval-Augmented Generation pipeline for BIS AI Assistant",
    version="1.0.0",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:3000", "http://localhost:8080"],
    allow_methods=["*"],
    allow_headers=["*"],
)

MAX_UPLOAD_BYTES = 50 * 1024 * 1024  # 50 MB
ALLOWED_INGEST_HOSTS = ("bis.gov.in",)


def is_allowed_bis_url(url: str) -> bool:
    """Validate that the URL belongs to BIS and does not resolve to private or internal IP ranges (SSRF defense)."""
    parsed = urlparse(url)
    if parsed.scheme != "https" or not parsed.hostname:
        return False
    host = parsed.hostname.lower()
    if not any(host == domain or host.endswith(f".{domain}") for domain in ALLOWED_INGEST_HOSTS):
        return False

    # SSRF Protection: Resolve hostname and ensure no private/link-local/loopback IP is returned
    try:
        addr_info = socket.getaddrinfo(host, 443, proto=socket.IPPROTO_TCP)
        for _, _, _, _, sockaddr in addr_info:
            ip_str = sockaddr[0]
            ip = ipaddress.ip_address(ip_str)
            if ip.is_private or ip.is_loopback or ip.is_link_local or ip.is_reserved or ip.is_multicast:
                log.warning("SSRF blocked: host resolved to restricted IP", host=host, ip=ip_str)
                return False
    except Exception as e:
        log.warning("DNS resolution failed for ingestion URL", host=host, error=str(e))
        return False

    return True


# ── Health ────────────────────────────────────────────────────

@app.get("/health")
async def health():
    return {"status": "ok", "service": "bis-rag"}


# ── RAG endpoints ─────────────────────────────────────────────

@app.post("/rag/query", response_model=RagResponse)
async def query(request: RagRequest):
    """Blocking RAG query — returns complete answer with citations."""
    log.info("RAG query received", query=request.query[:80], lang=request.lang)
    try:
        return await run_pipeline(request)
    except Exception as e:
        log.error("RAG pipeline error", error=str(e))
        raise HTTPException(status_code=500, detail=f"RAG pipeline error: {e}")


@app.post("/rag/stream")
async def stream(request: RagRequest):
    """Streaming RAG — yields SSE events: citations first, then tokens."""
    log.info("Streaming query", query=request.query[:80])
    return StreamingResponse(
        stream_pipeline(request),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "X-Accel-Buffering": "no",
        },
    )


# ── Ingestion endpoints ───────────────────────────────────────

@app.post("/rag/ingest/pdf")
async def ingest_pdf_endpoint(
    file: UploadFile = File(...),
    title: str = Form(...),
    doc_type: str = Form("IS_STANDARD"),
    is_number: str = Form(None),
    document_id: str = Form(None),
):
    """Upload and ingest a PDF into the vector store."""
    if not file.filename.lower().endswith(".pdf"):
        raise HTTPException(400, "Only PDF files are supported")

    with tempfile.NamedTemporaryFile(suffix=".pdf", delete=False) as tmp:
        total = 0
        header_checked = False
        while chunk := await file.read(1024 * 1024):
            total += len(chunk)
            if total > MAX_UPLOAD_BYTES:
                tmp.close()
                Path(tmp.name).unlink(missing_ok=True)
                raise HTTPException(413, "PDF upload exceeds the 50 MB limit")
            tmp.write(chunk)
            if not header_checked and total >= 5:
                tmp.seek(0)
                header = tmp.read(5)
                tmp.seek(total)
                if not header.startswith(b"%PDF-"):
                    tmp.close()
                    Path(tmp.name).unlink(missing_ok=True)
                    raise HTTPException(400, "Invalid PDF header: file is not a valid PDF")
                header_checked = True

        tmp_path = Path(tmp.name)

    doc_metadata = {
        "title": title,
        "doc_type": doc_type,
        "is_number": is_number,
        "document_id": document_id,
        "source_file": file.filename,
    }

    try:
        result = ingest_pdf(tmp_path, doc_metadata)
    finally:
        tmp_path.unlink(missing_ok=True)

    if result.error:
        raise HTTPException(500, detail=result.error)

    return {
        "document_id": result.document_id,
        "title": result.title,
        "chunks_created": result.chunks_created,
        "status": "ingested",
    }


@app.post("/rag/ingest/url")
async def ingest_url_endpoint(request: IngestRequest):
    """Fetch a BIS URL safely (SSRF-protected), extract text, and ingest it."""
    if not request.source_url:
        raise HTTPException(400, "source_url is required")
    if not is_allowed_bis_url(request.source_url):
        raise HTTPException(400, "Only HTTPS URLs hosted by approved bis.gov.in domains without internal IP resolution are allowed")

    try:
        async with httpx.AsyncClient(timeout=30) as client:
            async with client.stream("GET", request.source_url, follow_redirects=False) as resp:
                resp.raise_for_status()

                content_type = resp.headers.get("content-type", "").lower()
                if "pdf" not in content_type and not request.source_url.lower().endswith(".pdf"):
                    raise HTTPException(400, "URL does not point to a PDF resource")

                with tempfile.NamedTemporaryFile(suffix=".pdf", delete=False) as tmp:
                    total = 0
                    header_checked = False
                    async for chunk in resp.aiter_bytes():
                        total += len(chunk)
                        if total > MAX_UPLOAD_BYTES:
                            tmp.close()
                            Path(tmp.name).unlink(missing_ok=True)
                            raise HTTPException(413, "Remote PDF exceeds the 50 MB limit")
                        tmp.write(chunk)
                        if not header_checked and total >= 5:
                            tmp.seek(0)
                            header = tmp.read(5)
                            tmp.seek(total)
                            if not header.startswith(b"%PDF-"):
                                tmp.close()
                                Path(tmp.name).unlink(missing_ok=True)
                                raise HTTPException(400, "Downloaded remote file does not have valid PDF magic header")
                            header_checked = True

                    tmp_path = Path(tmp.name)

            try:
                result = ingest_pdf(tmp_path, request.model_dump())
            finally:
                tmp_path.unlink(missing_ok=True)

        if result.error:
            raise HTTPException(500, result.error)

        return {"chunks_created": result.chunks_created, "status": "ingested"}

    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(500, str(e))


# ── Crawl endpoints (tracked jobs) ───────────────────────────

crawl_jobs: dict[str, dict] = {}


class CrawlRequest(BaseModel):
    jobId: Optional[str] = None
    jobType: str = "INCREMENTAL"


async def _run_crawl_background(job_id: str, job_type: str):
    try:
        result = await crawl(job_type)
        crawl_jobs[job_id].update({
            "status": "DONE",
            "totalFound": result.total_found,
            "docsIngested": result.docs_ingested,
            "docsFailed": result.docs_failed,
            "docsSkipped": result.docs_skipped,
            "error": None,
        })
        log.info("Crawl job finished successfully", job_id=job_id, ingested=result.docs_ingested)
    except Exception as e:
        log.error("Crawl job failed", job_id=job_id, error=str(e))
        crawl_jobs[job_id].update({
            "status": "FAILED",
            "error": str(e),
        })


@app.post("/rag/crawl")
async def crawl_endpoint(request: CrawlRequest, background_tasks: BackgroundTasks):
    """
    Trigger a BIS portal crawl in the background and return running job state.
    Callers can poll /rag/crawl/{job_id}/status for updates.
    """
    job_id = request.jobId or str(uuid.uuid4())
    log.info("Crawl triggered", job_id=job_id, job_type=request.jobType)

    crawl_jobs[job_id] = {
        "jobId": job_id,
        "jobType": request.jobType,
        "status": "RUNNING",
        "totalFound": 0,
        "docsIngested": 0,
        "docsFailed": 0,
        "docsSkipped": 0,
        "error": None,
    }

    background_tasks.add_task(_run_crawl_background, job_id, request.jobType)
    return {"jobId": job_id, "status": "RUNNING"}


@app.get("/rag/crawl/{job_id}/status")
async def crawl_status_endpoint(job_id: str):
    """Get the current status and counters for an active or finished crawl job."""
    if job_id not in crawl_jobs:
        raise HTTPException(404, "Crawl job not found")
    return crawl_jobs[job_id]


# ── Startup handled by lifespan context manager (see top of file) ────────────
