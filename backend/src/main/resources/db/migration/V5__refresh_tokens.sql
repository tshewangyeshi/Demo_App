-- Server-side revocation list for refresh tokens (see design doc, "Auth
-- mechanism: JWT (stateless), short-lived, httpOnly refresh"). Access tokens
-- are stateless JWTs and never touch this table; only the long-lived refresh
-- token — delivered as an httpOnly cookie, never readable by JS — is tracked
-- here, so logout/revocation is a real DB write, not just "the client
-- forgot the token."
CREATE TABLE refresh_token (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES app_user(id),
    -- SHA-256 hash of the token, never the raw value — a DB read (backup,
    -- leaked query log) can't be replayed as a live session.
    token_hash  TEXT NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_refresh_token_user ON refresh_token (user_id);

-- Not RLS-protected: this table is only ever touched by the auth service
-- using the caller's own userId baked into the lookup query, before any
-- RlsContext exists for the request.
