package com.example.financial.ingestion.service;

import com.example.financial.common.event.TransactionReceivedEvent;
import com.example.financial.ingestion.util.FileValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.multipart.MultipartFile;
import com.example.financial.ingestion.database.entities.FileEntity;
import com.example.financial.ingestion.database.entities.FileStatus;
import com.example.financial.ingestion.database.repository.FileRepository;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.Base64;

@Service
@RequiredArgsConstructor
@Slf4j
public class IngestionService {
    private final KafkaTemplate<String, TransactionReceivedEvent> kafkaTemplate;

    private final FileRepository fileRepo;
    private final FileIngestionWorker fileIngestionWorker;


    public void publish(final TransactionReceivedEvent event) {
        event.setPublishedAt(Instant.now());
        try {
            kafkaTemplate.send("transactions.raw", event.getAccountId(), event).get();
            log.info("Published transaction event with transactionId {}", event.getTransactionId());
        } catch (Exception e) {
            log.error("Failed to publish event with transactionId {}", event.getTransactionId(), e);
            throw new RuntimeException("Failed to publish transaction event", e);
        }
    }


    public String initiateIngestion(MultipartFile file) {
        FileValidator.validate(file.getOriginalFilename());
        log.info("File ingestion started for {}", file.getOriginalFilename());
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
            log.info("File ingestion record created for hash {}", hash);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalStateException("File is currently being processed");
        }

        Path tempFile;
        try {
            tempFile = Files.createTempFile("ingest-", ".csv");
            file.transferTo(tempFile);
            log.info("File {} staged for ingestion at {}", file.getOriginalFilename(), tempFile);
        } catch (IOException e) {
            entity.setStatus(FileStatus.FAILED);
            fileRepo.save(entity);
            throw new RuntimeException("Failed to stage uploaded file", e);
        }

        try {
            fileIngestionWorker.process(tempFile, hash);
        } catch (TaskRejectedException e) {
            log.warn("File ingestion rejected for hash {}, worker pool saturated", hash);
            deleteQuietly(tempFile);
            entity.setStatus(FileStatus.FAILED);
            fileRepo.save(entity);
            throw e;
        }

        return hash;
    }

    private String hash(MultipartFile file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (DigestInputStream dis = new DigestInputStream(file.getInputStream(), digest)) {
                byte[] buffer = new byte[8192];
                while (dis.read(buffer) != -1) {
                    // reading drives the digest update
                }
            }
            return Base64.getEncoder().encodeToString(digest.digest());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Failed to delete temp ingestion file {}", path, e);
        }
    }
}
