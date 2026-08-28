CREATE TYPE payment_status AS ENUM (
    'PENDING',
    'SUCCEEDED',
    'FAILED',
    'UNKNOWN'
);

CREATE TABLE payments
(
    id                    UUID           PRIMARY KEY,
    user_id               BIGINT         NOT NULL CHECK (user_id > 0),
    amount                BIGINT         NOT NULL CHECK (amount > 0),
    currency              VARCHAR(3)     NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    status                payment_status NOT NULL DEFAULT 'PENDING',
    failure_code          VARCHAR(64),
    idempotency_key       VARCHAR(128)   NOT NULL UNIQUE,
    status_check_attempts INTEGER        NOT NULL DEFAULT 0,
    version               BIGINT         NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payments_user_created_at
    ON payments (user_id, created_at DESC);

CREATE INDEX idx_payments_pending_updated_at
    ON payments (updated_at)
    WHERE status = 'PENDING';
