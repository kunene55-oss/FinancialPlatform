package com.example.financial.ingestion.database.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "failed_payment_reports")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FailedPaymentReport {
    @Id
    private UUID id;

    private String fileHash;
    private int rowNumber;
    private String rawRow;
    private String failureReason;
    private Instant createdAt;
}