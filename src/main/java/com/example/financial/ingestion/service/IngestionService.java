package com.example.financial.ingestion.service;

import com.example.financial.ingestion.dto.TransactionReceivedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.kafka.core.KafkaTemplate;
import reactor.core.publisher.Flux;

import java.util.UUID;
import java.math.BigDecimal;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class IngestionService {

    private final KafkaTemplate<String, TransactionReceivedEvent> kafkaTemplate;

    public Flux<TransactionReceivedEvent> ingestMockTransactions() {
        return Flux.just(
            new TransactionReceivedEvent(
                UUID.randomUUID(),
                "ACC-123456789",
                BigDecimal.ONE,
                "Checkers",
                "Groceries",
                Instant.now(),
                "MOCK"
            )
        ).doOnNext( tx -> kafkaTemplate.send("transactions.raw", tx.transactionId().toString(), tx));
    }
    
}
