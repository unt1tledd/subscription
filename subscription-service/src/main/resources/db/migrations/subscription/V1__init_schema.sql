CREATE TYPE sub_status AS ENUM (
    'NEW',
    'PENDING_PAYMENT',
    'ACTIVE',
    'FAILED_PAYMENT',
    'CANCELLED'
);

CREATE TABLE plans
(
    id            UUID         PRIMARY KEY,
    code          VARCHAR(64)  NOT NULL UNIQUE,
    name          VARCHAR(128) NOT NULL,
    price         BIGINT       NOT NULL CHECK (price >= 0),
    currency      VARCHAR(3)   NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    duration_days INTEGER      NOT NULL CHECK (duration_days > 0),
    active        BOOLEAN      NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL
);


CREATE TABLE subscriptions
(
    id              UUID         PRIMARY KEY,
    user_id         BIGINT       NOT NULL CHECK (user_id > 0),
    plan_id         UUID         NOT NULL,
    status          sub_status       DEFAULT 'NEW',
    started_at      TIMESTAMPTZ,
    expires_at      TIMESTAMPTZ,
    auto_renew      BOOLEAN      NOT NULL,
    cancelled_at    TIMESTAMPTZ,
    version         BIGINT       NOT NULL DEFAULT 0,
    idempotency_key VARCHAR(128) NOT NULL UNIQUE,
    created_at      TIMESTAMPTZ  DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  DEFAULT NOW(),

    CONSTRAINT fk_subscriptions_plan
        FOREIGN KEY (plan_id)
            REFERENCES plans (id),

    CONSTRAINT chk_subscriptions_dates
        CHECK (
            started_at IS NULL
            OR expires_at IS NULL
            OR expires_at > started_at
        )
);


CREATE INDEX idx_subscriptions_user_status
    ON subscriptions (user_id, status);