"""
BIS RAG Pipeline
================
1. Detect language, translate if Hindi → English
2. Hybrid search: dense (OpenAI embed) + sparse (BM25) over Qdrant
3. Rerank with cross-encoder
4. Build augmented prompt with BIS-specific instructions
5. Generate answer via LLM with clause-level citations
6. Optionally translate answer back to Hindi
"""

import asyncio
import time
import re
from typing import AsyncGenerator

import structlog
from langchain_openai import ChatOpenAI, OpenAIEmbeddings
from langchain.schema import HumanMessage, SystemMessage, AIMessage
from qdrant_client import AsyncQdrantClient
from langdetect import detect
from deep_translator import GoogleTranslator
from tenacity import retry, stop_after_attempt, wait_exponential

from config import settings
from models import RagRequest, RagResponse, RagChunk

log = structlog.get_logger()

# ── Initialise singletons ─────────────────────────────────────

def _create_embeddings():
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

            def embed_query(self, text: str) -> list[float]:
                return self._get_model().encode([text], normalize_embeddings=True)[0].tolist()

            def embed_documents(self, texts: list[str]) -> list[list[float]]:
                return self._get_model().encode(texts, normalize_embeddings=True).tolist()

        return SentenceTransformerEmbeddings(settings.hf_embedding_model)

    if backend == "openai":
        return OpenAIEmbeddings(
            model=settings.embedding_model,
            openai_api_key=settings.openai_api_key,
            base_url=settings.openai_api_base,
        )

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


embeddings = _create_embeddings()

llm = ChatOpenAI(
    model=settings.effective_llm_model,
    temperature=0.1,
    api_key=settings.effective_llm_api_key,
    base_url=settings.effective_llm_api_base,
    max_tokens=2500,
)

llm_stream = ChatOpenAI(
    model=settings.effective_llm_model,
    temperature=0.1,
    api_key=settings.effective_llm_api_key,
    base_url=settings.effective_llm_api_base,
    streaming=True,
    max_tokens=2500,
)

qdrant = AsyncQdrantClient(
    url=settings.qdrant_url,
    api_key=settings.qdrant_api_key,
)

# ── System prompt ─────────────────────────────────────────────

SYSTEM_PROMPT = """You are BIS AI Assistant — the official conversational assistant for the \
Bureau of Indian Standards (BIS), India's national standards body.

Your purpose is to help manufacturers, MSMEs, consumers, and students with:
- Finding applicable Indian Standards (IS numbers) for products
- Explaining BIS certification schemes (Scheme-I / ISI mark, CRS, FMCS, Hallmarking, etc.)
- Describing licensing and certification procedures step-by-step
- Answering hallmarking queries for gold and silver jewellery
- Identifying BIS-recognised testing laboratories
- Interpreting clauses from IS documents

RULES:
1. Answer ONLY from the provided BIS document excerpts (context). Do not use external knowledge.
2. Every factual claim MUST be followed by an inline citation in this exact format: [IS XXXX:YYYY, Clause Z.Z]
   or [BIS Scheme-I Guidelines, Section N] — match the format in the retrieved chunks.
3. If the context does not contain enough information, say so clearly and suggest the user visit
   services.bis.gov.in or call BIS helpline 1800-11-4000.
4. Respond in the same language as the user's query (English or Hindi).
5. Be concise but complete. Use numbered steps for procedures.
6. Never invent IS numbers, clause numbers, or scheme details.

Retrieved context follows. Use it exclusively to answer the query.
"""


# ── Language detection & translation ─────────────────────────

def detect_lang(text: str) -> str:
    try:
        return detect(text)
    except Exception:
        return "en"


def translate(text: str, source: str = "en", target: str = "hi") -> str:
    try:
        return GoogleTranslator(source=source, target=target).translate(text)
    except Exception as e:
        log.warning("Translation failed", error=str(e))
        return text


# ── Embedding ─────────────────────────────────────────────────

