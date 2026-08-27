package com.example.financial.processing.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.example.financial.processing.domain.ClientEntity;

import java.util.UUID;
import java.util.Optional;

@Repository
public interface ClientRepository extends JpaRepository<ClientEntity, UUID> {
    Optional<ClientEntity> findByIdNumber(Long idNumber);
    Optional<ClientEntity> findByAccountId(String accountId);
    void deleteByAccountId(String accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM ClientEntity c WHERE c.accountId = :accountId")
    Optional<ClientEntity> findByAccountIdForUpdate(@Param("accountId") String accountId);

    @Query(value = "SELECT nextval('account_number_seq')", nativeQuery = true)
    Long nextAccountNumber();
}
