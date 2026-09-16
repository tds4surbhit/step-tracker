CREATE TABLE accounts (
    id              UUID PRIMARY KEY,
    email           VARCHAR(255) NOT NULL UNIQUE,
    google_sub      VARCHAR(255) NOT NULL UNIQUE,
    name            VARCHAR(120),
    date_of_birth   DATE,
    height_cm       INTEGER,
    weight_kg       NUMERIC(5,2),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE refresh_token (
    id              UUID PRIMARY KEY,
    user_id         UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    token_hash      VARCHAR(255) NOT NULL UNIQUE,
    expires_at      TIMESTAMPTZ NOT NULL,
    revoked         BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_refresh_token_user_id ON refresh_token(user_id);