@retry(stop=stop_after_attempt(3), wait=wait_exponential(min=1, max=10))
async def embed_query(text: str) -> list[float]:
    loop = asyncio.get_event_loop()
    return await loop.run_in_executor(None, embeddings.embed_query, text)


# ── Hybrid search ─────────────────────────────────────────────

async def hybrid_search(query: str, top_k: int = 8) -> list[dict]:
    """
    Qdrant search using dense vectors with fallback support for Qdrant versions.
    Returns a list of payload dicts with score.
    """
    try:
        query_vector = await embed_query(query)
    except Exception as e:
        log.warning("Embedding query failed", error=str(e))
        return []

    points = []
    try:
        results = await qdrant.query_points(
            collection_name=settings.qdrant_collection,
            query=query_vector,
            using="dense",
            limit=top_k,
            with_payload=True,
        )
        points = getattr(results, "points", []) or []
    except Exception as e:
        log.warning("Qdrant retrieval error, proceeding with empty retrieval", error=str(e))
        points = []

    chunks = []
    for hit in points:
        payload = getattr(hit, "payload", {}) or {}
        chunks.append({
            "chunk_id": str(getattr(hit, "id", "")),
            "score": getattr(hit, "score", 0.0),
            "content": payload.get("content", ""),
            "is_number": payload.get("is_number"),
            "clause_ref": payload.get("clause_ref"),
            "section_title": payload.get("section_title"),
            "doc_type": payload.get("doc_type"),
            "page_number": payload.get("page_number"),
        })
    return chunks


def build_excerpt(content: str, max_chars: int = 300) -> str:
    content = content.strip()
    return content[:max_chars] + "…" if len(content) > max_chars else content


# ── Prompt builder ────────────────────────────────────────────

def build_context_block(chunks: list[dict]) -> str:
    parts = []
    for i, chunk in enumerate(chunks, 1):
        header = f"[{i}] "
        if chunk.get("is_number"):
            header += chunk["is_number"]
        if chunk.get("clause_ref"):
            header += f", {chunk['clause_ref']}"
        if chunk.get("section_title"):
            header += f" — {chunk['section_title']}"
        parts.append(f"{header}\n{chunk['content']}")
    return "\n\n---\n\n".join(parts)


def build_messages(query: str, context: str, history: list[dict]) -> list:
    msgs = [SystemMessage(content=SYSTEM_PROMPT)]

    # Conversation history
    for h in history[-settings.memory_window:]:
        role = getattr(h, "role", None) or (h.get("role") if isinstance(h, dict) else None)
        content = getattr(h, "content", "") or (h.get("content", "") if isinstance(h, dict) else "")
        if role == "user":
            msgs.append(HumanMessage(content=content))
        elif role == "assistant":
            msgs.append(AIMessage(content=content))

    # Current query with context
    user_content = f"""RETRIEVED BIS DOCUMENTS:
{context}

USER QUERY:
{query}"""
    msgs.append(HumanMessage(content=user_content))
    return msgs


# ── Main pipeline ─────────────────────────────────────────────

