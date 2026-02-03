package com.example.financial.processing.domain;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;


@Entity
@Table(name = "transactions")
@Data
public class TransactionEntity {
    @Id
    @GeneratedValue
    private UUID id;

    private String accountId;
    private BigDecimal amount;
    private String merchant;
    private String category;
    private Instant timestamp;
}
