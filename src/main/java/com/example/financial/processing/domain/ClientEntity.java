package com.example.financial.processing.domain;

import lombok.Data;
import lombok.Builder;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.data.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Data
@Builder
public class ClientEntity {

    @Id
    @Builder.Default
    private final UUID id = UUID.randomUUID();

    private String accountId;

    private String firstName;
    private String lastName;

    @Builder.Default
    private BigDecimal balance = BigDecimal.ZERO;

    @CreatedDate
    private Instant created_at;

    @LastModifiedDate
    private Instant updated_at;

    @Version
    private long version;

}
