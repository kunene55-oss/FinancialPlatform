package com.example.financial.ingestion.service;

import com.example.financial.common.event.TransactionReceivedEvent;
import com.example.financial.common.type.TransactionType;
import com.example.financial.ingestion.util.FileValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.multipart.MultipartFile;
import com.example.financial.ingestion.database.entities.FileEntity;
import com.example.financial.ingestion.database.entities.FileStatus;
import com.example.financial.ingestion.database.entities.FailedPaymentReport;
import com.example.financial.ingestion.database.repository.FileRepository;
import com.example.financial.ingestion.database.repository.FailedPaymentReportRepository;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.UUID;
import java.math.BigDecimal;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.security.MessageDigest;
import java.util.Base64;

@Service
@RequiredArgsConstructor
@Slf4j
public class IngestionService {
    private final KafkaTemplate<String, TransactionReceivedEvent> kafkaTemplate;

    private final FileRepository fileRepo;
    private final FailedPaymentReportRepository failedPaymentReportRepo;


    public void publish(final TransactionReceivedEvent event) {
        event.setPublishedAt(Instant.now());
        try {
            kafkaTemplate.send("transactions.raw", event.getTransactionId().toString(), event).get();
        } catch (Exception e) {
            log.error("Failed to publish event with transactionId {}", event.getTransactionId(), e);
            throw new RuntimeException("Failed to publish transaction event", e);
        }
    }


    public void ingestFile(MultipartFile file) {
        FileValidator.validate(file.getOriginalFilename());
        String hash = hash(file);

        var existing = fileRepo.findByFileHash(hash);
        if (existing.isPresent() && existing.get().getStatus() != FileStatus.FAILED) {
            throw new IllegalStateException(
                "File has already been processed or is currently being processed (status: " + existing.get().getStatus() + ")");
        }

        var entity = FileEntity.builder()
            .fileHash(hash)
            .status(FileStatus.PROCESSING)
            .transactionCount(0)
            .build();
        try {
            fileRepo.save(entity);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalStateException("File is currently being processed");
        }

        int transactionCount = 0;
        int failedTransactionCount = 0;
        int rowNumber = 0;
        try(BufferedReader reader =
                new BufferedReader(
                    new InputStreamReader(
                        file.getInputStream()
                    )))
        {
            String line;
            while ((line = reader.readLine()) != null) {
                rowNumber++;
                TransactionReceivedEvent event;
                try {
                    event = parseLine(line, rowNumber);
                } catch (Exception e) {
                    failedPaymentReportRepo.save(FailedPaymentReport.builder()
                        .id(UUID.randomUUID())
                        .fileHash(hash)
                        .rowNumber(rowNumber)
                        .rawRow(line)
                        .failureReason(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())
                        .createdAt(Instant.now())
                        .build());
                    failedTransactionCount++;
                    log.error("Failed to parse row {} for file {}: {}", rowNumber, hash, e.getMessage());
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
            log.error("File ingestion aborted for hash {} after {} transactions", hash, transactionCount, e);
            entity.setStatus(FileStatus.FAILED);
            entity.setTransactionCount(transactionCount);
            entity.setFailedTransactionCount(failedTransactionCount);
            fileRepo.save(entity);
            throw new RuntimeException(e);
        }
    }

    private String hash(MultipartFile file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(file.getBytes());
            return Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
       
    }

    private TransactionReceivedEvent parseLine(String line, int row) {
        String[] parts = line.split(",", -1);

        if (parts.length < 5) {
            var output = "Invalid format in row: " + row;
            throw new IllegalArgumentException(output);
        }

        var event = TransactionReceivedEvent.builder()
            .transactionId(UUID.fromString(parts[0].trim()))
            .accountId(parts[1].trim())
            .amount(new BigDecimal(parts[2].trim()))
            .type(TransactionType.valueOf(parts[3].trim().toUpperCase()))
            .timestamp(Instant.parse(parts[4].trim())).build();
        log.info("Transaction with id: {}, successfully ingested", parts[0]);
        return event;
    }
    
    
}
