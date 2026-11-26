-- V2: Payments, Payment Attempts, Events, Idempotency Records, and Provider Simulator Configs

CREATE TABLE provider_configs (
    provider_code        VARCHAR(32) PRIMARY KEY,
    display_name         VARCHAR(100) NOT NULL,
    enabled              BOOLEAN NOT NULL DEFAULT TRUE,
    failure_mode         VARCHAR(40) NOT NULL DEFAULT 'NORMAL_SUCCESS',
    latency_ms           INTEGER NOT NULL DEFAULT 35 CHECK (latency_ms >= 0),
    failure_rate_percent INTEGER NOT NULL DEFAULT 0 CHECK (failure_rate_percent BETWEEN 0 AND 100),
    timeout_rate_percent INTEGER NOT NULL DEFAULT 0 CHECK (timeout_rate_percent BETWEEN 0 AND 100),
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

INSERT INTO provider_configs (provider_code, display_name, enabled, failure_mode, latency_ms, failure_rate_percent, timeout_rate_percent) VALUES
    ('PROVIDER_ALPHA', 'Provider Alpha (Primary UPI/Card Sim)', TRUE, 'NORMAL_SUCCESS', 30, 0, 0),
    ('PROVIDER_BETA',  'Provider Beta (Secondary Gateway Sim)', TRUE, 'NORMAL_SUCCESS', 45, 0, 0),
    ('PROVIDER_GAMMA', 'Provider Gamma (Backup Rail Sim)', TRUE, 'NORMAL_SUCCESS', 60, 0, 0);

CREATE TABLE payments (
    id                    UUID PRIMARY KEY,
    merchant_id           UUID NOT NULL REFERENCES merchants(id),
    merchant_order_ref    VARCHAR(100) NOT NULL,
    amount_minor          BIGINT NOT NULL CHECK (amount_minor > 0),
    currency              VARCHAR(3) NOT NULL,
    status                VARCHAR(32) NOT NULL,
    refunded_amount_minor BIGINT NOT NULL DEFAULT 0 CHECK (refunded_amount_minor >= 0),
    selected_provider     VARCHAR(32) NOT NULL,
    provider_reference    VARCHAR(128),
    idempotency_key       VARCHAR(128) NOT NULL,
    failure_reason        VARCHAR(255),
    is_demo_data          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (merchant_id, idempotency_key)
);

CREATE INDEX idx_payments_merchant_created ON payments(merchant_id, created_at DESC);
CREATE INDEX idx_payments_status ON payments(status);
CREATE INDEX idx_payments_provider_ref ON payments(provider_reference);

CREATE TABLE idempotency_records (
    id              UUID PRIMARY KEY,
    merchant_id     UUID NOT NULL REFERENCES merchants(id),
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash    VARCHAR(64) NOT NULL,
    payment_id      UUID REFERENCES payments(id),
    status          VARCHAR(24) NOT NULL CHECK (status IN ('IN_PROGRESS', 'COMPLETED')),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (merchant_id, idempotency_key)
);

CREATE TABLE payment_attempts (
    id                  UUID PRIMARY KEY,
    payment_id          UUID NOT NULL REFERENCES payments(id) ON DELETE CASCADE,
    attempt_number      INTEGER NOT NULL CHECK (attempt_number > 0),
    provider_code       VARCHAR(32) NOT NULL,
    provider_request_id VARCHAR(128) NOT NULL UNIQUE,
    provider_reference  VARCHAR(128),
    outcome             VARCHAR(32) NOT NULL,
    latency_ms          INTEGER NOT NULL DEFAULT 0,
    error_code          VARCHAR(64),
    error_message       VARCHAR(255),
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payment_attempts_payment ON payment_attempts(payment_id);

CREATE TABLE payment_events (
    id             UUID PRIMARY KEY,
    payment_id     UUID NOT NULL REFERENCES payments(id) ON DELETE CASCADE,
    from_status    VARCHAR(32),
    to_status      VARCHAR(32) NOT NULL,
    event_type     VARCHAR(64) NOT NULL,
    actor          VARCHAR(200) NOT NULL,
    correlation_id VARCHAR(128),
    details_json   TEXT,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payment_events_payment ON payment_events(payment_id, created_at ASC);
