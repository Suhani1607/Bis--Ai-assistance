"""
BIS Document Ingestion Pipeline
================================
Reads PDF/HTML documents → extracts text → chunks → embeds → upserts to Qdrant.
Supports:
  - Standard IS PDFs (with clause detection via regex)
  - Scheme manuals
  - Hallmarking regulations
  - FAQ pages
"""

import hashlib
import re
import uuid
from pathlib import Path
from dataclasses import dataclass, field
from typing import Optional

import structlog
import pdfplumber
from langchain_text_splitters import RecursiveCharacterTextSplitter
from langchain_openai import OpenAIEmbeddings
from qdrant_client import QdrantClient
from qdrant_client.models import (
    Distance, VectorParams,
    PointStruct, OptimizersConfigDiff,
)
from tenacity import retry, stop_after_attempt, wait_exponential

from config import settings

log = structlog.get_logger()

# ── Data classes ──────────────────────────────────────────────

@dataclass
class DocumentChunk:
    chunk_id: str
    content: str
    is_number: Optional[str]
    clause_ref: Optional[str]
    section_title: Optional[str]
    doc_type: str
    source_url: Optional[str]
    page_number: Optional[int]
    token_count: int = 0
    metadata: dict = field(default_factory=dict)

@dataclass
class IngestionResult:
    document_id: str
    title: str
    chunks_created: int
    chunks_skipped: int
    error: Optional[str] = None


# ── Clause pattern detection ──────────────────────────────────

IS_NUMBER_RE = re.compile(r'\bIS\s+(\d+(?:[:\-/]\d+)?(?::\d{4})?)\b', re.IGNORECASE)
CLAUSE_RE    = re.compile(r'\b(Clause\s+\d+(?:\.\d+)*|\d+\.\d+(?:\.\d+)*)\b', re.IGNORECASE)
SECTION_RE   = re.compile(r'^(\d+(?:\.\d+)*)\s+([A-Z][^\n]{5,80})\s*$', re.MULTILINE)


def extract_is_number(text: str) -> Optional[str]:
    m = IS_NUMBER_RE.search(text)
    return f"IS {m.group(1)}" if m else None


def extract_clause(text: str) -> Optional[str]:
    m = CLAUSE_RE.search(text)
    return m.group(1) if m else None


def extract_section_title(text: str) -> Optional[str]:
    m = SECTION_RE.search(text)
    return m.group(2).strip() if m else None


# ── PDF extraction ────────────────────────────────────────────

def extract_pdf_pages(pdf_path: Path) -> list[tuple[int, str]]:
    """Returns list of (page_num, text) tuples."""
    pages = []
    with pdfplumber.open(pdf_path) as pdf:
        for i, page in enumerate(pdf.pages, 1):
            text = page.extract_text(x_tolerance=2, y_tolerance=3) or ""
            if len(text.strip()) < 50:
                # Try OCR for scanned pages
                try:
                    import pytesseract
                    from PIL import Image
                    img = page.to_image(resolution=200).original
                    text = pytesseract.image_to_string(img, lang="eng")
                except Exception:
                    pass
            if text.strip():
                pages.append((i, text))
    return pages


# ── Chunking ──────────────────────────────────────────────────

splitter = RecursiveCharacterTextSplitter(
    chunk_size=settings.chunk_size,
    chunk_overlap=settings.chunk_overlap,
    separators=["\n\n\n", "\n\n", "\n", ". ", " ", ""],
)


def chunk_document(
    pages: list[tuple[int, str]],
    doc_metadata: dict,
) -> list[DocumentChunk]:
    chunks = []
    for page_num, text in pages:
        raw_chunks = splitter.split_text(text)
        for chunk_text in raw_chunks:
            if len(chunk_text.strip()) < 50:
                continue
            cid = str(uuid.uuid4())
            chunks.append(DocumentChunk(
                chunk_id=cid,
                content=chunk_text.strip(),
                is_number=doc_metadata.get("is_number") or extract_is_number(chunk_text),
                clause_ref=extract_clause(chunk_text),
                section_title=extract_section_title(chunk_text),
                doc_type=doc_metadata.get("doc_type", "IS_STANDARD"),
                source_url=doc_metadata.get("source_url"),
                page_number=page_num,
                metadata={**doc_metadata},
            ))
    return chunks


# ── Embedding ─────────────────────────────────────────────────

def _create_embedder():
    backend = settings.embedding_backend

    if backend == "huggingface":
        from sentence_transformers import SentenceTransformer

        class SentenceTransformerEmbeddings:
            def __init__(self, model_name: str):
                self._model_name = model_name
                self._model = None

            def _get_model(self):
                if self._model is None:
                    log.info("Loading HuggingFace embedding model...", model=self._model_name)
                    self._model = SentenceTransformer(self._model_name)
                    log.info("HuggingFace embedding model loaded successfully", model=self._model_name)
                return self._model

            def embed_documents(self, texts: list[str]) -> list[list[float]]:
                return self._get_model().encode(texts, normalize_embeddings=True).tolist()

            def embed_query(self, text: str) -> list[float]:
                return self._get_model().encode([text], normalize_embeddings=True)[0].tolist()

        return SentenceTransformerEmbeddings(settings.hf_embedding_model)

    if backend == "openai":
        return OpenAIEmbeddings(
            model=settings.embedding_model,
            openai_api_key=settings.openai_api_key,
            base_url=settings.openai_api_base,
        )

    # Dummy deterministic fallback — NOT suitable for semantic search
    import hashlib, math
    log.warning("No real embedding backend configured — using deterministic hash (for dev only)")

    class DeterministicEmbeddings:
        def embed_query(self, text: str) -> list[float]:
            return self._embed(text)

        def embed_documents(self, texts: list[str]) -> list[list[float]]:
            return [self._embed(t) for t in texts]

        def _embed(self, text: str) -> list[float]:
            dim = settings.vector_size
            h = hashlib.sha256(text.encode("utf-8")).digest()
            vec = [(h[i % len(h)] / 255.0) - 0.5 for i in range(dim)]
            norm = math.sqrt(sum(x * x for x in vec)) or 1.0
            return [x / norm for x in vec]

    return DeterministicEmbeddings()


