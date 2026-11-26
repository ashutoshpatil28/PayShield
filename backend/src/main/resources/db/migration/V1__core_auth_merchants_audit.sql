-- V1: Core Auth, Merchants, and Append-Only Audit Events

CREATE TABLE merchants (
    id              UUID PRIMARY KEY,
    merchant_code   VARCHAR(64) NOT NULL UNIQUE,
    legal_name      VARCHAR(200) NOT NULL,
    display_name    VARCHAR(200) NOT NULL,
    status          VARCHAR(32) NOT NULL CHECK (status IN ('ACTIVE', 'SUSPENDED', 'ONBOARDING')),
    default_currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    is_demo_data    BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE roles (
    id          UUID PRIMARY KEY,
    name        VARCHAR(64) NOT NULL UNIQUE,
    description VARCHAR(255) NOT NULL
);

CREATE TABLE users (
    id            UUID PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    full_name     VARCHAR(200) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    merchant_id   UUID REFERENCES merchants(id),
    active        BOOLEAN NOT NULL DEFAULT TRUE,
    is_demo_user  BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_merchant_id ON users(merchant_id);

CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(128) NOT NULL UNIQUE,
    expires_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at  TIMESTAMP WITH TIME ZONE,
    replaced_by UUID,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);

CREATE TABLE audit_events (
    id             UUID PRIMARY KEY,
    actor_user_id  UUID,
    actor_email    VARCHAR(255),
    actor_roles    VARCHAR(512),
    action         VARCHAR(100) NOT NULL,
    resource_type  VARCHAR(64) NOT NULL,
    resource_id    VARCHAR(128),
    outcome        VARCHAR(32) NOT NULL CHECK (outcome IN ('SUCCESS', 'FAILURE', 'DENIED')),
    correlation_id VARCHAR(128),
    ip_address     VARCHAR(64),
    details_json   TEXT,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_events_created_at ON audit_events(created_at DESC);
CREATE INDEX idx_audit_events_actor_user_id ON audit_events(actor_user_id);
CREATE INDEX idx_audit_events_action ON audit_events(action);

-- Seed canonical RBAC roles
INSERT INTO roles (id, name, description) VALUES
    ('11111111-1111-1111-1111-111111111101', 'PLATFORM_ADMIN', 'Full platform administration and configuration'),
    ('11111111-1111-1111-1111-111111111102', 'MERCHANT_ADMIN', 'Merchant-scoped operations and payment initiation'),
    ('11111111-1111-1111-1111-111111111103', 'OPERATIONS_ANALYST', 'Payment operations, provider monitoring, and reconciliation'),
    ('11111111-1111-1111-1111-111111111104', 'RISK_ANALYST', 'Risk alert investigation and rule review'),
    ('11111111-1111-1111-1111-111111111105', 'PAYOUT_MAKER', 'Initiate payout requests (cannot approve own payouts)'),
    ('11111111-1111-1111-1111-111111111106', 'PAYOUT_CHECKER', 'Approve or reject payout requests created by makers'),
    ('11111111-1111-1111-1111-111111111107', 'FINANCIAL_AUDITOR', 'Read-only access to ledger, audit trail, and reconciliation');

-- Seed synthetic demo merchants
INSERT INTO merchants (id, merchant_code, legal_name, display_name, status, default_currency, is_demo_data) VALUES
    ('22222222-2222-2222-2222-222222222201', 'MRC_ACME_RETAIL', 'Acme Retail Technologies Pvt Ltd (Demo)', 'Acme Retail (Demo)', 'ACTIVE', 'INR', TRUE),
    ('22222222-2222-2222-2222-222222222202', 'MRC_NOVA_CLOUD', 'Nova Cloud Services India Pvt Ltd (Demo)', 'Nova Cloud (Demo)', 'ACTIVE', 'INR', TRUE);
