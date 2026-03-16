package com.example.financial.processing.service;

import com.example.financial.processing.repository.ClientRepository;
import com.example.financial.processing.domain.ClientEntity;
import com.example.financial.processing.domain.AccountStatus;
import com.example.financial.common.type.TransactionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {
    private final ClientRepository clientRepo;

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
    public void processTransaction(final BigDecimal amount, final String accountNumber, final TransactionType type){
        var client = clientRepo.findByAccountId(accountNumber).get();
        if (client == null) {
            log.error("Client does not exist, transaction cannot be processed");
            return;
        }
        var balance = client.getBalance();
        switch (type) {
            case TransactionType.DEPOSIT: {
                if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                    log.error("Deposit amounts must be greater than R0.00");
                    break;
                }
                client.setBalance(balance.add(amount));
                clientRepo.save(client);
                break;
            }
            case TransactionType.TRANSFER: {
                if ((balance.subtract(amount)).compareTo(BigDecimal.ZERO) <= 0){
                    log.error("Account {} does not have enough funds available for transfer", accountNumber);
                    break;
                }
                client.setBalance(balance.subtract(amount));
                clientRepo.save(client);
                break;
            }
            case TransactionType.WITHDRAWAL: {
                if ((balance.subtract(amount)).compareTo(BigDecimal.ZERO) <= 0){
                    log.error("Account {} does not have enough funds available for withdrawal", accountNumber);
                    break;
                }
                client.setBalance(balance.subtract(amount));
                clientRepo.save(client);
                break;
            }
        };
    }
    
}
