package com.example.financial.common.event;

import lombok.Data;
import com.example.financial.common.type.TransactionType;
import com.example.financial.common.type.TransactionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
public class TransactionReceivedEvent {
    private UUID transactionId;
    private String accountId;
    private BigDecimal amount;
    private TransactionType type;
    private TransactionStatus status;
    private Instant timestamp;
}
