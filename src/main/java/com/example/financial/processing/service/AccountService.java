package com.example.financial.processing.service;

import com.example.financial.processing.repository.ClientRepository;
import com.example.financial.processing.domain.ClientEntity;
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
        if (clientRepo.findByIdNumber(idNumber) != null){
            return;
        }
        var client = ClientEntity.builder()
            .firstName(name)
            .lastName(surname)
            .idNumber(idNumber)
            .build();
        clientRepo.save(client);
    }
    public void createPreloadedAccount(final String firstName, final String lastName, final BigDecimal amount){
        return;
    }
    //public void deleteAccount(){}
    //public void updateClient(){}
}
