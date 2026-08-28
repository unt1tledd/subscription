CREATE TYPE inbox_status AS ENUM (
    'PROCESSING',
    'PROCESSED',
    'FAILED'
);

CREATE TYPE incoming_payment_status AS ENUM (
    'SUCCEEDED',
    'FAILED',
    'UNKNOWN'
);

CREATE TYPE notification_status AS ENUM (
    'NEW',
    'SENT',
    'FAILED'
);

CREATE TABLE inbox_events
(
    event_id        UUID                    PRIMARY KEY,
    payment_id      UUID                    NOT NULL,
    payment_status  incoming_payment_status NOT NULL,
    payload         JSONB                   NOT NULL,
    status          inbox_status            NOT NULL DEFAULT 'PROCESSING',
    attempts        INTEGER                 NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    last_error      TEXT,
    received_at     TIMESTAMPTZ             NOT NULL DEFAULT NOW(),
    processed_at    TIMESTAMPTZ,
    updated_at      TIMESTAMPTZ             NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_inbox_processed_at
        CHECK (status <> 'PROCESSED' OR processed_at IS NOT NULL)
);

CREATE INDEX idx_inbox_events_payment_id
    ON inbox_events (payment_id);

CREATE INDEX idx_inbox_events_status
    ON inbox_events (status);

CREATE TABLE notifications
(
    id               UUID                    PRIMARY KEY,
    event_id         UUID                    NOT NULL UNIQUE,
    payment_id       UUID                    NOT NULL,
    user_id          BIGINT                  NOT NULL CHECK (user_id > 0),
    payment_status   incoming_payment_status NOT NULL,
    payload          JSONB                   NOT NULL,
    message          TEXT                    NOT NULL,
    status           notification_status     NOT NULL DEFAULT 'NEW',
    attempts         INTEGER                 NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    last_error       TEXT,
    next_attempt_at  TIMESTAMPTZ             NOT NULL DEFAULT NOW(),
    created_at       TIMESTAMPTZ             NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ             NOT NULL DEFAULT NOW(),
    sent_at          TIMESTAMPTZ,

    CONSTRAINT fk_notifications_inbox_event
        FOREIGN KEY (event_id)
            REFERENCES inbox_events (event_id),

    CONSTRAINT chk_notifications_sent_at
        CHECK (status <> 'SENT' OR sent_at IS NOT NULL)
);

CREATE INDEX idx_notifications_ready
    ON notifications (
        status,
        next_attempt_at,
        created_at
    );

CREATE INDEX idx_notifications_user_id
    ON notifications (user_id);
