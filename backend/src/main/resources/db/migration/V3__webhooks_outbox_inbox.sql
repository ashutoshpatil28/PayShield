-- V3: Transactional Outbox, Inbox Deduplication, and Provider Webhook Events

CREATE TABLE outbox_events (
    id             UUID PRIMARY KEY,
    event_type     VARCHAR(80) NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id   VARCHAR(128) NOT NULL,
    payload_json   TEXT NOT NULL,
    publish_status VARCHAR(24) NOT NULL CHECK (publish_status IN ('PENDING', 'PUBLISHED', 'FAILED')),
    retry_count    INTEGER NOT NULL DEFAULT 0,
    last_error     VARCHAR(500),
    correlation_id VARCHAR(128),
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    published_at   TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_outbox_pending ON outbox_events(publish_status, created_at ASC);

CREATE TABLE inbox_events (
    id             UUID PRIMARY KEY,
    consumer_name  VARCHAR(80) NOT NULL,
    event_key      VARCHAR(200) NOT NULL,
    correlation_id VARCHAR(128),
    processed_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (consumer_name, event_key)
);

CREATE TABLE provider_events (
    id                UUID PRIMARY KEY,
    provider_code     VARCHAR(32) NOT NULL,
    provider_event_id VARCHAR(128) NOT NULL,
    payment_id        UUID REFERENCES payments(id),
    event_type        VARCHAR(64) NOT NULL,
    payload_json      TEXT NOT NULL,
    signature_valid   BOOLEAN NOT NULL,
    processing_status VARCHAR(24) NOT NULL CHECK (processing_status IN ('PROCESSED', 'DUPLICATE', 'REJECTED', 'FAILED')),
    error_message     VARCHAR(500),
    received_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    processed_at      TIMESTAMP WITH TIME ZONE,
    UNIQUE (provider_code, provider_event_id)
);

CREATE INDEX idx_provider_events_payment ON provider_events(payment_id);
