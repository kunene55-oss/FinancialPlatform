package com.example.financial.processing.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.example.financial.processing.domain.ClientEntity;

import java.util.UUID;
import java.util.Optional;

@Repository
public interface ClientRepository extends JpaRepository<ClientEntity, UUID> {
    Optional<ClientEntity> findByIdNumber(Long idNumber);
    Optional<ClientEntity> findByAccountId(String accountId);
    void deleteByAccountId(String accountId);
}
