package com.example.financial.aggregation.service;

import com.example.financial.aggregation.domain.TransactionEntity;
import com.example.financial.aggregation.dto.*;
import com.example.financial.aggregation.repository.AggregationRepository;
import com.example.financial.common.type.TransactionStatus;
import com.example.financial.common.type.TransactionType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AggregationService {

    private final AggregationRepository repository;

    public Map<String, BigDecimal> summary(String accountId, Instant from, Instant to) {
        validateRange(from, to);
        return repository.summarizeAccountHistory(accountId, from, to).stream()
            .collect(Collectors.toMap(r -> String.valueOf(r[0]), r -> (BigDecimal) r[1]));
    }

    public List<TrendPoint> trend(String accountId, TrendInterval interval, Instant from, Instant to) {
        validateRange(from, to);
        return repository.trendByInterval(accountId, interval.sqlField(), from, to).stream()
            .map(r -> new TrendPoint(
                ((java.sql.Timestamp) r[0]).toInstant(),
                (BigDecimal) r[1],
                ((Number) r[2]).longValue()))
            .toList();
    }

    public TotalsResponse totals(String accountId, Instant from, Instant to) {
        validateRange(from, to);
        Map<TransactionType, BigDecimal> byType = repository.summarizeAccountHistory(accountId, from, to).stream()
            .collect(Collectors.toMap(r -> (TransactionType) r[0], r -> (BigDecimal) r[1]));

        BigDecimal income = byType.getOrDefault(TransactionType.DEPOSIT, BigDecimal.ZERO);
        BigDecimal expense = byType.getOrDefault(TransactionType.WITHDRAWAL, BigDecimal.ZERO);
        return new TotalsResponse(income, expense, income.subtract(expense));
    }

    public Page<MerchantSummary> merchants(String accountId, Instant from, Instant to, Pageable pageable) {
        validateRange(from, to);
        return repository.merchantBreakdown(accountId, from, to, pageable)
            .map(r -> new MerchantSummary((String) r[0], (BigDecimal) r[1], ((Number) r[2]).longValue()));
    }

    public List<StatusSummary> statusSummary(String accountId, Instant from, Instant to) {
        validateRange(from, to);
        return repository.statusBreakdown(accountId, from, to).stream()
            .map(r -> new StatusSummary((TransactionStatus) r[0], (BigDecimal) r[1], ((Number) r[2]).longValue()))
            .toList();
    }

    public Page<TransactionView> transactions(String accountId, Instant from, Instant to, Pageable pageable) {
        validateRange(from, to);
        return repository.findByAccount(accountId, from, to, pageable)
            .map(this::toView);
    }

    public List<AccountTotal> accountTotals(List<String> accountIds, Instant from, Instant to) {
        if (accountIds == null || accountIds.isEmpty()) {
            throw new IllegalArgumentException("accountIds must not be empty");
        }
        validateRange(from, to);
        return repository.summarizeAccounts(accountIds, from, to).stream()
            .map(r -> new AccountTotal((String) r[0], (BigDecimal) r[1]))
            .toList();
    }

    private TransactionView toView(TransactionEntity t) {
        return new TransactionView(t.getTransactionId(), t.getTimestamp(), t.getAmount(),
            t.getMerchant(), t.getCategory(), t.getStatus(), t.getDescription());
    }

    private void validateRange(Instant from, Instant to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("'from' must not be after 'to'");
        }
    }
}
