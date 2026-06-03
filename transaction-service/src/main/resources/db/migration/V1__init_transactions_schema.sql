-- V1__init_transactions_schema.sql

CREATE TYPE transaction_type   AS ENUM ('TRANSFER', 'DEPOSIT', 'WITHDRAWAL', 'REVERSAL');
CREATE TYPE transaction_status AS ENUM ('PENDING', 'COMPLETED', 'FAILED', 'REVERSED');

CREATE TABLE transactions (
    id                  UUID                PRIMARY KEY DEFAULT gen_random_uuid(),
    reference_id        VARCHAR(64)         NOT NULL UNIQUE,  -- idempotency key
    transaction_type    transaction_type    NOT NULL,
    status              transaction_status  NOT NULL DEFAULT 'PENDING',
    from_account        VARCHAR(20),
    to_account          VARCHAR(20),
    amount              NUMERIC(19, 4)      NOT NULL,
    currency            VARCHAR(3)          NOT NULL DEFAULT 'INR',
    description         VARCHAR(255),
    failure_reason      VARCHAR(500),
    initiated_by        UUID                NOT NULL,  -- user_id
    created_at          TIMESTAMP           NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP           NOT NULL DEFAULT NOW()
);

-- For looking up a user's transaction history efficiently
CREATE INDEX idx_txn_from_account  ON transactions(from_account);
CREATE INDEX idx_txn_to_account    ON transactions(to_account);
CREATE INDEX idx_txn_initiated_by  ON transactions(initiated_by);
CREATE INDEX idx_txn_reference_id  ON transactions(reference_id);
CREATE INDEX idx_txn_created_at    ON transactions(created_at DESC);
