package com.example.financial.aggregation.dto;

import com.example.financial.common.type.TransactionStatus;
import com.example.financial.common.type.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionView(
    UUID transactionId,
    Instant timestamp,
    BigDecimal amount,
    String merchant,
    TransactionType category,
    TransactionStatus status,
    String description) {
}
