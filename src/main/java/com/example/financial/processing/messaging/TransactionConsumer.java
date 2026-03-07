package com.example.financial.processing.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.kafka.annotation.KafkaListener;
import com.example.financial.processing.service.TransactionProcessingService;
import com.example.financial.common.event.TransactionReceivedEvent;

@RequiredArgsConstructor
@Component
public class TransactionConsumer {
    private final TransactionProcessingService service;

    @KafkaListener(topics = "transactions.raw")
    public void consume(TransactionReceivedEvent event) {
        service.process(event);
    }
}
