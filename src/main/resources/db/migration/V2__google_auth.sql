ALTER TABLE users
    DROP COLUMN password_hash,
    ADD COLUMN google_sub VARCHAR(255);

UPDATE users SET google_sub = 'unset-' || id::text WHERE google_sub IS NULL;

ALTER TABLE users
    ALTER COLUMN google_sub SET NOT NULL,
    ADD CONSTRAINT uq_users_google_sub UNIQUE (google_sub);
