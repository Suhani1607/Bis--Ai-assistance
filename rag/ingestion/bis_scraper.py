"""
BIS Portal Extended Scraper
============================
Scrapes BIS website listing pages to discover IS document PDF URLs dynamically,
going beyond the hardcoded seed list in crawler.py.

Targets:
  - Know Your Standard portal   (searchable IS metadata)
  - New licences granted page   (latest IS in use)
  - BIS publications index
"""

import asyncio
import re
from dataclasses import dataclass
from typing import Optional
from urllib.parse import urljoin, urlparse

import httpx
import structlog
from tenacity import retry, stop_after_attempt, wait_exponential

log = structlog.get_logger()

# ── BIS base URL ─────────────────────────────────────────────
BIS_BASE = "https://www.bis.gov.in"

# Pages that list or link to IS documents
LISTING_PAGES = [
    f"{BIS_BASE}/index.php/standards/published-standards/",
    f"{BIS_BASE}/index.php/standardsadmin/knowyourstandard/",
    f"{BIS_BASE}/index.php/schemes/product-certification-schemes/scheme-i/",
    f"{BIS_BASE}/index.php/hallmarking/",
]

# Pattern to match BIS document PDF links
PDF_URL_RE   = re.compile(r'href=["\']([^"\']+\.pdf)["\']', re.IGNORECASE)
IS_NUMBER_RE = re.compile(r'\bIS\s*:?\s*(\d{3,6}(?:[:\-]\d{1,4})?(?::\d{4})?)\b', re.IGNORECASE)

# Doc-type inference from URL path keywords
DOC_TYPE_HINTS = {
    "hallmark":  "HALLMARKING",
    "scheme":    "SCHEME_MANUAL",
    "regulation": "REGULATION",
    "faq":       "FAQ",
    "standard":  "IS_STANDARD",
    "circular":  "CIRCULAR",
}


@dataclass
class DiscoveredDocument:
    url: str
    title: str
    doc_type: str
    is_number: Optional[str]


def infer_doc_type(url: str) -> str:
    lower = url.lower()
    for keyword, dtype in DOC_TYPE_HINTS.items():
        if keyword in lower:
            return dtype
    return "IS_STANDARD"


def extract_is_number_from_url(url: str) -> Optional[str]:
    m = IS_NUMBER_RE.search(url)
    return f"IS {m.group(1)}" if m else None


def make_title_from_url(url: str) -> str:
    """Best-effort title from URL path segment."""
    path = urlparse(url).path
    filename = path.split("/")[-1].replace(".pdf", "").replace("-", " ").replace("_", " ")
    return filename.title() or "BIS Document"


@retry(stop=stop_after_attempt(3), wait=wait_exponential(min=2, max=10))
async def fetch_page(url: str, client: httpx.AsyncClient) -> Optional[str]:
    try:
        resp = await client.get(url, follow_redirects=True, timeout=20)
        resp.raise_for_status()
        return resp.text
    except Exception as e:
        log.warning("Failed to fetch listing page", url=url, error=str(e))
        return None


def extract_pdf_links(html: str, base_url: str) -> list[str]:
    """Find all PDF links in an HTML page and make them absolute."""
    links = PDF_URL_RE.findall(html)
    absolute = []
    for link in links:
        abs_url = urljoin(base_url, link)
        # Only keep BIS domain links
        if "bis.gov.in" in abs_url:
            absolute.append(abs_url)
    return list(set(absolute))  # deduplicate


async def discover_documents(
    extra_pages: Optional[list[str]] = None,
) -> list[DiscoveredDocument]:
    """
    Scrape BIS listing pages and return a list of discovered document metadata.
    Does NOT download PDFs — just returns URLs + metadata for the ingestion pipeline.
    """
    pages_to_scrape = LISTING_PAGES + (extra_pages or [])
    discovered: list[DiscoveredDocument] = []
    seen_urls: set[str] = set()

    async with httpx.AsyncClient(
        headers={
            "User-Agent": "BIS-AI-Assistant/1.0 (document indexing; contact: admin@bis-assistant.in)",
            "Accept": "text/html",
        },
        timeout=httpx.Timeout(30, connect=10),
    ) as client:

        for page_url in pages_to_scrape:
            log.info("Scraping listing page", url=page_url)
            html = await fetch_page(page_url, client)
            if not html:
                continue

            pdf_links = extract_pdf_links(html, page_url)
            log.info("Found PDF links", count=len(pdf_links), source=page_url)

            for pdf_url in pdf_links:
                if pdf_url in seen_urls:
                    continue
                seen_urls.add(pdf_url)

                doc = DiscoveredDocument(
                    url=pdf_url,
                    title=make_title_from_url(pdf_url),
                    doc_type=infer_doc_type(pdf_url),
                    is_number=extract_is_number_from_url(pdf_url),
                )
                discovered.append(doc)

            # Polite crawl delay
            await asyncio.sleep(1.5)

    log.info("Discovery complete", total=len(discovered))
    return discovered


async def discover_and_ingest(extra_pages: Optional[list[str]] = None) -> dict:
    """
    Full pipeline: discover → ingest all found documents.
    Returns summary dict compatible with CrawlResult structure.
    """
    from ingestion.ingestor import ingest_pdf
    from ingestion.crawler import fetch_pdf, sha256_bytes, CrawlResult
    import tempfile
    from pathlib import Path

    result = CrawlResult()
    docs = await discover_documents(extra_pages)
    result.total_found = len(docs)

    async with httpx.AsyncClient(
        headers={"User-Agent": "BIS-AI-Assistant/1.0"},
        timeout=httpx.Timeout(60, connect=10),
    ) as client:

        for doc in docs:
            try:
                pdf_bytes = await fetch_pdf(doc.url, client)
                if not pdf_bytes:
                    result.docs_skipped += 1
                    continue

                with tempfile.NamedTemporaryFile(suffix=".pdf", delete=False) as tmp:
                    tmp.write(pdf_bytes)
                    tmp_path = Path(tmp.name)

                ingest_result = ingest_pdf(tmp_path, {
                    "title":      doc.title,
                    "doc_type":   doc.doc_type,
                    "is_number":  doc.is_number,
                    "source_url": doc.url,
                    "checksum":   sha256_bytes(pdf_bytes),
                })
                tmp_path.unlink(missing_ok=True)

                if ingest_result.error:
                    result.docs_failed += 1
                    result.errors.append(f"{doc.url}: {ingest_result.error}")
                else:
                    result.docs_ingested += 1
                    log.info("Scraped and ingested", title=doc.title,
                             chunks=ingest_result.chunks_created)

                await asyncio.sleep(2)   # polite delay

            except Exception as e:
                log.error("Scraper ingest error", url=doc.url, error=str(e))
                result.docs_failed += 1
                result.errors.append(f"{doc.url}: {e}")

    return {
        "total_found":   result.total_found,
        "docs_ingested": result.docs_ingested,
        "docs_skipped":  result.docs_skipped,
        "docs_failed":   result.docs_failed,
        "errors":        result.errors[:20],  # cap error list
    }
