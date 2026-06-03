-- V1__init_accounts_schema.sql

CREATE TYPE account_type AS ENUM ('SAVINGS', 'CURRENT', 'FIXED_DEPOSIT');
CREATE TYPE account_status AS ENUM ('ACTIVE', 'FROZEN', 'CLOSED');

CREATE TABLE accounts (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    account_number  VARCHAR(20)     NOT NULL UNIQUE,
    user_id         UUID            NOT NULL,
    account_type    account_type    NOT NULL,
    status          account_status  NOT NULL DEFAULT 'ACTIVE',
    balance         NUMERIC(19, 4)  NOT NULL DEFAULT 0.0000,
    currency        VARCHAR(3)      NOT NULL DEFAULT 'INR',
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_accounts_user_id        ON accounts(user_id);
CREATE INDEX idx_accounts_account_number ON accounts(account_number);
CREATE INDEX idx_accounts_status         ON accounts(status);
