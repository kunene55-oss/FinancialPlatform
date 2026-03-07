package com.example.financial.ingestion.service;

import com.example.financial.common.event.TransactionReceivedEvent;
import com.example.financial.ingestion.util.FileValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.multipart.MultipartFile;
import com.example.financial.ingestion.database.entities.FileEntity;
import com.example.financial.ingestion.database.repository.FileRepository;

import java.time.Instant;
import java.util.UUID;
import java.math.BigDecimal;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
@RequiredArgsConstructor
@Slf4j
public class IngestionService {
    private final KafkaTemplate<String, TransactionReceivedEvent> kafkaTemplate;

    private final FileRepository fileRepo;


    public void publish(final TransactionReceivedEvent event) {
        if (event.getTransactionId().toString() != null ) {
            kafkaTemplate.send("transaction.raw", event.getTransactionId().toString(), event);
        } 
    }


    public void ingestFile(MultipartFile file) {
        FileValidator.validate(file.getOriginalFilename());
        String hash = hash(file);

        if (fileRepo.findByFileHash(hash).isPresent()) {
            throw new IllegalStateException("File has already been processed");
        }
        var entity = FileEntity.builder().fileHash(hash).build();
        

        try(BufferedReader reader = 
                new BufferedReader(
                    new InputStreamReader(
                        file.getInputStream()
                    ))) 
        {
            String line;
            int transactionCount = 0;
            while ((line = reader.readLine()) != null) {
                try {
                    TransactionReceivedEvent event = parseLine(line);
                    publish(event);
                    transactionCount++;
                } catch (Exception e) {
                    log.error("Failed to process line: {} | Error: {}", line, e.getMessage());
                }
            }
            entity.setTransactionCount(transactionCount);
            fileRepo.save(entity);

        } catch (Exception e) {
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

    private TransactionReceivedEvent parseLine(String line) {
        String[] parts = line.split(",");

        if (parts.length < 5) {
            throw new IllegalArgumentException("Invalid for in row");
        } 

        TransactionReceivedEvent event = new TransactionReceivedEvent();
        event.setTransactionId(UUID.fromString(parts[0]));
        event.setAccountId(parts[1]);
        event.setAmount(new BigDecimal(parts[2]));
        event.setTimestamp(Instant.now());
        return event;
    }
    
    
}
