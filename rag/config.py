"""
Centralised settings — reads from environment variables / .env
"""
from pydantic_settings import BaseSettings, SettingsConfigDict
from pydantic import Field


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    # ── LLM / API Credentials ────────────────────────────────
    groq_api_key: str | None = Field(None, alias="GROQ_API_KEY")
    openai_api_key: str | None = Field(None, alias="OPENAI_API_KEY")
    openai_api_base: str = Field("https://api.openai.com/v1", alias="OPENAI_API_BASE")
    llm_model: str = Field("openai/gpt-oss-120b", alias="LLM_MODEL")
    embedding_model: str = Field("text-embedding-3-small", alias="EMBEDDING_MODEL")

    # ── HuggingFace / local embeddings ───────────────────────
    # Set HF_EMBEDDING_MODEL to use sentence-transformers locally (no API key).
    # Recommended: BAAI/bge-m3 (1024-dim, multilingual, English+Hindi).
    # Leave blank to fall back to openai_api_key → OpenAI, or dummy hash fallback.
    hf_embedding_model: str | None = Field(None, alias="HF_EMBEDDING_MODEL")

    # ── Qdrant ────────────────────────────────────────────────
    qdrant_url: str = Field("http://localhost:6333", alias="QDRANT_URL")
    qdrant_api_key: str | None = Field(None, alias="QDRANT_API_KEY")
    qdrant_collection: str = Field("bis_documents", alias="QDRANT_COLLECTION")
    # Aligned with BAAI/bge-m3 (1024 dimensions). Change to 1536 if using text-embedding-3-small.
    vector_size: int = Field(1024, alias="VECTOR_SIZE")

    # ── Search weights ────────────────────────────────────────
    dense_weight: float = 1.0                      # semantic weight
    sparse_weight: float = 0.0                     # BM25 weight (reserved)

    # ── RAG parameters ───────────────────────────────────────
    chunk_size: int = 800
    chunk_overlap: int = 150
    max_retrieval_results: int = 8
    rerank_top_k: int = 4

    # ── Redis (conversation memory) ───────────────────────────
    redis_url: str = Field("redis://localhost:6379", alias="REDIS_URL")
    memory_window: int = 10                        # messages to keep in context

    # ── PostgreSQL (analytics) ────────────────────────────────
    db_url: str = Field("postgresql://bisuser:bispassword@localhost:5432/bis_assistant",
                         alias="DATABASE_URL")

    # ── Misc ──────────────────────────────────────────────────
    log_level: str = "INFO"
    environment: str = Field("production", alias="ENVIRONMENT")

    @property
    def effective_llm_api_key(self) -> str:
        if self.groq_api_key:
            return self.groq_api_key
        return self.openai_api_key or ""

    @property
    def effective_llm_api_base(self) -> str:
        if self.groq_api_key and "api.openai.com" in self.openai_api_base:
            return "https://api.groq.com/openai/v1"
        return self.openai_api_base

    @property
    def effective_llm_model(self) -> str:
        if self.groq_api_key and self.llm_model == "gpt-4o":
            return "llama-3.3-70b-versatile"
        return self.llm_model

    @property
    def embedding_backend(self) -> str:
        """Returns which embedding backend to use: 'huggingface', 'openai', or 'dummy'."""
        if self.hf_embedding_model and self.hf_embedding_model.strip():
            return "huggingface"
        if self.openai_api_key and not self.openai_api_key.startswith("gsk_"):
            return "openai"
        return "dummy"


settings = Settings()
