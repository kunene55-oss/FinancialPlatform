package com.example.financial.aggregation.dto;

import java.math.BigDecimal;

public record AccountTotal(String accountId, BigDecimal totalAmount) {
}
