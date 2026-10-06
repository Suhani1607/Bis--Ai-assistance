CREATE TABLE refresh_token (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  token_hash CHAR(64) NOT NULL UNIQUE,
  expires_at TIMESTAMPTZ NOT NULL,
  revoked_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX refresh_token_user_idx ON refresh_token(user_id);
CREATE INDEX refresh_token_active_idx ON refresh_token(expires_at) WHERE revoked_at IS NULL;
