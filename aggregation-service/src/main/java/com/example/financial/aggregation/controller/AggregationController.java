package com.example.financial.aggregation.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.example.financial.aggregation.repository.AggregationRepository;

import java.math.BigDecimal;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/aggregations")
public class AggregationController {

    private final AggregationRepository repository;

    @GetMapping("/{accountId}/summary")
    @PreAuthorize("hasRole('account-read') or hasRole('account-admin')")
    public Map<String, BigDecimal> summary(@PathVariable String accountId) {
        return repository.summarizeAccountHistory(accountId).stream()
            .collect(Collectors.toMap(
                r -> (String) r[0],
                r -> (BigDecimal) r[1]
        ));
    }
}
