package com.example.financial.ingestion.dto;

import java.math.BigDecimal;
import java.util.UUID;
import java.time.Instant;

public record TransactionReceivedEvent(
    UUID transactionId,
    String accountId,
    BigDecimal amount,
    String merchant,
    String description,
    Instant timestamp,
    String source
) {
}
