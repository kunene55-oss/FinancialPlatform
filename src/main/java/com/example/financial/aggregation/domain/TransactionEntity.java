package com.example.financial.aggregation.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import com.example.financial.common.type.TransactionStatus;
import com.example.financial.common.type.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transactions")
@Data
public class TransactionEntity {
    @Id
    private UUID id;

    private UUID transactionId;
    private String accountId;
    private BigDecimal amount;
    private String merchant;

    @Enumerated(EnumType.STRING)
    private TransactionType category;

    @Enumerated(EnumType.STRING)
    private TransactionStatus status;

    private String descriptions;
    private Instant timestamp;
}
