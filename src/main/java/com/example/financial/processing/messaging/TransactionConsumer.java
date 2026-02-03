package com.example.financial.processing.messaging;

import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import com.example.financial.processing.repository.TransactionRepository;
import com.example.financial.processing.domain.TransactionEntity;
import org.springframework.kafka.annotation.KafkaListener;
import com.example.financial.ingestion.dto.TransactionReceivedEvent;

@Service
@RequiredArgsConstructor
public class TransactionConsumer {
    private final TransactionRepository transactionRepository;

    @KafkaListener(topics ="", groupId = "")
    public void consume(TransactionReceivedEvent event) {
        TransactionEntity tx = new TransactionEntity();
        tx.setAccount(event.accountId);
        tx.setAmount(event.amount());
        tx.setMerchant(event.merchant());
        tx.setTimestamp(event.timestamp());
        tx.setCategory("GROCERIES");

        transactionRepository.save(tx);
    }
    
}
