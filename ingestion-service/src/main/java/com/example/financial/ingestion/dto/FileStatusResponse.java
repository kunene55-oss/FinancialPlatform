package com.example.financial.ingestion.dto;

import com.example.financial.ingestion.database.entities.FileStatus;

public record FileStatusResponse(
    String fileHash,
    FileStatus status,
    int transactionCount,
    int failedTransactionCount) {
}
