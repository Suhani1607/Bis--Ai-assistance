"""
BIS Portal Crawler
==================
Crawls BIS website for publicly listed IS documents and scheme manuals,
downloads PDFs, and passes them to the ingestion pipeline.

Registered BIS document source URLs:
  - Standards listing: https://www.bis.gov.in/index.php/standards/
  - CRS product list:  https://www.bis.gov.in/index.php/schemes/product-certification-schemes/crs/
  - Hallmarking:       https://www.bis.gov.in/index.php/hallmarking/
"""

import asyncio
import hashlib
import tempfile
from dataclasses import dataclass, field
from pathlib import Path
from typing import Optional

import httpx
import structlog
from tenacity import retry, stop_after_attempt, wait_exponential

from ingestion.ingestor import ingest_pdf

log = structlog.get_logger()

# ── Seed URLs ─────────────────────────────────────────────────
# These are stable BIS document URLs to bootstrap the knowledge base.
# Extend this list as more PDFs are identified.

SEED_DOCUMENTS = [
    {
        "url": "https://www.bis.gov.in/wp-content/uploads/2020/03/Scheme-I-guidelines.pdf",
        "title": "BIS Scheme-I Guidelines for Grant of Licence",
        "doc_type": "SCHEME_MANUAL",
        "is_number": None,
    },
    {
        "url": "https://www.bis.gov.in/wp-content/uploads/2020/03/FMCS-guidelines.pdf",
        "title": "Foreign Manufacturer Certification Scheme (FMCS) Guidelines",
        "doc_type": "SCHEME_MANUAL",
        "is_number": None,
    },
    {
        "url": "https://www.bis.gov.in/wp-content/uploads/2020/03/hallmarking-regulations-2018.pdf",
        "title": "BIS (Hallmarking) Regulations 2018",
        "doc_type": "HALLMARKING",
        "is_number": None,
    },
    {
        "url": "https://www.bis.gov.in/wp-content/uploads/2020/03/CRS-guidelines.pdf",
        "title": "Compulsory Registration Scheme (CRS) Guidelines",
        "doc_type": "SCHEME_MANUAL",
        "is_number": None,
    },
    {
        "url": "https://www.bis.gov.in/wp-content/uploads/2020/03/BIS-CA-Regulations-2018.pdf",
        "title": "BIS (Conformity Assessment) Regulations 2018",
        "doc_type": "REGULATION",
        "is_number": None,
    },
]


@dataclass
class CrawlResult:
    total_found: int = 0
    docs_ingested: int = 0
    docs_skipped: int = 0
    docs_failed: int = 0
    errors: list[str] = field(default_factory=list)


# ── Checksum helpers ─────────────────────────────────────────

def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


# ── HTTP helpers ─────────────────────────────────────────────

@retry(stop=stop_after_attempt(3), wait=wait_exponential(min=2, max=15))
async def fetch_pdf(url: str, client: httpx.AsyncClient) -> Optional[bytes]:
    """Download a PDF from a URL. Returns bytes or None on failure."""
    try:
        resp = await client.get(url, follow_redirects=True, timeout=60)
        resp.raise_for_status()
        content_type = resp.headers.get("content-type", "")
        if "pdf" not in content_type and not url.lower().endswith(".pdf"):
            log.warning("URL does not appear to be a PDF", url=url, content_type=content_type)
            return None
        return resp.content
    except Exception as e:
        log.error("Failed to fetch URL", url=url, error=str(e))
        raise


# ── Main crawler ─────────────────────────────────────────────

async def crawl(job_type: str = "INCREMENTAL") -> CrawlResult:
    """
    Crawl BIS portal for PDF documents and ingest them.
    FULL_CRAWL: all seed URLs
    INCREMENTAL: only new documents (checksum-based dedup)
    """
    result = CrawlResult()
    ingested_checksums: set[str] = set()  # in-memory dedup for this run

    # In production this would query the database for existing checksums
    # to avoid re-ingesting already-processed documents.

    async with httpx.AsyncClient(
        headers={"User-Agent": "BIS-AI-Assistant/1.0 (document indexing bot)"},
        timeout=httpx.Timeout(60, connect=10),
    ) as client:

        for doc_meta in SEED_DOCUMENTS:
            url = doc_meta["url"]
            result.total_found += 1
            log.info("Fetching document", url=url, title=doc_meta["title"])

            try:
                pdf_bytes = await fetch_pdf(url, client)
                if pdf_bytes is None:
                    result.docs_skipped += 1
                    continue

                checksum = sha256_bytes(pdf_bytes)
                if checksum in ingested_checksums:
                    log.info("Skipping duplicate document", url=url)
                    result.docs_skipped += 1
                    continue

                # Write to temp file and ingest
                with tempfile.NamedTemporaryFile(suffix=".pdf", delete=False) as tmp:
                    tmp.write(pdf_bytes)
                    tmp_path = Path(tmp.name)

                metadata = {
                    **doc_meta,
                    "source_url": url,
                    "checksum": checksum,
                }
                ingest_result = ingest_pdf(tmp_path, metadata)
                tmp_path.unlink(missing_ok=True)

                if ingest_result.error:
                    result.docs_failed += 1
                    result.errors.append(f"{url}: {ingest_result.error}")
                else:
                    result.docs_ingested += 1
                    ingested_checksums.add(checksum)
                    log.info("Ingested", title=doc_meta["title"],
                             chunks=ingest_result.chunks_created)

                # Polite delay between requests
                await asyncio.sleep(2)

            except Exception as e:
                log.error("Crawl error", url=url, error=str(e))
                result.docs_failed += 1
                result.errors.append(f"{url}: {str(e)}")

    log.info("Crawl complete", **{
        "total": result.total_found,
        "ingested": result.docs_ingested,
        "skipped": result.docs_skipped,
        "failed": result.docs_failed,
    })
    return result
