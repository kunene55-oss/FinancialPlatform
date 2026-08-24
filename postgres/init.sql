CREATE TABLE transactions (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP,
    transaction_id UUID UNIQUE,
    account_id VARCHAR(255),
    amount NUMERIC,
    merchant VARCHAR(255),
    category VARCHAR(255),
    status VARCHAR(255),
    description VARCHAR(255),
    timestamp TIMESTAMP
);

CREATE TABLE files (
    file_hash VARCHAR(255) PRIMARY KEY,
    transaction_count INTEGER
);

CREATE TABLE clients (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    account_id VARCHAR(255) UNIQUE,
    first_name VARCHAR(20),
    last_name VARCHAR(20),
    id_number BIGINT UNIQUE,
    account_status VARCHAR(255),
    balance NUMERIC,
    version BIGINT
);

CREATE SEQUENCE account_number_seq START WITH 10000001;