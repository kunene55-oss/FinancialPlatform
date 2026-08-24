package com.example.financial.processing.dto;

import java.time.LocalDateTime;
import lombok.Data;

import com.example.financial.common.type.TransactionType;

@Data
public class ReportRequest {
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private TransactionType transactionType;
    private int page = 0;
    private int size = 20;
}
