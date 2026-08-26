package com.example.financial.aggregation.dto;

import com.example.financial.common.type.TransactionStatus;

import java.math.BigDecimal;

public record StatusSummary(TransactionStatus status, BigDecimal totalAmount, long transactionCount) {
}
