package com.example.financial.processing.domain;

import java.math.BigDecimal;

public interface TransactionSummaryProjection {

    String getAccountId();

    Long getTransactionCount();

    BigDecimal getTotalAmount();
}