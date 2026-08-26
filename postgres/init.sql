CREATE TABLE transactions (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    version BIGINT,
    transaction_id UUID UNIQUE,
    account_id VARCHAR(255) NOT NULL,
    amount NUMERIC NOT NULL,
    merchant VARCHAR(255),
    category VARCHAR(255),
    status VARCHAR(255),
    description VARCHAR(255),
    transfer_id VARCHAR(255),
    timestamp TIMESTAMP
);

CREATE TABLE files (
    file_hash VARCHAR(255) PRIMARY KEY,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    version BIGINT,
    transaction_count INTEGER,
    failed_transaction_count INTEGER,
    status VARCHAR(255)
);

CREATE TABLE failed_payment_reports (
    id UUID PRIMARY KEY,
    file_hash VARCHAR(255),
    row_number INTEGER,
    raw_row TEXT,
    failure_reason VARCHAR(255),
    created_at TIMESTAMP
);

CREATE TABLE clients (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    account_id VARCHAR(255) UNIQUE NOT NULL,
    first_name VARCHAR(20),
    last_name VARCHAR(20),
    id_number BIGINT UNIQUE NOT NULL,
    account_status VARCHAR(255) NOT NULL,
    balance NUMERIC NOT NULL,
    version BIGINT
);

CREATE SEQUENCE account_number_seq START WITH 10000001;