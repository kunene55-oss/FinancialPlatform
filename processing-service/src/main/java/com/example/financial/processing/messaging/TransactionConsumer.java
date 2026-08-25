package com.example.financial.processing.messaging;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.kafka.annotation.KafkaListener;
import com.example.financial.processing.exception.TransactionValidationException;
import com.example.financial.processing.service.TransactionProcessingService;
import com.example.financial.common.event.TransactionReceivedEvent;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Component
public class TransactionConsumer {
    private final TransactionProcessingService service;
    private final Validator validator;

    @KafkaListener(topics = "transactions.raw", groupId = "costumGroup")
    public void consume(TransactionReceivedEvent event) {
        if (event.getPublishedAt() != null) {
            var lag = Duration.between(event.getPublishedAt(), Instant.now());
            log.info("Transaction {} received {} ms after publish", event.getTransactionId(), lag.toMillis());
        }

        Set<ConstraintViolation<TransactionReceivedEvent>> violations = validator.validate(event);
        if (!violations.isEmpty()) {
            String reasons = violations.stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining(", "));
            log.error("Transaction {} failed validation: {}", event.getTransactionId(), reasons);
            throw new TransactionValidationException(reasons);
        }

        service.processTransaction(event);
    }
}
