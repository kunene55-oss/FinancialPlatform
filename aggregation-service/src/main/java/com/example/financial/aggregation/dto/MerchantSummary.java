package com.example.financial.aggregation.dto;

import java.math.BigDecimal;

public record MerchantSummary(String merchant, BigDecimal totalAmount, long transactionCount) {
}