async def run_pipeline(request: RagRequest) -> RagResponse:
    start = time.perf_counter()

    # 1. Language detection
    detected_lang = detect_lang(request.query)
    query_en = request.query

    if detected_lang == "hi":
        query_en = translate(request.query, source="hi", target="en")
        log.info("Translated query", original=request.query[:60], translated=query_en[:60])

    # 2. Hybrid search
    chunks = await hybrid_search(query_en, top_k=request.max_results or settings.max_retrieval_results)
    log.info("Retrieved chunks", count=len(chunks))

    if not chunks:
        answer = (
            "I could not find relevant BIS documents for your query. "
            "Please visit services.bis.gov.in or call BIS helpline 1800-11-4000."
        )
        return RagResponse(
            answer=answer,
            chunks=[],
            detected_intent="UNKNOWN",
            detected_lang=detected_lang,
            tokens_used=0,
            latency_ms=int((time.perf_counter() - start) * 1000),
        )

    # 3. Build context
    context = build_context_block(chunks)

    # 4. Detect intent (simple keyword heuristic — extend with a classifier)
    intent = detect_intent(query_en)

    # 5. Generate answer
    messages = build_messages(query_en, context, request.history or [])
    loop = asyncio.get_event_loop()
    response = await loop.run_in_executor(None, llm.invoke, messages)
    answer_en = response.content
    tokens = response.response_metadata.get("token_usage", {}).get("total_tokens", 0)

    # 6. Optionally translate answer to Hindi
    answer_hi = None
    if request.include_hindi or detected_lang == "hi":
        answer_hi = translate(answer_en, source="en", target="hi")

    latency = int((time.perf_counter() - start) * 1000)
    log.info("Pipeline complete", latency_ms=latency, tokens=tokens, intent=intent)

    return RagResponse(
        answer=answer_en,
        answer_hi=answer_hi,
        chunks=[RagChunk(
            chunk_id=c["chunk_id"],
            is_number=c.get("is_number"),
            clause_ref=c.get("clause_ref"),
            section_title=c.get("section_title"),
            excerpt=build_excerpt(c["content"]),
            score=c["score"],
        ) for c in chunks],
        detected_intent=intent,
        detected_lang=detected_lang,
        tokens_used=tokens,
        latency_ms=latency,
    )


async def stream_pipeline(request: RagRequest) -> AsyncGenerator[str, None]:
    """Yield SSE-formatted token strings."""
    import json
    try:
        detected_lang = detect_lang(request.query)
        query_en = request.query
        if detected_lang == "hi":
            query_en = translate(request.query, source="hi", target="en")

        chunks = await hybrid_search(query_en, top_k=request.max_results or settings.max_retrieval_results)
        context = build_context_block(chunks)
        messages = build_messages(query_en, context, request.history or [])

        # Yield citations first as a structured event
        citations_payload = [
            {
                "chunk_id": c["chunk_id"],
                "is_number": c.get("is_number"),
                "clause_ref": c.get("clause_ref"),
                "section_title": c.get("section_title"),
                "excerpt": build_excerpt(c["content"]),
                "score": c["score"],
            }
            for c in chunks
        ]
        yield f"data: {json.dumps({'type': 'citations', 'data': citations_payload})}\n\n"

        # Stream tokens
        async for token in llm_stream.astream(messages):
            if token.content:
                yield f"data: {json.dumps({'type': 'token', 'data': token.content})}\n\n"

        yield "data: [DONE]\n\n"
    except Exception as e:
        log.error("Stream pipeline failed", error=str(e))
        yield f"data: {json.dumps({'type': 'token', 'data': 'I encountered an error generating the response. Please try again.'})}\n\n"
        yield "data: [DONE]\n\n"


# ── Intent detection ─────────────────────────────────────────

INTENT_PATTERNS = {
    "FIND_STANDARD":  [r"\bwhich is\b", r"\bstandard for\b", r"\bIS number\b", r"\bapplicable is\b"],
    "CERT_SCHEME":    [r"\bcertif", r"\bisi mark\b", r"\bscheme\b", r"\bcrs\b", r"\bfmcs\b"],
    "LICENSING":      [r"\blic[e|a]nce\b", r"\bapply\b", r"\bapplication\b", r"\bhow to get\b"],
    "HALLMARKING":    [r"\bhallmark", r"\bgold\b", r"\bsilver\b", r"\bjewel"],
    "LABS":           [r"\blab\b", r"\btesting\b", r"\btest report\b", r"\baccredit"],
    "GENERAL":        [],
}


def detect_intent(query: str) -> str:
    q = query.lower()
    for intent, patterns in INTENT_PATTERNS.items():
        if any(re.search(p, q) for p in patterns):
            return intent
    return "GENERAL"
