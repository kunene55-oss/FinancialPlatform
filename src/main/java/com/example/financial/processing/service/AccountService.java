package com.example.financial.processing.service;

import com.example.financial.processing.repository.ClientRepository;
import com.example.financial.processing.domain.ClientEntity;
import com.example.financial.processing.domain.AccountStatus;
import com.example.financial.common.type.TransactionType;
import com.example.financial.processing.domain.TransactionEntity;
import com.example.financial.common.type.TransactionStatus;
import com.example.financial.processing.repository.TransactionRepository;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {
    private final ClientRepository clientRepo;
    private final TransactionRepository repo;

    public void createAccount(final String name, final String surname, final Long idNumber ){
        if (name == null || surname == null || idNumber == null){
            return;
        }
        clientRepo.findByIdNumber(idNumber)
            .ifPresentOrElse( client -> log.warn("Client with ID number: {} already exists", idNumber),
                () -> {
                    ClientEntity acc = ClientEntity.builder()
                        .firstName(name)
                        .lastName(surname)
                        .idNumber(idNumber)
                        .accountStatus(AccountStatus.OPEN)
                        .build();
                    
                    log.info("Account successfully created");
                    clientRepo.save(acc);
                }
            );
        
    }
    public void createPreloadedAccount(final String firstName, final String lastName, final BigDecimal amount){
        return;
    }
    
    public void deleteAccount(String accountId) {
        clientRepo.findByAccountId(accountId)
            .ifPresentOrElse(client -> {
                log.info("Client account {} is present in DB", accountId);
                clientRepo.deleteByAccountId(accountId);
                log.info("Account successfully removed");
            }, () -> {
                log.error("Client account {} not present in DB", accountId);
            });
    }

    public void updateAccountStatus(String accountId, AccountStatus status ) {
        clientRepo.findByAccountId(accountId)
            .ifPresentOrElse(client -> {
                if (client.getAccountStatus() == status) {
                    log.warn("Account {} is already in status {}", accountId, status);
                    return;
                }
                log.info("Client account {} is present in DB", accountId);
                client.setAccountStatus(status);
                clientRepo.save(client);
                log.info("Account status successfully updated to {}", status);
            }, () -> {
                log.error("Client account {} not present in DB", accountId);
            });
    }
    //public void updateClient(){}
    public void processTransaction(final TransactionEntity entity){//final BigDecimal amount, final String accountNumber, final TransactionType type){
        var client = clientRepo.findByAccountId(entity.getAccountId()).get();
        if (client == null) {
            log.error("Client does not exist, transaction cannot be processed");
            return;
        }

        if (client.getAccountStatus() != AccountStatus.OPEN) {
            log.error("Account {} is in status {}. Please check account", client.getAccountId(), client.getAccountStatus());
            return;
        }
        var balance = client.getBalance();
        var amount = entity.getAmount();
        switch (entity.getCategory()) {
            case TransactionType.DEPOSIT: {
                if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                    log.error("Deposit amounts must be greater than R0.00");
                    entity.setStatus(TransactionStatus.FAILED);
                    repo.save(entity);
                    break;
                }
                client.setBalance(balance.add(amount));
                clientRepo.save(client);
                entity.setStatus(TransactionStatus.PROCESSED);
                repo.save(entity);
                break;
            }
            case TransactionType.TRANSFER: {
                if ((balance.subtract(amount)).compareTo(BigDecimal.ZERO) <= 0){
                    log.error("Account {} does not have enough funds available for transfer", entity.getAccountId());
                    entity.setStatus(TransactionStatus.FAILED);
                    repo.save(entity);
                    break;
                }
                client.setBalance(balance.subtract(amount));
                clientRepo.save(client);
                entity.setStatus(TransactionStatus.PROCESSED);
                repo.save(entity);
                break;
            }
            case TransactionType.WITHDRAWAL: {
                if ((balance.subtract(amount)).compareTo(BigDecimal.ZERO) <= 0){
                    log.error("Account {} does not have enough funds available for withdrawal", entity.getAccountId());
                    entity.setStatus(TransactionStatus.FAILED);
                    repo.save(entity);
                    break;
                }
                client.setBalance(balance.subtract(amount));
                clientRepo.save(client);
                entity.setStatus(TransactionStatus.PROCESSED);
                repo.save(entity);
                break;
            }
        };
    }
    
}
