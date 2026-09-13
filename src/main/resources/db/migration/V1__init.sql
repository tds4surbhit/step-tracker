CREATE TABLE users (
    id              UUID PRIMARY KEY,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    display_name    VARCHAR(120),
    date_of_birth   DATE,
    height_cm       INTEGER,
    weight_kg       NUMERIC(5,2),
    gender          VARCHAR(30),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE devices (
    id              UUID PRIMARY KEY,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    platform        VARCHAR(20) NOT NULL CHECK (platform IN ('ios', 'android')),
    health_source   VARCHAR(20) NOT NULL CHECK (health_source IN ('healthkit', 'health_connect', 'sensor', 'manual')),
    last_synced_at  TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_devices_user_id ON devices(user_id);

CREATE TABLE daily_step_summary (
    id              UUID PRIMARY KEY,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    entry_date      DATE NOT NULL,
    step_count      INTEGER NOT NULL CHECK (step_count >= 0),
    source          VARCHAR(20) NOT NULL CHECK (source IN ('healthkit', 'health_connect', 'sensor', 'manual')),
    timezone        VARCHAR(50),
    synced_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, entry_date)
);

CREATE INDEX idx_daily_step_summary_user_date ON daily_step_summary(user_id, entry_date);

CREATE TABLE step_goal (
    user_id             UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    daily_goal_steps    INTEGER NOT NULL DEFAULT 10000 CHECK (daily_goal_steps > 0),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE refresh_token (
    id              UUID PRIMARY KEY,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash      VARCHAR(255) NOT NULL UNIQUE,
    expires_at      TIMESTAMPTZ NOT NULL,
    revoked         BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_refresh_token_user_id ON refresh_token(user_id);
