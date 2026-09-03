package com.example.financial.processing.service;

import com.example.financial.processing.domain.*;
import com.example.financial.processing.repository.ClientRepository;
import com.example.financial.processing.repository.TransactionRepository;
import com.example.financial.common.type.TransactionType;
import com.example.financial.common.type.TransactionStatus;

import jakarta.persistence.EntityNotFoundException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AccountServiceTest {
    @Mock
    private ClientRepository clientRepo;

    @Mock
    private TransactionRepository transactionRepo;

    @InjectMocks
    private AccountService accountService;

    private ClientEntity client;

    @BeforeEach
    void setup() {
        client = ClientEntity.builder()
            .accountId("ACC123")
            .firstName("John")
            .lastName("Doe")
            .idNumber(123L)
            .accountStatus(AccountStatus.OPEN).balance(BigDecimal.valueOf(1000))
            .build();
    }

    @Test
    void createAccount_shouldSaveClient_whenValid() {
        when(clientRepo.findByIdNumber(123L)).thenReturn(Optional.empty());
        accountService.createAccount("John", "Doe", 123L);
        verify(clientRepo).save(any(ClientEntity.class));
    }

    //Null field
    @Test
    void createAccount_shouldThrowException_whenInputIsNull() {
        assertThrows(IllegalArgumentException.class, 
            () -> accountService.createAccount(null, "Doe", 123L));
    }

    //Duplicate client creation
    @Test
    void createAccount_shouldThrowException_whenClientAlreadyExists() {
        when(clientRepo.findByIdNumber(123L)).thenReturn(Optional.of(client));
        assertThrows(DataIntegrityViolationException.class, 
            () -> accountService.createAccount("John", "Doe", 123L));
    }

    //Successful delete
    @Test
    void deleteAccount_shouldDelete_whenClientExists() {
        when(clientRepo.findByAccountId("ACC123")).thenReturn(Optional.of(client));
        accountService.deleteAccount("ACC123");
        verify(clientRepo).delete(client);
    }

    //Null/blank test
    @Test
    void deleteAccount_shouldThrowException_whenInvalidId() {
        assertThrows(IllegalArgumentException.class,
            () -> accountService.deleteAccount(""));
    }

    //Delete null
    @Test
    void deleteAccount_shouldThrowException_whenNotFound() {
        when(clientRepo.findByAccountId("ACC123")).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class,
            () -> accountService.deleteAccount("ACC123"));
    }

    //Update Account status
    @Test
    void updateAccountStatus_shouldUpdate_whenValid() {
        when(clientRepo.findByAccountId("ACC123")).thenReturn(Optional.of(client));
        accountService.updateAccountStatus("ACC123", AccountStatus.CLOSED);
        assertEquals(AccountStatus.CLOSED, client.getAccountStatus());
        verify(clientRepo).save(client);
    }

    //Update to same status
    @Test
    void updateAccountStatus_shouldThrowException_whenSameStatus() {
        when(clientRepo.findByAccountId("ACC123")).thenReturn(Optional.of(client));
        assertThrows(IllegalStateException.class,
            () -> accountService.updateAccountStatus("ACC123", AccountStatus.OPEN));
    }

    //Update not found
    @Test
    void updateAccountStatus_shouldThrow_whenClientNotFound() {
        when(clientRepo.findByAccountId("ACC123")).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class,
            () -> accountService.updateAccountStatus("ACC123", AccountStatus.OPEN));
    }
}
