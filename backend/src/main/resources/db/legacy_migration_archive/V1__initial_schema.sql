CREATE TABLE app_user (
  id UUID PRIMARY KEY,
  email VARCHAR(255) NOT NULL UNIQUE,
  name VARCHAR(100) NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  role VARCHAR(30) NOT NULL DEFAULT 'USER',
  preferred_lang VARCHAR(5) NOT NULL DEFAULT 'en',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE is_catalogue (
  is_number VARCHAR(60) PRIMARY KEY,
  title TEXT NOT NULL,
  year INTEGER,
  division VARCHAR(100),
  cert_scheme VARCHAR(30) NOT NULL DEFAULT 'NONE',
  mandatory BOOLEAN NOT NULL DEFAULT false,
  status VARCHAR(30) NOT NULL DEFAULT 'CURRENT'
);
CREATE TABLE conversation (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES app_user(id),
  title VARCHAR(250),
  lang VARCHAR(5) NOT NULL DEFAULT 'en',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE message (
  id UUID PRIMARY KEY,
  conversation_id UUID NOT NULL REFERENCES conversation(id) ON DELETE CASCADE,
  role VARCHAR(20) NOT NULL,
  content TEXT NOT NULL,
  citations JSONB NOT NULL DEFAULT '[]'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX conversation_user_updated_idx ON conversation(user_id, updated_at DESC);
CREATE INDEX message_conversation_created_idx ON message(conversation_id, created_at);
