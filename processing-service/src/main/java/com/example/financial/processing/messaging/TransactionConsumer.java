package com.example.financial.processing.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.kafka.annotation.KafkaListener;
import com.example.financial.processing.service.TransactionProcessingService;
import com.example.financial.common.event.TransactionReceivedEvent;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@RequiredArgsConstructor
@Component
public class TransactionConsumer {
    private final TransactionProcessingService service;

    @KafkaListener(topics = "transactions.raw", groupId = "costumGroup")
    public void consume(TransactionReceivedEvent event) {
        if (event.getPublishedAt() != null) {
            var lag = Duration.between(event.getPublishedAt(), Instant.now());
            log.info("Transaction {} received {} ms after publish", event.getTransactionId(), lag.toMillis());
        }
        service.processTransaction(event);
    }
}
