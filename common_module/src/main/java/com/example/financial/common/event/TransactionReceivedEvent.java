package com.example.financial.common.event;

import lombok.Data;
import com.example.financial.common.type.TransactionType;
import com.example.financial.common.type.TransactionStatus;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionReceivedEvent {

    @NotNull(message = "TransactionId cannot be null")
    private UUID transactionId;

    private String accountId;

    @NotNull(message = "Transaction amount cannot be null")
    private BigDecimal amount;
    
    private TransactionType type;
    private TransactionStatus status;
    private Instant timestamp;
}
