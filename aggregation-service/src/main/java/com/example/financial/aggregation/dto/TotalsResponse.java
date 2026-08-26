package com.example.financial.aggregation.dto;

import java.math.BigDecimal;

public record TotalsResponse(BigDecimal income, BigDecimal expense, BigDecimal transferIn, BigDecimal transferOut, BigDecimal net) {
}
