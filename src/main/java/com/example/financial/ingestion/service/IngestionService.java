package com.example.financial.ingestion.service;

import com.example.financial.common.event.TransactionReceivedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.multipart.MultipartFile;
import com.example.financial.ingestion.database.entities.FileEntity;
import com.example.financial.ingestion.database.repository.FileRepository;
import java.time.Instant;
import java.util.UUID;
import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class IngestionService {
    private final KafkaTemplate<String, TransactionReceivedEvent> kafkaTemplate;

    private final FileRepository fileRepo;


    public void publish(final TransactionReceivedEvent event) {
        if (event.getTransactionId().toString() != null ) {
            kafkaTemplate.send("transaction.raw", event.getTransactionId().toString(), event);
        } 
    }


    public void ingestFile(MultipartFile file) {

    }

    private String hash(String filename) {
        return "";
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
