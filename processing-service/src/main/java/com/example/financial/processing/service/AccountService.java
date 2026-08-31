package com.example.financial.processing.service;

import com.example.financial.processing.repository.ClientRepository;
import com.example.financial.processing.domain.ClientEntity;
import com.example.financial.processing.domain.AccountStatus;
import com.example.financial.common.type.TransactionType;
import com.example.financial.processing.domain.TransactionEntity;
import com.example.financial.common.type.TransactionStatus;
import com.example.financial.processing.repository.TransactionRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {
    private final ClientRepository clientRepo;
    private final TransactionRepository repo;

    @Transactional
    public void createAccount(final String name, final String surname, final Long idNumber ){
        if (name == null || surname == null || idNumber == null){
           log.error("Key information is missing from client profile please check details");
           throw new IllegalArgumentException("Name, Surname and ID number can not be null");
        }
        var client = clientRepo.findByIdNumber(idNumber);
        if (client.isPresent()) {
            log.error("Client account for ID number {} already present in DB", idNumber);
            throw new DataIntegrityViolationException(String.format("Client with Id %s already exists", idNumber));
        }
        try {
            String accountId = String.valueOf(clientRepo.nextAccountNumber());
            ClientEntity acc = ClientEntity.builder()
                        .accountId(accountId)
                        .firstName(name)
                        .lastName(surname)
                        .idNumber(idNumber)
                        .accountStatus(AccountStatus.OPEN)
                        .build();

            log.info("Account successfully created with accountId {}", accountId);
            clientRepo.save(acc);
        } catch (Exception ex) {
            log.error("Conflict account {} due to DB constraints", ex);
            throw ex;
        }
    }
    
    @Transactional
    public void deleteAccount(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            log.warn("Account ID is required to deleted account");
            throw new IllegalArgumentException("Account ID cannot be null or empty");
        }
        var client = clientRepo.findByAccountId(accountId)
            .orElseThrow(() -> {
                log.error("Client account {} not present in DB", accountId);
                return new EntityNotFoundException(String.format("No client with accountId %s exists", accountId));
            });
        try {
            log.info("Deleting account: {}", accountId);
            clientRepo.delete(client);
            log.info("Account {} successfully deleted", accountId);
        } catch (DataIntegrityViolationException ex) {
            log.error("Conflict deleting account {} due to DB constraints", accountId, ex);
            throw ex;
        }
        
    }

    @Transactional
    public void updateAccountStatus(String accountId, AccountStatus status ) {
        if (accountId == null || accountId.isBlank()) {
            log.warn("Account ID is required to deleted account");
            throw new IllegalArgumentException("Account ID cannot be null or empty");
        }
        
        var client = clientRepo.findByAccountId(accountId).orElseThrow(() -> {
                log.error("Client account {} not present in DB", accountId);
                throw new EntityNotFoundException(String.format("No client with accountId %s exists", accountId));
            });

        if (client.getAccountStatus() == status){
            log.warn("Account {} is already in status {}", accountId, status);
            throw new IllegalStateException(String.format("Account %s is already in status %s", accountId, status));
        }
        log.info("Client account {} is present in DB", accountId);
        client.setAccountStatus(status);
        clientRepo.save(client);
        log.info("Account status successfully updated to {}", status);
    }
   
    @Transactional
    public void processTransaction(final TransactionEntity entity) {
        log.info("Processing transaction entity for accountId: {}", entity.getAccountId());
        var client = clientRepo.findByAccountIdForUpdate(entity.getAccountId()).orElse(null);
        if (client == null) {
            log.error("Client does not exist, transaction cannot be processed");
            entity.setStatus(TransactionStatus.FAILED);
            repo.save(entity);
            return;
        }

        if (client.getAccountStatus() != AccountStatus.OPEN) {
            log.error("Account {} is in status {}. Please check account", client.getAccountId(), client.getAccountStatus());
            entity.setStatus(TransactionStatus.FAILED);
            repo.save(entity);
            return;
        }
         if (entity.getCategory() == null) {
            log.error("Transaction {} has no type, cannot be processed", entity.getTransactionId());
            entity.setStatus(TransactionStatus.FAILED);
            repo.save(entity);
            return;
        }

        if (entity.getAmount() == null || entity.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            log.error("Transaction {} has invalid amount: {}", entity.getTransactionId(), entity.getAmount());
            entity.setStatus(TransactionStatus.FAILED);
            repo.save(entity);
            return;
        }

        if (entity.getCategory() == TransactionType.TRANSFER && (entity.getTransferId() == null || entity.getTransferId().isBlank())) {
            log.error("Transaction {} is a transfer but has no transferId, cannot be processed", entity.getTransactionId());
            entity.setStatus(TransactionStatus.FAILED);
            repo.save(entity);
            return;
        }
        var balance = client.getBalance();
        var amount = entity.getAmount();
       
        switch (entity.getCategory()) {
            case TransactionType.DEPOSIT: {
                client.setBalance(balance.add(amount));
                clientRepo.save(client);
                entity.setStatus(TransactionStatus.PROCESSED);
                repo.save(entity);
                break;
            }
            case TransactionType.TRANSFER: {
                if ((balance.subtract(amount)).compareTo(BigDecimal.ZERO) < 0){
                    log.error("Account {} does not have enough funds available for transfer", entity.getAccountId());
                    entity.setStatus(TransactionStatus.FAILED);
                    repo.save(entity);
                    break;
                }
                if (entity.getTransferId().equals(entity.getAccountId())) {
                    log.error("Transaction {} is a transfer to its own account, cannot be processed", entity.getTransactionId());
                    entity.setStatus(TransactionStatus.FAILED);
                    repo.save(entity);
                    break;
                }
                var receivingClient = clientRepo.findByAccountIdForUpdate(entity.getTransferId()).orElse(null);
                if (receivingClient == null || receivingClient.getAccountStatus() != AccountStatus.OPEN) {
                    log.error("Receiving account {} does not exist or is not open, transfer cannot be processed", entity.getTransferId());
                    entity.setStatus(TransactionStatus.FAILED);
                    repo.save(entity);
                    break;
                }
                entity.setCategory(TransactionType.TRANSFER_OUT);
                client.setBalance(balance.subtract(amount));
                receivingClient.setBalance(receivingClient.getBalance().add(amount));
                clientRepo.save(client);
                clientRepo.save(receivingClient);

                entity.setStatus(TransactionStatus.PROCESSED);
                repo.save(entity);

                var creditEntity = TransactionEntity.builder()
                    .transactionId(UUID.randomUUID())
                    .accountId(receivingClient.getAccountId())
                    .amount(amount)
                    .category(TransactionType.TRANSFER_IN)
                    .status(TransactionStatus.PROCESSED)
                    .transferId(entity.getAccountId())
                    .timestamp(entity.getTimestamp())
                    .build();
                repo.save(creditEntity);
                log.info("Transfer transaction {} processed successfully, credited to account {}", entity.getTransactionId(), receivingClient.getAccountId());
                break;
            }
            case TransactionType.WITHDRAWAL: {
            
                if ((balance.subtract(amount)).compareTo(BigDecimal.ZERO) < 0){
                    log.error("Account {} does not have enough funds available for withdrawal", entity.getAccountId());
                    entity.setStatus(TransactionStatus.FAILED);
                    repo.save(entity);
                    break;
                }
                client.setBalance(balance.subtract(amount));
                clientRepo.save(client);
                entity.setStatus(TransactionStatus.PROCESSED);
                repo.save(entity);
                log.info("Withdrawal transaction {} processed successfully for account {}", entity.getTransactionId(), entity.getAccountId());
                break;
            }
            case TransactionType.TRANSFER_IN, TransactionType.TRANSFER_OUT: {
                log.error("Transaction {} is a transfer, which should be created automatically by the system", entity.getTransactionId());
                entity.setStatus(TransactionStatus.FAILED);
                repo.save(entity);
                break;
            }
        }
    }
    
}
