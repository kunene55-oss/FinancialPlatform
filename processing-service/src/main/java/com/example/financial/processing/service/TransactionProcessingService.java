package com.example.financial.processing.service;

import com.example.financial.common.event.TransactionReceivedEvent;
import com.example.financial.common.type.TransactionStatus;
import com.example.financial.processing.domain.TransactionEntity;
import com.example.financial.processing.repository.TransactionRepository;
import com.example.financial.processing.service.AccountService;


import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionProcessingService {
    private final TransactionRepository repo;
    private final AccountService accountService;

    @Transactional
    public void processTransaction(TransactionReceivedEvent event) {
        var existing = repo.findByTransactionId(event.getTransactionId());
        if (existing.isPresent()) {
            var entity = existing.get();
            if (entity.getStatus() != TransactionStatus.PROCESSING) {
                log.info("Transaction {} already processed with status {}, skipping", event.getTransactionId(), entity.getStatus());
                return;
            }
            log.warn("Transaction {} found stuck in PROCESSING, retrying", event.getTransactionId());
            accountService.processTransaction(entity);
            return;
        }

        if (event.getAmount() == null || event.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            log.error("Transaction {} has invalid amount: {}", event.getTransactionId(), event.getAmount());
            var entity = TransactionEntity.builder()
                .transactionId(event.getTransactionId())
                .accountId(event.getAccountId())
                .amount(event.getAmount())
                .category(event.getType())
                .status(TransactionStatus.FAILED)
                .build();
            repo.save(entity);
            return;
        }

        var entity = TransactionEntity.builder()
            .transactionId(event.getTransactionId())
            .accountId(event.getAccountId())
            .amount(event.getAmount())
            .category(event.getType())
            .status(TransactionStatus.PROCESSING)
            .build();
        repo.save(entity);
        accountService.processTransaction(entity);
    }
}
