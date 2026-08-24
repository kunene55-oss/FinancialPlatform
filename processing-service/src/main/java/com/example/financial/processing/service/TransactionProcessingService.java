package com.example.financial.processing.service;

import com.example.financial.common.event.TransactionReceivedEvent;
import com.example.financial.common.type.TransactionStatus;
import com.example.financial.processing.domain.TransactionEntity;
import com.example.financial.processing.repository.TransactionRepository;
import com.example.financial.processing.service.AccountService;


import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionProcessingService {
    private final TransactionRepository repo;
    private final AccountService accountService;

    public void processTransaction(TransactionReceivedEvent event) {
        if (event.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            log.error("Transaction {} has invalid amount: {}", event.getTransactionId(), event.getAmount());
            return;
        }
        if (repo.findByTransactionId(event.getTransactionId()).isPresent()) {
            log.error("Transaction {} has already been processed.", event.getTransactionId());
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
