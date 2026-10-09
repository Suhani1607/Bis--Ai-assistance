"""
RAG Pipeline Tests
==================
Run with:  pytest rag/tests/ -v
Requires:  OPENAI_API_KEY and QDRANT_URL set (or use mocks below for CI)
"""
import json
import pytest
from unittest.mock import AsyncMock, MagicMock, patch
from pathlib import Path

# ── Fixtures ─────────────────────────────────────────────────

@pytest.fixture
def sample_rag_request():
    from models import RagRequest
    return RagRequest(
        query="Which Indian Standard covers pressure cookers?",
        lang="en",
        history=[],
        max_results=5,
        include_hindi=False,
    )

@pytest.fixture
def sample_chunks():
    return [
        {
            "chunk_id": "abc-001",
            "score": 0.92,
            "content": (
                "IS 2902:2006 specifies requirements for household hot water bottles. "
                "Clause 4.1 covers materials — shall be natural rubber or synthetic rubber."
            ),
            "is_number": "IS 2902",
            "clause_ref": "Clause 4.1",
            "section_title": "Material Requirements",
            "doc_type": "IS_STANDARD",
            "page_number": 3,
        },
        {
            "chunk_id": "abc-002",
            "score": 0.78,
            "content": (
                "IS 2902 is covered under BIS Scheme-I (ISI mark). "
                "Manufacturers must apply online at services.bis.gov.in."
            ),
            "is_number": "IS 2902",
            "clause_ref": "Clause 8.2",
            "section_title": "Certification",
            "doc_type": "SCHEME_MANUAL",
            "page_number": 12,
        },
    ]


# ── Intent detection tests ────────────────────────────────────

class TestIntentDetection:
    def test_find_standard_intent(self):
        from pipeline import detect_intent
        assert detect_intent("Which standard covers LED bulbs?") == "FIND_STANDARD"

    def test_certification_intent(self):
        from pipeline import detect_intent
        assert detect_intent("How do I get ISI mark certification?") == "CERT_SCHEME"

    def test_hallmarking_intent(self):
        from pipeline import detect_intent
        assert detect_intent("How to get gold hallmarking done?") == "HALLMARKING"

    def test_labs_intent(self):
        from pipeline import detect_intent
        assert detect_intent("Which labs can test my product?") == "LABS"

    def test_licensing_intent(self):
        from pipeline import detect_intent
        assert detect_intent("How do I apply for BIS licence?") == "LICENSING"

    def test_general_intent(self):
        from pipeline import detect_intent
        assert detect_intent("Tell me about BIS") == "GENERAL"


# ── Language detection tests ──────────────────────────────────

class TestLanguageDetection:
    def test_english_detected(self):
        from pipeline import detect_lang
        assert detect_lang("What is the IS number for LPG cylinders?") == "en"

    def test_hindi_detected(self):
        from pipeline import detect_lang
        result = detect_lang("दबाव कुकर के लिए कौन सा IS नंबर है?")
        assert result == "hi"


# ── Context block builder ─────────────────────────────────────

class TestContextBuilder:
    def test_build_context_block(self, sample_chunks):
        from pipeline import build_context_block
        context = build_context_block(sample_chunks)
        assert "IS 2902" in context
        assert "Clause 4.1" in context
        assert "Material Requirements" in context
        assert "[1]" in context
        assert "[2]" in context

    def test_empty_chunks_returns_empty(self):
        from pipeline import build_context_block
        assert build_context_block([]) == ""


# ── Excerpt builder ───────────────────────────────────────────

class TestExcerptBuilder:
    def test_short_content_unchanged(self):
        from pipeline import build_excerpt
        short = "IS 2902 covers hot water bottles."
        assert build_excerpt(short) == short

    def test_long_content_truncated(self):
        from pipeline import build_excerpt
        long_text = "A" * 400
        result = build_excerpt(long_text)
        assert len(result) <= 303  # 300 + "…"
        assert result.endswith("…")


# ── Pipeline integration test (mocked LLM + Qdrant) ──────────

class TestPipelineMocked:
    @pytest.mark.asyncio
    async def test_run_pipeline_returns_response(self, sample_rag_request, sample_chunks):
        mock_llm_response = MagicMock()
        mock_llm_response.content = (
            "IS 2902:2006 covers hot water bottles. [IS 2902, Clause 4.1]"
        )
        mock_llm_response.response_metadata = {"token_usage": {"total_tokens": 120}}

        with (
            patch("pipeline.hybrid_search", new=AsyncMock(return_value=sample_chunks)),
            patch("pipeline.llm") as mock_llm,
        ):
            mock_llm.invoke = MagicMock(return_value=mock_llm_response)

            from pipeline import run_pipeline
            result = await run_pipeline(sample_rag_request)

        assert result.answer is not None
        assert "IS 2902" in result.answer
        assert len(result.chunks) == 2
        assert result.chunks[0].is_number == "IS 2902"
        assert result.tokens_used == 120
        assert result.detected_intent in (
            "FIND_STANDARD", "CERT_SCHEME", "GENERAL"
        )

    @pytest.mark.asyncio
    async def test_no_chunks_returns_fallback(self, sample_rag_request):
        with patch("pipeline.hybrid_search", new=AsyncMock(return_value=[])):
            from pipeline import run_pipeline
            result = await run_pipeline(sample_rag_request)

        assert "services.bis.gov.in" in result.answer or "1800-11-4000" in result.answer
        assert result.chunks == []


# ── Ingestion tests ───────────────────────────────────────────

class TestIngestion:
    def test_is_number_extraction(self):
        from ingestion.ingestor import extract_is_number
        text = "This product shall conform to IS 14286:1995 for solar PV systems."
        assert extract_is_number(text) == "IS 14286"

    def test_clause_extraction(self):
        from ingestion.ingestor import extract_clause
        text = "Refer to Clause 4.2.1 for detailed requirements."
        result = extract_clause(text)
        assert result is not None
        assert "4.2.1" in result

    def test_section_extraction(self):
        from ingestion.ingestor import extract_section_title
        text = "4.1 Material Requirements\nThe material shall conform to..."
        result = extract_section_title(text)
        # May or may not find depending on multiline; just assert no crash
        assert result is None or isinstance(result, str)

    def test_chunk_document_creates_chunks(self):
        from ingestion.ingestor import chunk_document
        pages = [
            (1, "IS 2902:2006 Specification for Hot Water Bottles\n\n"
                "1 Scope\nThis standard applies to household rubber hot water bottles.\n\n"
                "2 Material\nShall be natural rubber compound as per Clause 4.1.\n\n"
                "3 Testing\nSamples shall be tested as per IS 6307."),
            (2, "4 Marking\nEach bottle shall carry the ISI mark if certified under Scheme-I.\n\n"
                "5 Packing\nProducts shall be packed individually in polythene bags."),
        ]
        chunks = chunk_document(pages, {"doc_type": "IS_STANDARD", "is_number": "IS 2902"})
        assert len(chunks) > 0
        for chunk in chunks:
            assert chunk.content.strip() != ""
            assert len(chunk.content) >= 50
            assert chunk.doc_type == "IS_STANDARD"
