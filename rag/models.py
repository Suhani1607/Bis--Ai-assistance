from pydantic import BaseModel, Field
from typing import Optional


class RagMessage(BaseModel):
    role: str                  # user | assistant
    content: str


class RagRequest(BaseModel):
    query: str = Field(..., min_length=1, max_length=4000)
    lang: str = Field("en", pattern=r"^(en|hi)$")
    history: list[RagMessage] = Field(default_factory=list)
    max_results: int = Field(8, ge=1, le=20)
    include_hindi: bool = False


class RagChunk(BaseModel):
    chunk_id: str
    is_number: Optional[str] = None
    clause_ref: Optional[str] = None
    section_title: Optional[str] = None
    excerpt: str
    score: float


class RagResponse(BaseModel):
    answer: str
    answer_hi: Optional[str] = None
    chunks: list[RagChunk] = Field(default_factory=list)
    detected_intent: str = "GENERAL"
    detected_lang: str = "en"
    tokens_used: int = 0
    latency_ms: int = 0


class IngestRequest(BaseModel):
    source_url: Optional[str] = None
    title: str
    doc_type: str = "IS_STANDARD"          # IS_STANDARD | SCHEME_MANUAL | REGULATION | FAQ
    is_number: Optional[str] = None
    document_id: Optional[str] = None
