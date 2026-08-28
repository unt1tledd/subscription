CREATE TYPE outbox_status AS ENUM (
    'NEW',
    'PROCESSING',
    'PUBLISHED',
    'FAILED'
);

CREATE TABLE outbox_events
(
    id              UUID           PRIMARY KEY,
    payment_id      UUID           NOT NULL,
    payment_status  payment_status NOT NULL,
    payload         JSONB          NOT NULL,
    status          outbox_status  NOT NULL DEFAULT 'NEW',
    attempts        INTEGER        NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    next_attempt_at TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    last_error      TEXT,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMPTZ,

    CONSTRAINT chk_outbox_events_published_at
        CHECK (
            status <> 'PUBLISHED'
            OR published_at IS NOT NULL
        )
);

CREATE INDEX idx_outbox_events_ready
    ON outbox_events (next_attempt_at, created_at)
    WHERE status IN ('NEW', 'FAILED');

CREATE INDEX idx_outbox_events_payment_id
    ON outbox_events (payment_id);
