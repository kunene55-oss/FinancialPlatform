package com.example.financial.processing.domain; 

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.springframework.data.annotation.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import jakarta.validation.constraints.NotNull;
import com.example.financial.processing.domain.AccountStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "clients")
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientEntity {

    @Id
    @Builder.Default
    private final UUID id = UUID.randomUUID();

    private String accountId;

    @NotNull
    private String firstName;
    @NotNull
    private String lastName;
    @NotNull
    private Long idNumber;

    @Enumerated(EnumType.STRING)
    private AccountStatus accountStatus;

    @Builder.Default
    private BigDecimal balance = BigDecimal.ZERO;

    @CreatedDate
    private Instant created_at;

    @LastModifiedDate
    private Instant updated_at;

    @Version
    private long version;

}
