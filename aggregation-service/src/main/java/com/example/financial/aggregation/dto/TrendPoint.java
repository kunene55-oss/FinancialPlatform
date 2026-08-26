package com.example.financial.aggregation.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record TrendPoint(Instant bucketStart, BigDecimal totalAmount, long transactionCount) {
}