embedder = _create_embedder()


@retry(stop=stop_after_attempt(3), wait=wait_exponential(min=2, max=30))
def embed_batch(texts: list[str]) -> list[list[float]]:
    return embedder.embed_documents(texts)


# ── Qdrant setup ─────────────────────────────────────────────

qdrant_sync = QdrantClient(
    url=settings.qdrant_url,
    api_key=settings.qdrant_api_key,
)


def ensure_collection(recreate_if_mismatch: bool = True):
    """Create or validate Qdrant collection with configured dense vector size."""
    try:
        collections = [c.name for c in qdrant_sync.get_collections().collections]
    except Exception as e:
        log.warning("Could not reach Qdrant to list collections", error=str(e))
        return

    if settings.qdrant_collection in collections:
        try:
            col_info = qdrant_sync.get_collection(settings.qdrant_collection)
            vectors_param = col_info.config.params.vectors
            existing_size = None
            if isinstance(vectors_param, dict) and "dense" in vectors_param:
                existing_size = vectors_param["dense"].size
            elif hasattr(vectors_param, "size"):
                existing_size = vectors_param.size

            if existing_size is not None and existing_size != settings.vector_size:
                log.warning(
                    "Collection vector dimension mismatch",
                    existing=existing_size,
                    required=settings.vector_size,
                )
                if recreate_if_mismatch:
                    log.info("Recreating Qdrant collection for correct dimension", size=settings.vector_size)
                    qdrant_sync.delete_collection(settings.qdrant_collection)
                else:
                    return
            else:
                log.info("Collection already exists with verified dimension", name=settings.qdrant_collection, size=settings.vector_size)
                return
        except Exception as e:
            log.warning("Could not inspect existing collection vectors", error=str(e))

    qdrant_sync.create_collection(
        collection_name=settings.qdrant_collection,
        vectors_config={
            "dense": VectorParams(
                size=settings.vector_size,
                distance=Distance.COSINE,
            )
        },
        optimizers_config=OptimizersConfigDiff(indexing_threshold=20_000),
    )
    log.info("Created Qdrant collection", name=settings.qdrant_collection, vector_size=settings.vector_size)


# ── Upsert ────────────────────────────────────────────────────

BATCH_SIZE = 64


def upsert_chunks(chunks: list[DocumentChunk]):
    """Embed and upsert chunks to Qdrant in batches."""
    ensure_collection()
    total = len(chunks)
    log.info("Upserting chunks", total=total)

    for i in range(0, total, BATCH_SIZE):
        batch = chunks[i: i + BATCH_SIZE]
        texts = [c.content for c in batch]
        vectors = embed_batch(texts)

        points = [
            PointStruct(
                id=c.chunk_id,
                vector={"dense": vec},
                payload={
                    "content": c.content,
                    "is_number": c.is_number,
                    "clause_ref": c.clause_ref,
                    "section_title": c.section_title,
                    "doc_type": c.doc_type,
                    "source_url": c.source_url,
                    "page_number": c.page_number,
                    **c.metadata,
                },
            )
            for c, vec in zip(batch, vectors)
        ]
        qdrant_sync.upsert(collection_name=settings.qdrant_collection, points=points)
        log.info("Upserted batch", start=i, end=i + len(batch))


# ── Main ingest function ──────────────────────────────────────

def ingest_pdf(
    pdf_path: Path,
    doc_metadata: dict,
) -> IngestionResult:
    """
    Full pipeline: PDF → pages → chunks → embeddings → Qdrant.
    Returns an IngestionResult summary.
    """
    doc_id = doc_metadata.get("document_id", str(uuid.uuid4()))
    title  = doc_metadata.get("title", pdf_path.stem)

    log.info("Starting ingestion", path=str(pdf_path), title=title)

    try:
        pages = extract_pdf_pages(pdf_path)
        if not pages:
            return IngestionResult(doc_id, title, 0, 0, "No extractable text found")

        chunks = chunk_document(pages, doc_metadata)
        if not chunks:
            return IngestionResult(doc_id, title, 0, 0, "No chunks generated")

        upsert_chunks(chunks)

        return IngestionResult(
            document_id=doc_id,
            title=title,
            chunks_created=len(chunks),
            chunks_skipped=0,
        )

    except Exception as e:
        log.error("Ingestion failed", error=str(e), path=str(pdf_path))
        return IngestionResult(doc_id, title, 0, 0, str(e))
