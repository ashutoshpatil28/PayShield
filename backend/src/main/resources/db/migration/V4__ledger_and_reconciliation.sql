-- V4: Double-Entry Financial Ledger and Settlement Reconciliation

CREATE TABLE ledger_accounts (
    id           UUID PRIMARY KEY,
    account_code VARCHAR(80) NOT NULL UNIQUE,
    account_name VARCHAR(150) NOT NULL,
    account_type VARCHAR(24) NOT NULL CHECK (account_type IN ('ASSET', 'LIABILITY', 'EQUITY', 'REVENUE', 'EXPENSE')),
    merchant_id  UUID REFERENCES merchants(id),
    currency     VARCHAR(3) NOT NULL DEFAULT 'INR',
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE ledger_balances (
    ledger_account_id UUID PRIMARY KEY REFERENCES ledger_accounts(id),
    currency          VARCHAR(3) NOT NULL,
    balance_minor     BIGINT NOT NULL DEFAULT 0,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE journal_transactions (
    id                UUID PRIMARY KEY,
    reference_code    VARCHAR(100) NOT NULL UNIQUE,
    description       VARCHAR(255) NOT NULL,
    currency          VARCHAR(3) NOT NULL,
    reversal_of_tx_id UUID REFERENCES journal_transactions(id),
    created_by        VARCHAR(200) NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE journal_lines (
    id                UUID PRIMARY KEY,
    journal_tx_id     UUID NOT NULL REFERENCES journal_transactions(id),
    ledger_account_id UUID NOT NULL REFERENCES ledger_accounts(id),
    entry_type        VARCHAR(12) NOT NULL CHECK (entry_type IN ('DEBIT', 'CREDIT')),
    amount_minor      BIGINT NOT NULL CHECK (amount_minor > 0),
    currency          VARCHAR(3) NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_journal_lines_tx ON journal_lines(journal_tx_id);
CREATE INDEX idx_journal_lines_account ON journal_lines(ledger_account_id);

CREATE TABLE ledger_posting_references (
    id            UUID PRIMARY KEY,
    source_type   VARCHAR(40) NOT NULL,
    source_id     VARCHAR(128) NOT NULL,
    posting_type  VARCHAR(40) NOT NULL,
    journal_tx_id UUID NOT NULL REFERENCES journal_transactions(id),
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (source_type, source_id, posting_type)
);

-- Seed core chart of accounts (INR)
INSERT INTO ledger_accounts (id, account_code, account_name, account_type, merchant_id, currency) VALUES
    ('44444444-4444-4444-4444-444444444401', 'ASSET:PROVIDER_CLEARING:INR', 'Provider Clearing Receivable (INR)', 'ASSET', NULL, 'INR'),
    ('44444444-4444-4444-4444-444444444402', 'ASSET:BANK_SETTLEMENT:INR', 'Settlement Bank Account (INR)', 'ASSET', NULL, 'INR'),
    ('44444444-4444-4444-4444-444444444403', 'LIABILITY:MERCHANT_PAYABLE:22222222-2222-2222-2222-222222222201:INR', 'Acme Retail Payable (INR)', 'LIABILITY', '22222222-2222-2222-2222-222222222201', 'INR'),
    ('44444444-4444-4444-4444-444444444404', 'LIABILITY:MERCHANT_PAYABLE:22222222-2222-2222-2222-222222222202:INR', 'Nova Cloud Payable (INR)', 'LIABILITY', '22222222-2222-2222-2222-222222222202', 'INR'),
    ('44444444-4444-4444-4444-444444444405', 'REVENUE:PLATFORM_FEE:INR', 'Platform Processing Fee Revenue (INR)', 'REVENUE', NULL, 'INR'),
    ('44444444-4444-4444-4444-444444444406', 'EXPENSE:PROVIDER_FEE:INR', 'Provider Gateway Fee Expense (INR)', 'EXPENSE', NULL, 'INR');

INSERT INTO ledger_balances (ledger_account_id, currency, balance_minor) VALUES
    ('44444444-4444-4444-4444-444444444401', 'INR', 0),
    ('44444444-4444-4444-4444-444444444402', 'INR', 0),
    ('44444444-4444-4444-4444-444444444403', 'INR', 0),
    ('44444444-4444-4444-4444-444444444404', 'INR', 0),
    ('44444444-4444-4444-4444-444444444405', 'INR', 0),
    ('44444444-4444-4444-4444-444444444406', 'INR', 0);

-- Reconciliation tables
CREATE TABLE settlement_files (
    id                UUID PRIMARY KEY,
    file_name         VARCHAR(255) NOT NULL,
    file_sha256       VARCHAR(64) NOT NULL UNIQUE,
    provider_code     VARCHAR(32) NOT NULL,
    total_records     INTEGER NOT NULL DEFAULT 0,
    matched_records   INTEGER NOT NULL DEFAULT 0,
    unmatched_records INTEGER NOT NULL DEFAULT 0,
    uploaded_by       VARCHAR(200) NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE settlement_records (
    id                 UUID PRIMARY KEY,
    settlement_file_id UUID NOT NULL REFERENCES settlement_files(id) ON DELETE CASCADE,
    line_number        INTEGER NOT NULL,
    provider_reference VARCHAR(128) NOT NULL,
    merchant_order_ref VARCHAR(100),
    amount_minor       BIGINT NOT NULL,
    fee_minor          BIGINT NOT NULL DEFAULT 0,
    currency           VARCHAR(3) NOT NULL,
    settlement_date    VARCHAR(32) NOT NULL,
    match_status       VARCHAR(32) NOT NULL CHECK (match_status IN ('MATCHED', 'UNMATCHED', 'DISCREPANCY')),
    matched_payment_id UUID REFERENCES payments(id),
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_settlement_records_file ON settlement_records(settlement_file_id);

CREATE TABLE reconciliation_cases (
    id                   UUID PRIMARY KEY,
    settlement_file_id   UUID NOT NULL REFERENCES settlement_files(id) ON DELETE CASCADE,
    settlement_record_id UUID REFERENCES settlement_records(id),
    payment_id           UUID REFERENCES payments(id),
    exception_category   VARCHAR(48) NOT NULL,
    status               VARCHAR(32) NOT NULL CHECK (status IN ('OPEN', 'INVESTIGATING', 'RESOLVED', 'DISMISSED')),
    summary              VARCHAR(500) NOT NULL,
    resolution_notes     VARCHAR(1000),
    resolved_by          VARCHAR(200),
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    resolved_at          TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_recon_cases_status ON reconciliation_cases(status, created_at DESC);
