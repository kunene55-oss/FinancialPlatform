package com.example.financial.common.event;

import lombok.Data;
import com.example.financial.common.type.TransactionType;
import com.example.financial.common.type.TransactionStatus;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import jakarta.validation.constraints.NotBlank;
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

    @NotNull(message = "AccountId cannot be null")
    @NotBlank(message = "AccountId cannot be blank")
    private String accountId;

    @NotNull(message = "Transaction amount cannot be null")
    private BigDecimal amount;
    
    @NotNull(message = "Transaction type cannot be null")
    private TransactionType type;
    private TransactionStatus status;

    @NotNull
    private Instant timestamp;
    private Instant publishedAt;
    private String transferId;
}
