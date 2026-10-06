-- ============================================================
--  BIS AI ASSISTANT — PostgreSQL Schema
--  Flyway migration V1 — initial schema
-- ============================================================

-- Extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";     -- trigram search on text fields

-- ============================================================
-- 1. USERS
-- ============================================================
CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email           VARCHAR(255) UNIQUE NOT NULL,
    name            VARCHAR(255) NOT NULL,
    password_hash   VARCHAR(255),                      -- null for OAuth users
    role            VARCHAR(50) NOT NULL DEFAULT 'USER',  -- USER | ADMIN | BIS_OFFICER
    preferred_lang  VARCHAR(10) NOT NULL DEFAULT 'en',    -- en | hi
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    email_verified  BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_email ON users (email);
CREATE INDEX idx_users_role  ON users (role);

-- ============================================================
-- 2. REFRESH TOKENS
-- ============================================================
CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(512) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_tokens_user    ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_expires ON refresh_tokens (expires_at);

-- ============================================================
-- 3. CONVERSATIONS
-- ============================================================
CREATE TABLE conversations (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title       VARCHAR(500),                  -- auto-generated from first message
    lang        VARCHAR(10) NOT NULL DEFAULT 'en',
    is_archived BOOLEAN NOT NULL DEFAULT FALSE,
    metadata    JSONB DEFAULT '{}',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_conversations_user    ON conversations (user_id, created_at DESC);
CREATE INDEX idx_conversations_updated ON conversations (updated_at DESC);

-- ============================================================
-- 4. MESSAGES
-- ============================================================
CREATE TABLE messages (
    id               UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    conversation_id  UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    role             VARCHAR(20) NOT NULL,       -- user | assistant | system
    content          TEXT NOT NULL,
    content_hi       TEXT,                       -- Hindi translation (nullable)
    tokens_used      INTEGER,
    latency_ms       INTEGER,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_messages_conversation ON messages (conversation_id, created_at ASC);

-- ============================================================
-- 5. CITATIONS  (each assistant message can cite multiple chunks)
-- ============================================================
CREATE TABLE citations (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    message_id      UUID NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    chunk_id        VARCHAR(255) NOT NULL,    -- Qdrant point ID
    document_id     UUID,                    -- FK to bis_documents (nullable for legacy)
    is_number       VARCHAR(50),             -- e.g. "IS 14286:1995"
    clause_ref      VARCHAR(255),            -- e.g. "Clause 4.2.1"
    section_title   VARCHAR(500),
    excerpt         TEXT,                    -- short relevant snippet (≤300 chars)
    relevance_score FLOAT,
    sort_order      SMALLINT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_citations_message  ON citations (message_id);
CREATE INDEX idx_citations_chunk    ON citations (chunk_id);
CREATE INDEX idx_citations_is       ON citations (is_number);

-- ============================================================
-- 6. BIS DOCUMENTS  (metadata for ingested PDFs / pages)
-- ============================================================
CREATE TABLE bis_documents (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    source_url      VARCHAR(2000),
    file_path       VARCHAR(2000),             -- local storage path
    title           VARCHAR(1000) NOT NULL,
    doc_type        VARCHAR(100) NOT NULL,     -- IS_STANDARD | SCHEME_MANUAL | REGULATION | FAQ | HALLMARKING | CIRCULAR
    is_number       VARCHAR(100),              -- Indian Standard number if applicable
    year            SMALLINT,
    status          VARCHAR(50) DEFAULT 'ACTIVE',   -- ACTIVE | SUPERSEDED | WITHDRAWN
    page_count      INTEGER,
    checksum        VARCHAR(64),               -- SHA-256 to detect re-ingestion
    chunk_count     INTEGER DEFAULT 0,
    lang            VARCHAR(10) DEFAULT 'en',
    metadata        JSONB DEFAULT '{}',
    last_ingested   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_bis_docs_type       ON bis_documents (doc_type);
CREATE INDEX idx_bis_docs_is_number  ON bis_documents (is_number);
CREATE INDEX idx_bis_docs_status     ON bis_documents (status);
CREATE INDEX idx_bis_docs_title_trgm ON bis_documents USING gin (title gin_trgm_ops);

-- ============================================================
-- 7. DOCUMENT CHUNKS  (shadow table — Qdrant is the source of truth,
--    this keeps searchable metadata and full text for keyword fallback)
-- ============================================================
CREATE TABLE document_chunks (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    qdrant_point_id VARCHAR(255) UNIQUE NOT NULL,
    document_id     UUID NOT NULL REFERENCES bis_documents(id) ON DELETE CASCADE,
    chunk_index     INTEGER NOT NULL,
    content         TEXT NOT NULL,
    is_number       VARCHAR(100),
    clause_ref      VARCHAR(255),
    section_title   VARCHAR(500),
    page_number     INTEGER,
    token_count     INTEGER,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_chunks_document   ON document_chunks (document_id);
CREATE INDEX idx_chunks_qdrant     ON document_chunks (qdrant_point_id);
CREATE INDEX idx_chunks_is         ON document_chunks (is_number);
CREATE INDEX idx_chunks_content_ft ON document_chunks USING gin (to_tsvector('english', content));

-- ============================================================
-- 8. INGESTION JOBS
-- ============================================================
CREATE TABLE ingestion_jobs (
    id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    job_type     VARCHAR(100) NOT NULL,     -- FULL_CRAWL | INCREMENTAL | SINGLE_DOC
    status       VARCHAR(50) NOT NULL DEFAULT 'PENDING',  -- PENDING | RUNNING | DONE | FAILED
    triggered_by VARCHAR(255),
    docs_found   INTEGER DEFAULT 0,
    docs_ingested INTEGER DEFAULT 0,
    docs_failed  INTEGER DEFAULT 0,
    error_log    TEXT,
    started_at   TIMESTAMPTZ,
    finished_at  TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================
-- 9. FEEDBACK  (thumbs up/down per message)
-- ============================================================
CREATE TABLE feedback (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    message_id  UUID NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    user_id     UUID REFERENCES users(id) ON DELETE SET NULL,
    rating      SMALLINT NOT NULL CHECK (rating IN (-1, 1)),  -- -1 = down, 1 = up
    comment     TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_feedback_message ON feedback (message_id);
CREATE INDEX idx_feedback_user    ON feedback (user_id);
CREATE UNIQUE INDEX idx_feedback_user_message ON feedback (user_id, message_id);

-- ============================================================
-- 10. QUERY ANALYTICS
-- ============================================================
CREATE TABLE query_analytics (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id             UUID REFERENCES users(id) ON DELETE SET NULL,
    session_id          UUID,
    query_text          TEXT NOT NULL,
    detected_intent     VARCHAR(100),    -- FIND_STANDARD | CERT_SCHEME | LICENSING | HALLMARKING | LABS | GENERAL
    detected_lang       VARCHAR(10),
    detected_product    VARCHAR(255),
    detected_is_number  VARCHAR(100),
    result_count        INTEGER,
    top_score           FLOAT,
    latency_ms          INTEGER,
    was_fallback        BOOLEAN DEFAULT FALSE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_analytics_intent  ON query_analytics (detected_intent);
CREATE INDEX idx_analytics_created ON query_analytics (created_at DESC);

-- ============================================================
-- 11. IS CATALOGUE  (lightweight BIS standards index for fast lookup)
-- ============================================================
CREATE TABLE is_catalogue (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    is_number       VARCHAR(100) UNIQUE NOT NULL,  -- e.g. "IS 14286"
    title           VARCHAR(1000) NOT NULL,
    title_hi        VARCHAR(1000),
    year            SMALLINT,
    division        VARCHAR(255),                  -- BIS technical division
    product_scope   TEXT,                          -- plain-text product description
    cert_scheme     VARCHAR(100),                  -- SCHEME_I | CRS | FMCS | HALLMARKING | NONE
    is_mandatory    BOOLEAN DEFAULT FALSE,
    status          VARCHAR(50) DEFAULT 'CURRENT', -- CURRENT | SUPERSEDED | WITHDRAWN
    replaces        VARCHAR(100),                  -- superseded IS number
    replaced_by     VARCHAR(100),
    document_id     UUID REFERENCES bis_documents(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_is_cat_number  ON is_catalogue (is_number);
CREATE INDEX idx_is_cat_scheme  ON is_catalogue (cert_scheme);
CREATE INDEX idx_is_cat_status  ON is_catalogue (status);
CREATE INDEX idx_is_cat_title   ON is_catalogue USING gin (title gin_trgm_ops);

-- ============================================================
-- 12. AUDIT LOG
-- ============================================================
CREATE TABLE audit_log (
    id          BIGSERIAL PRIMARY KEY,
    actor_id    UUID,
    actor_email VARCHAR(255),
    action      VARCHAR(255) NOT NULL,
    entity_type VARCHAR(100),
    entity_id   VARCHAR(255),
    payload     JSONB,
    ip_address  INET,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_actor   ON audit_log (actor_id);
CREATE INDEX idx_audit_entity  ON audit_log (entity_type, entity_id);
CREATE INDEX idx_audit_created ON audit_log (created_at DESC);

-- ============================================================
-- TRIGGERS — auto-update updated_at
-- ============================================================
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
  NEW.updated_at = NOW();
  RETURN NEW;
END;
$$;

CREATE TRIGGER trg_users_updated           BEFORE UPDATE ON users            FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_conversations_updated   BEFORE UPDATE ON conversations     FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_bis_docs_updated        BEFORE UPDATE ON bis_documents     FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_is_catalogue_updated    BEFORE UPDATE ON is_catalogue      FOR EACH ROW EXECUTE FUNCTION set_updated_at();
