-- V5: Payout Authorization (Maker-Checker, Kill Switch) and Rule-Based Risk Engine

CREATE TABLE beneficiaries (
    id                    UUID PRIMARY KEY,
    merchant_id           UUID NOT NULL REFERENCES merchants(id),
    beneficiary_name      VARCHAR(150) NOT NULL,
    account_number_masked VARCHAR(32) NOT NULL,
    ifsc_or_routing       VARCHAR(32) NOT NULL,
    allowlisted           BOOLEAN NOT NULL DEFAULT TRUE,
    is_demo_data          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

INSERT INTO beneficiaries (id, merchant_id, beneficiary_name, account_number_masked, ifsc_or_routing, allowlisted, is_demo_data) VALUES
    ('55555555-5555-5555-5555-555555555501', '22222222-2222-2222-2222-222222222201', 'Acme Logistics Vendor (Demo)', 'XXXX-XXXX-4821', 'SIMB0001001', TRUE, TRUE),
    ('55555555-5555-5555-5555-555555555502', '22222222-2222-2222-2222-222222222201', 'Unverified External Vendor (Demo)', 'XXXX-XXXX-9910', 'SIMB0002002', FALSE, TRUE),
    ('55555555-5555-5555-5555-555555555503', '22222222-2222-2222-2222-222222222202', 'Nova Cloud Infra Partner (Demo)', 'XXXX-XXXX-3314', 'SIMB0003003', TRUE, TRUE);

CREATE TABLE payout_kill_switch (
    id         VARCHAR(32) PRIMARY KEY,
    enabled    BOOLEAN NOT NULL DEFAULT FALSE,
    reason     VARCHAR(255),
    updated_by VARCHAR(200) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

INSERT INTO payout_kill_switch (id, enabled, reason, updated_by) VALUES
    ('GLOBAL', FALSE, 'Normal operations', 'SYSTEM_SEED');

CREATE TABLE payout_requests (
    id                  UUID PRIMARY KEY,
    merchant_id         UUID NOT NULL REFERENCES merchants(id),
    beneficiary_id      UUID NOT NULL REFERENCES beneficiaries(id),
    amount_minor        BIGINT NOT NULL CHECK (amount_minor > 0),
    currency            VARCHAR(3) NOT NULL,
    status              VARCHAR(32) NOT NULL,
    purpose             VARCHAR(255) NOT NULL,
    maker_user_id       UUID NOT NULL REFERENCES users(id),
    risk_score          INTEGER NOT NULL DEFAULT 0,
    approval_expires_at TIMESTAMP WITH TIME ZONE,
    failure_reason      VARCHAR(255),
    is_demo_data        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payout_requests_merchant ON payout_requests(merchant_id, created_at DESC);
CREATE INDEX idx_payout_requests_status ON payout_requests(status);

CREATE TABLE payout_approvals (
    id                UUID PRIMARY KEY,
    payout_request_id UUID NOT NULL UNIQUE REFERENCES payout_requests(id) ON DELETE CASCADE,
    maker_user_id     UUID NOT NULL REFERENCES users(id),
    checker_user_id   UUID NOT NULL REFERENCES users(id),
    decision          VARCHAR(24) NOT NULL CHECK (decision IN ('APPROVED', 'REJECTED')),
    comments          VARCHAR(500),
    decided_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_maker_not_checker CHECK (maker_user_id <> checker_user_id)
);

CREATE TABLE risk_rules (
    rule_code       VARCHAR(64) PRIMARY KEY,
    name            VARCHAR(150) NOT NULL,
    description     VARCHAR(500) NOT NULL,
    severity        VARCHAR(24) NOT NULL CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    score_weight    INTEGER NOT NULL CHECK (score_weight BETWEEN 1 AND 100),
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    threshold_value BIGINT NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

INSERT INTO risk_rules (rule_code, name, description, severity, score_weight, enabled, threshold_value) VALUES
    ('HIGH_AMOUNT_TX', 'High Transaction Amount', 'Flags payments or payouts exceeding configured minor-unit threshold', 'HIGH', 45, TRUE, 10000000),
    ('VELOCITY_SPIKE', 'Merchant Velocity Spike', 'Flags rapid consecutive transactions within a short window', 'MEDIUM', 30, TRUE, 5),
    ('REPEATED_FAILED_PAYMENTS', 'Repeated Failed Payments', 'Flags multiple failed payment attempts for a merchant', 'HIGH', 40, TRUE, 3),
    ('NEW_BENEFICIARY_PAYOUT', 'High-Value Payout Review', 'Flags payouts above secondary review threshold', 'MEDIUM', 35, TRUE, 2500000),
    ('SUSPICIOUS_PROVIDER_ACTIVITY', 'Provider Timeout / Error Anomaly', 'Flags transactions entering UNKNOWN or provider error states', 'MEDIUM', 25, TRUE, 1),
    ('INVALID_AUTH_ATTEMPTS', 'Repeated Authentication Failures', 'Flags repeated failed login attempts', 'HIGH', 50, TRUE, 5);

CREATE TABLE risk_alerts (
    id            UUID PRIMARY KEY,
    rule_code     VARCHAR(64) NOT NULL REFERENCES risk_rules(rule_code),
    entity_type   VARCHAR(32) NOT NULL,
    entity_id     VARCHAR(128) NOT NULL,
    merchant_id   UUID REFERENCES merchants(id),
    severity      VARCHAR(24) NOT NULL CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    risk_score    INTEGER NOT NULL,
    status        VARCHAR(24) NOT NULL CHECK (status IN ('OPEN', 'ACKNOWLEDGED', 'RESOLVED', 'DISMISSED')),
    explanation   VARCHAR(500) NOT NULL,
    analyst_notes VARCHAR(1000),
    resolved_by   VARCHAR(200),
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    resolved_at   TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_risk_alerts_status ON risk_alerts(status, created_at DESC);
