package com.example.financial.ingestion.service;

import com.example.financial.common.event.TransactionReceivedEvent;
import com.example.financial.common.type.TransactionType;
import com.example.financial.ingestion.database.entities.FailedPaymentReport;
import com.example.financial.ingestion.database.entities.FileEntity;
import com.example.financial.ingestion.database.entities.FileStatus;
import com.example.financial.ingestion.database.repository.FailedPaymentReportRepository;
import com.example.financial.ingestion.database.repository.FileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileIngestionWorker {

    private final KafkaTemplate<String, TransactionReceivedEvent> kafkaTemplate;
    private final FileRepository fileRepo;
    private final FailedPaymentReportRepository failedPaymentReportRepo;

    @Value("${ingestion.max-rows-per-file:50000}")
    private int maxRowsPerFile;

    @Value("${ingestion.max-processing-duration-seconds:300}")
    private long maxProcessingDurationSeconds;

    @Async("fileIngestionExecutor")
    public void process(Path tempFile, String fileHash) {
        var entity = fileRepo.findByFileHash(fileHash)
            .orElseThrow(() -> new IllegalStateException("File record missing for hash " + fileHash));

        int transactionCount = 0;
        int failedTransactionCount = 0;
        int rowNumber = 0;
        Instant start = Instant.now();
        try (BufferedReader reader = Files.newBufferedReader(tempFile);
             CSVParser parser = CSVFormat.DEFAULT.parse(reader)) {
            log.info("File ingestion started for hash {} with max rows {} and max duration {} seconds", fileHash, maxRowsPerFile, maxProcessingDurationSeconds);
            for (CSVRecord record : parser) {
                rowNumber = (int) record.getRecordNumber();

                if (rowNumber > maxRowsPerFile) {
                    throw new IllegalStateException(
                        "File exceeds max row quota of " + maxRowsPerFile + " rows");
                }
                if (Duration.between(start, Instant.now()).getSeconds() > maxProcessingDurationSeconds) {
                    throw new IllegalStateException(
                        "File processing exceeded max duration of " + maxProcessingDurationSeconds + "s");
                }

                TransactionReceivedEvent event;
                try {
                    event = parseRecord(record, rowNumber);
                } catch (Exception e) {
                    failedPaymentReportRepo.save(FailedPaymentReport.builder()
                        .id(UUID.randomUUID())
                        .fileHash(fileHash)
                        .rowNumber(rowNumber)
                        .rawRow(record.toString())
                        .failureReason(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())
                        .createdAt(Instant.now())
                        .build());
                    failedTransactionCount++;
                    log.error("Failed to parse row {} for file {}: {}", rowNumber, fileHash, e.getMessage());
                    continue;
                }
                publish(event);
                log.info("Transaction: {}, successfully published", transactionCount);
                transactionCount++;
            }
            entity.setStatus(failedTransactionCount == 0 ? FileStatus.COMPLETED : FileStatus.PARTIAL);
            entity.setTransactionCount(transactionCount);
            entity.setFailedTransactionCount(failedTransactionCount);
            fileRepo.save(entity);

        } catch (Exception e) {
            log.error("File ingestion aborted for hash {} after {} transactions", fileHash, transactionCount, e);
            entity.setStatus(FileStatus.FAILED);
            entity.setTransactionCount(transactionCount);
            entity.setFailedTransactionCount(failedTransactionCount);
            fileRepo.save(entity);
        } finally {
            deleteQuietly(tempFile);
        }
    }

    private void publish(final TransactionReceivedEvent event) {
        event.setPublishedAt(Instant.now());
        try {
            kafkaTemplate.send("transactions.raw", event.getAccountId(), event).get();
            log.info("Published transaction event with transactionId {}", event.getTransactionId());
        } catch (Exception e) {
            log.error("Failed to publish event with transactionId {}", event.getTransactionId(), e);
            throw new RuntimeException("Failed to publish transaction event", e);
        }
    }

    private TransactionReceivedEvent parseRecord(CSVRecord parts, int row) {
        if (parts.size() < 4 || parts.size() > 6) {
            throw new IllegalArgumentException("Invalid format in row: " + row);
        }
        var type = TransactionType.valueOf(parts.get(3).trim().toUpperCase());

        int expectedColumns = type == TransactionType.TRANSFER ? 6 : 5;
        if (parts.size() != expectedColumns) {
            throw new IllegalArgumentException("Invalid format in row: " + row);
        }

        var eventBuilder = TransactionReceivedEvent.builder()
            .transactionId(UUID.fromString(parts.get(0).trim()))
            .accountId(parts.get(1).trim())
            .amount(new BigDecimal(parts.get(2).trim()))
            .type(type)
            .timestamp(Instant.parse(parts.get(4).trim()));
        if (type == TransactionType.TRANSFER) {
            eventBuilder.transferId(parts.get(5).trim());
        }

        var event = eventBuilder.build();
        log.info("Transaction with id: {}, successfully ingested", parts.get(0));
        return event;
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
            log.info("Temp ingestion file {} deleted successfully", path);
        } catch (IOException e) {
            log.warn("Failed to delete temp ingestion file {}", path, e);
        }
    }
}
