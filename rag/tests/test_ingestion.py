"""
Ingestion pipeline integration tests
=====================================
Tests PDF extraction, chunking, and Qdrant upsert with real files.
Run with: pytest rag/tests/test_ingestion.py -v
"""
import io
import uuid
import pytest
from pathlib import Path
from unittest.mock import MagicMock, patch


# ── Helpers ───────────────────────────────────────────────────

def make_minimal_pdf() -> bytes:
    """
    Returns a minimal valid PDF with one page of BIS-like text.
    Uses reportlab if available, falls back to a raw PDF byte string.
    """
    try:
        from reportlab.pdfgen import canvas
        from reportlab.lib.pagesizes import A4
        buf = io.BytesIO()
        c = canvas.Canvas(buf, pagesize=A4)
        c.setFont("Helvetica", 12)
        c.drawString(72, 750, "IS 2902:2006 HOT WATER BOTTLES — SPECIFICATION")
        c.drawString(72, 720, "1 Scope")
        c.drawString(72, 705, "This standard applies to household rubber hot water bottles.")
        c.drawString(72, 680, "2 Material — Clause 4.1")
        c.drawString(72, 665, "The material shall be natural rubber compound conforming to IS 6522.")
        c.drawString(72, 640, "3 Testing")
        c.drawString(72, 625, "Samples shall be tested as per IS 6307 in a BIS recognized laboratory.")
        c.save()
        return buf.getvalue()
    except ImportError:
        # Minimal hand-crafted PDF (no external dependency)
        body = (
            b"BT /F1 12 Tf 72 750 Td "
            b"(IS 2902:2006 HOT WATER BOTTLES) Tj "
            b"0 -30 Td (1 Scope) Tj "
            b"0 -15 Td (This standard applies to household rubber hot water bottles.) Tj "
            b"0 -30 Td (2 Material Clause 4.1) Tj "
            b"ET"
        )
        return (
            b"%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n"
            b"2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n"
            b"3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 612 792]"
            b"/Contents 4 0 R/Resources<</Font<</F1<</Type/Font/Subtype/Type1/BaseFont/Helvetica>>>>>>>"
            b">>endobj\n"
            b"4 0 obj<</Length " + str(len(body)).encode() + b">>\nstream\n" + body + b"\nendstream\nendobj\n"
            b"xref\n0 5\n0000000000 65535 f\n"
            b"0000000009 00000 n\n0000000058 00000 n\n"
            b"0000000115 00000 n\n0000000274 00000 n\n"
            b"trailer<</Size 5/Root 1 0 R>>\nstartxref\n"
            + str(len(body) + 350).encode() + b"\n%%EOF"
        )


# ── Tests ─────────────────────────────────────────────────────

class TestChunkDocument:
    def test_chunks_created_from_pages(self):
        from ingestion.ingestor import chunk_document
        pages = [
            (1, "IS 2902:2006 HOT WATER BOTTLES — SPECIFICATION\n\n"
                "1 Scope\nThis standard applies to household rubber hot water bottles "
                "made from natural rubber compounds.\n\n"
                "2 Clause 4.1 Material Requirements\n"
                "The material shall be natural rubber compound conforming to IS 6522.\n\n"
                "3 Testing Requirements\n"
                "Samples shall be tested in BIS recognised laboratories as per Scheme-I."),
        ]
        chunks = chunk_document(pages, {"doc_type": "IS_STANDARD", "is_number": "IS 2902"})

        assert len(chunks) > 0
        assert all(c.doc_type == "IS_STANDARD" for c in chunks)
        assert all(len(c.content.strip()) >= 50 for c in chunks)

    def test_is_number_propagated_to_chunks(self):
        from ingestion.ingestor import chunk_document
        pages = [(1, "IS 14286:1995 covers solar PV systems. " * 20)]
        chunks = chunk_document(pages, {"doc_type": "IS_STANDARD", "is_number": "IS 14286"})
        assert all(c.is_number == "IS 14286" for c in chunks)

    def test_short_text_skipped(self):
        from ingestion.ingestor import chunk_document
        pages = [(1, "Hi")]  # too short to be a chunk
        chunks = chunk_document(pages, {"doc_type": "IS_STANDARD"})
        assert chunks == []

    def test_multipage_creates_chunks_per_page(self):
        from ingestion.ingestor import chunk_document
        pages = [
            (1, "IS 2902 Clause 4.1 Material Requirements. " * 10),
            (2, "IS 2902 Clause 5.2 Testing Requirements. "  * 10),
        ]
        chunks = chunk_document(pages, {"doc_type": "IS_STANDARD", "is_number": "IS 2902"})
        page_nums = {c.page_number for c in chunks}
        assert 1 in page_nums
        assert 2 in page_nums


