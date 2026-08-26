package com.example.financial.processing.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import com.example.financial.common.type.TransactionStatus;
import com.example.financial.common.type.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "transactions")
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionEntity {

    @Id
    @Builder.Default
    private final UUID id = UUID.randomUUID();

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Version
    private long version;

    private UUID transactionId;
    private String accountId;
    private BigDecimal amount;
    private String merchant;


    @Enumerated(EnumType.STRING)
    private TransactionType category;

    @Enumerated(EnumType.STRING)
    private TransactionStatus status;

    private String description;
    private String transferId;
    private Instant timestamp;

}
