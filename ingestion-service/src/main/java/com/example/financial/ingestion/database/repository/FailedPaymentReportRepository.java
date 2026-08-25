package com.example.financial.ingestion.database.repository;

import com.example.financial.ingestion.database.entities.FailedPaymentReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FailedPaymentReportRepository extends JpaRepository<FailedPaymentReport, UUID> {
    List<FailedPaymentReport> findByFileHashOrderByRowNumber(String fileHash);
}