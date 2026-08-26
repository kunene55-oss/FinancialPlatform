package com.example.financial.aggregation.dto;

public enum TrendInterval {
    DAILY("day"),
    WEEKLY("week"),
    MONTHLY("month");

    private final String sqlField;

    TrendInterval(String sqlField) {
        this.sqlField = sqlField;
    }

    public String sqlField() {
        return sqlField;
    }
}
