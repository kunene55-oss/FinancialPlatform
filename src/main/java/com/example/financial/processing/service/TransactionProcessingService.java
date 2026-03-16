package com.example.financial.processing.service;

import com.example.financial.common.event.TransactionReceivedEvent;
import com.example.financial.common.type.TransactionStatus;
import com.example.financial.processing.domain.TransactionEntity;
import com.example.financial.processing.repository.TransactionRepository;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionProcessingService {
    private final TransactionRepository repo;

    public void process(TransactionReceivedEvent event) {
        if (repo.findByTransactionId(event.getTransactionId()).isPresent()) {
            log.error("Transaction {} has already been processed.", event.getTransactionId());
            return;
        }

        var entity = TransactionEntity.builder()
            .transactionId(event.getTransactionId())
            .accountId(event.getAccountId())
            .amount(event.getAmount())
            .category(event.getType())
            .status(TransactionStatus.PROCESSED)
            .build();
        repo.save(entity);
    }
}
