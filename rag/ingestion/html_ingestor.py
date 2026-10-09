"""
HTML Ingestor — BIS FAQ and web page ingestion
===============================================
Converts BIS FAQ/HTML pages into text chunks and ingests them into Qdrant.
Complements the PDF ingestor for web-only content.
"""
import re
import uuid
from dataclasses import dataclass, field
from pathlib import Path
from typing import Optional

import httpx
import structlog
from langchain_text_splitters import RecursiveCharacterTextSplitter
from tenacity import retry, stop_after_attempt, wait_exponential

from config import settings
from ingestion.ingestor import (
    DocumentChunk, IngestionResult,
    extract_is_number, extract_clause, extract_section_title,
    upsert_chunks, BATCH_SIZE,
)

log = structlog.get_logger()

# HTML tag stripping
HTML_TAG_RE      = re.compile(r"<[^>]+>")
WHITESPACE_RE    = re.compile(r"\s{2,}")
SCRIPT_STYLE_RE  = re.compile(r"<(script|style)[^>]*>.*?</\1>", re.DOTALL | re.IGNORECASE)

# BIS FAQ / guidance pages to ingest
BIS_HTML_PAGES = [
    {
        "url": "https://www.bis.gov.in/index.php/schemes/product-certification-schemes/scheme-i/",
        "title": "BIS Scheme-I (ISI Mark) — Overview",
        "doc_type": "SCHEME_MANUAL",
    },
    {
        "url": "https://www.bis.gov.in/index.php/hallmarking/",
        "title": "BIS Hallmarking — Overview",
        "doc_type": "HALLMARKING",
    },
    {
        "url": "https://www.bis.gov.in/index.php/schemes/product-certification-schemes/crs/",
        "title": "Compulsory Registration Scheme (CRS) — Overview",
        "doc_type": "SCHEME_MANUAL",
    },
    {
        "url": "https://www.bis.gov.in/index.php/schemes/product-certification-schemes/fmcs/",
        "title": "Foreign Manufacturer Certification Scheme (FMCS)",
        "doc_type": "SCHEME_MANUAL",
    },
    {
        "url": "https://www.bis.gov.in/index.php/about-bis/",
        "title": "About BIS — Bureau of Indian Standards",
        "doc_type": "FAQ",
    },
]

splitter = RecursiveCharacterTextSplitter(
    chunk_size=settings.chunk_size,
    chunk_overlap=settings.chunk_overlap,
    separators=["\n\n", "\n", ". ", " "],
)


def strip_html(html: str) -> str:
    """Remove tags, scripts, styles; collapse whitespace."""
    text = SCRIPT_STYLE_RE.sub(" ", html)
    text = HTML_TAG_RE.sub(" ", text)
    text = WHITESPACE_RE.sub(" ", text)
    # Clean up common HTML entities
    text = text.replace("&nbsp;", " ").replace("&amp;", "&") \
               .replace("&lt;", "<").replace("&gt;", ">") \
               .replace("&quot;", '"').replace("&#39;", "'")
    return text.strip()


@retry(stop=stop_after_attempt(3), wait=wait_exponential(min=2, max=10))
async def fetch_html(url: str, client: httpx.AsyncClient) -> Optional[str]:
    try:
        resp = await client.get(url, follow_redirects=True, timeout=20)
        resp.raise_for_status()
        return resp.text
    except Exception as e:
        log.warning("HTML fetch failed", url=url, error=str(e))
        return None


def html_to_chunks(
    text: str,
    doc_metadata: dict,
) -> list[DocumentChunk]:
    """Split extracted HTML text into DocumentChunks."""
    raw_chunks = splitter.split_text(text)
    chunks = []
    for chunk_text in raw_chunks:
        if len(chunk_text.strip()) < 50:
            continue
        chunks.append(DocumentChunk(
            chunk_id=str(uuid.uuid4()),
            content=chunk_text.strip(),
            is_number=doc_metadata.get("is_number") or extract_is_number(chunk_text),
            clause_ref=extract_clause(chunk_text),
            section_title=extract_section_title(chunk_text),
            doc_type=doc_metadata.get("doc_type", "FAQ"),
            source_url=doc_metadata.get("url"),
            page_number=None,
            metadata=doc_metadata,
        ))
    return chunks


async def ingest_html_page(url: str, doc_metadata: dict) -> IngestionResult:
    """Fetch an HTML page and ingest its text content into Qdrant."""
    import asyncio
    title = doc_metadata.get("title", url)

    async with httpx.AsyncClient(
        headers={"User-Agent": "BIS-AI-Assistant/1.0 (document indexer)"},
        timeout=30,
    ) as client:
        html = await fetch_html(url, client)

    if not html:
        return IngestionResult(str(uuid.uuid4()), title, 0, 0, "Failed to fetch page")

    text = strip_html(html)
    if len(text) < 100:
        return IngestionResult(str(uuid.uuid4()), title, 0, 0, "Page content too short")

    chunks = html_to_chunks(text, {**doc_metadata, "url": url})
    if not chunks:
        return IngestionResult(str(uuid.uuid4()), title, 0, 0, "No chunks generated")

    try:
        upsert_chunks(chunks)
        log.info("HTML page ingested", url=url, chunks=len(chunks))
        return IngestionResult(str(uuid.uuid4()), title, len(chunks), 0)
    except Exception as e:
        log.error("HTML upsert failed", url=url, error=str(e))
        return IngestionResult(str(uuid.uuid4()), title, 0, 0, str(e))


async def ingest_all_html_pages() -> dict:
    """Ingest all configured BIS HTML pages."""
    import asyncio
    total, done, failed = 0, 0, 0

    for page in BIS_HTML_PAGES:
        total += 1
        result = await ingest_html_page(page["url"], page)
        if result.error:
            failed += 1
            log.warning("HTML page ingestion failed", url=page["url"], error=result.error)
        else:
            done += 1
        await asyncio.sleep(1.5)  # polite delay

    return {"total": total, "ingested": done, "failed": failed}
