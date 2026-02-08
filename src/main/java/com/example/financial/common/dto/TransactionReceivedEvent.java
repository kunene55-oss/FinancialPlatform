package com.example.financial.common.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionReceivedEvent(
    String transactionId,
    String accountId,
    BigDecimal amount,
    String merchant,
    String description,
    Instant timestamp,
    String source
) {
}