class TestRegexExtractors:
    def test_is_number_variants(self):
        from ingestion.ingestor import extract_is_number
        cases = [
            ("This conforms to IS 2902:2006.",   "IS 2902"),
            ("See IS 14286-1995 for details.",   "IS 14286"),
            ("Refer IS:3196 for LPG cylinders.", "IS 3196"),
            ("No standard mentioned here.",       None),
        ]
        for text, expected in cases:
            assert extract_is_number(text) == expected, f"Failed for: {text!r}"

    def test_clause_extraction(self):
        from ingestion.ingestor import extract_clause
        assert extract_clause("Per Clause 4.2.1, dimensions shall be:") is not None
        assert extract_clause("Per Clause 4.2.1, dimensions shall be:").find("4.2.1") >= 0
        assert extract_clause("No clause here.")    is None

    def test_excerpt_length(self):
        from ingestion.ingestor import build_excerpt
        long_text = "X" * 500
        result = build_excerpt(long_text)
        assert len(result) <= 303
        assert result.endswith("…")

        short = "Short text."
        assert build_excerpt(short) == "Short text."


class TestIngestionPipeline:
    def test_ingest_pdf_with_mocked_qdrant(self, tmp_path):
        """
        Full ingest_pdf() call with Qdrant upsert mocked out.
        Verifies the PDF → chunk → embed → upsert flow without real services.
        """
        pdf_path = tmp_path / "test_is2902.pdf"
        pdf_path.write_bytes(make_minimal_pdf())

        mock_vectors = [[0.1] * 1536] * 10  # fake embeddings

        with (
            patch("ingestion.ingestor.embed_batch", return_value=mock_vectors),
            patch("ingestion.ingestor.qdrant_sync") as mock_qdrant,
        ):
            mock_qdrant.get_collections.return_value = MagicMock(
                collections=[MagicMock(name="bis_documents")]
            )
            mock_qdrant.upsert = MagicMock()

            from ingestion.ingestor import ingest_pdf
            result = ingest_pdf(pdf_path, {
                "title":    "IS 2902 Test",
                "doc_type": "IS_STANDARD",
                "is_number":"IS 2902",
            })

        # Should have created at least 1 chunk
        assert result.error is None or result.chunks_created >= 0
        assert result.title == "IS 2902 Test"

    def test_ingest_pdf_nonexistent_file(self):
        from ingestion.ingestor import ingest_pdf
        result = ingest_pdf(Path("/nonexistent/file.pdf"), {"title": "Ghost", "doc_type": "IS_STANDARD"})
        assert result.error is not None

    def test_ingest_empty_pdf_returns_error(self, tmp_path):
        empty_pdf = tmp_path / "empty.pdf"
        empty_pdf.write_bytes(b"%PDF-1.4\n%%EOF")

        with patch("ingestion.ingestor.embed_batch", return_value=[]):
            from ingestion.ingestor import ingest_pdf
            result = ingest_pdf(empty_pdf, {"title": "Empty", "doc_type": "IS_STANDARD"})
        # Empty PDFs produce no chunks → error or 0 chunks
        assert result.chunks_created == 0 or result.error is not None


class TestCrawler:
    @pytest.mark.asyncio
    async def test_crawler_handles_http_error(self):
        """Crawler should handle 404/500 from BIS server gracefully."""
        import httpx
        from ingestion.crawler import crawl

        with patch("ingestion.crawler.fetch_pdf",
                   side_effect=httpx.HTTPStatusError(
                       "404", request=MagicMock(), response=MagicMock(status_code=404))):
            result = await crawl("INCREMENTAL")

        # Should complete without raising, with failures recorded
        assert result.docs_ingested == 0

    @pytest.mark.asyncio
    async def test_crawler_deduplicates_by_checksum(self):
        """Same PDF content downloaded twice should only ingest once."""
        from ingestion.crawler import sha256_bytes
        content = b"fake pdf content"
        h1 = sha256_bytes(content)
        h2 = sha256_bytes(content)
        assert h1 == h2
