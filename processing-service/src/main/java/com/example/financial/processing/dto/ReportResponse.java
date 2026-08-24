package com.example.financial.processing.dto;

import lombok.Data;
import lombok.Builder;
import java.util.List;

@Data
@Builder
public class ReportResponse<T> {
    private String reportName;
    private Long totalRecords;
    private List<T> data;
}
