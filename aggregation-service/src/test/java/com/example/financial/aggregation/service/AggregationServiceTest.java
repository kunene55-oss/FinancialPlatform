package com.example.financial.aggregation.service;

import com.example.financial.aggregation.domain.TransactionEntity;
import com.example.financial.aggregation.repository.AggregationRepository;
import com.example.financial.common.type.TransactionStatus;
import com.example.financial.common.type.TransactionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AggregationServiceTest {

    @Mock
    private AggregationRepository repository;

    @InjectMocks
    private AggregationService service;

    @Test
    void summary_mapsCategoryRowsToTotals() {
        when(repository.summarizeAccountHistory(eq("acc-1"), any(), any()))
            .thenReturn(List.<Object[]>of(new Object[]{TransactionType.DEPOSIT, new BigDecimal("100")}));

        var result = service.summary("acc-1", null, null);

        assertEquals(new BigDecimal("100"), result.get("DEPOSIT"));
    }

    @Test
    void summary_throwsWhenFromAfterTo() {
        Instant from = Instant.now();
        Instant to = from.minusSeconds(60);

        assertThrows(IllegalArgumentException.class, () -> service.summary("acc-1", from, to));
    }

    @Test
    void totals_computesIncomeExpenseAndSurfacesTransferInAndOut() {
        when(repository.summarizeAccountHistory(eq("acc-1"), any(), any())).thenReturn(List.<Object[]>of(
            new Object[]{TransactionType.DEPOSIT, new BigDecimal("100")},
            new Object[]{TransactionType.WITHDRAWAL, new BigDecimal("40")},
            new Object[]{TransactionType.TRANSFER_IN, new BigDecimal("25")},
            new Object[]{TransactionType.TRANSFER_OUT, new BigDecimal("10")}
        ));

        var result = service.totals("acc-1", null, null);

        assertEquals(new BigDecimal("100"), result.income());
        assertEquals(new BigDecimal("40"), result.expense());
        assertEquals(new BigDecimal("25"), result.transferIn());
        assertEquals(new BigDecimal("10"), result.transferOut());
        assertEquals(new BigDecimal("75"), result.net());
    }

    @Test
    void totals_defaultsMissingCategoriesToZero() {
        when(repository.summarizeAccountHistory(eq("acc-1"), any(), any())).thenReturn(List.of());

        var result = service.totals("acc-1", null, null);

        assertEquals(BigDecimal.ZERO, result.income());
        assertEquals(BigDecimal.ZERO, result.expense());
        assertEquals(BigDecimal.ZERO, result.transferIn());
        assertEquals(BigDecimal.ZERO, result.transferOut());
        assertEquals(BigDecimal.ZERO, result.net());
    }

    @Test
    void accountTotals_throwsWhenAccountIdsEmpty() {
        assertThrows(IllegalArgumentException.class, () -> service.accountTotals(List.of(), null, null));
    }

    @Test
    void accountTotals_throwsWhenAccountIdsNull() {
        assertThrows(IllegalArgumentException.class, () -> service.accountTotals(null, null, null));
    }

    @Test
    void accountTotals_mapsRepositoryRows() {
        when(repository.summarizeAccounts(eq(List.of("acc-1")), any(), any()))
            .thenReturn(List.<Object[]>of(new Object[]{"acc-1", new BigDecimal("50")}));

        var result = service.accountTotals(List.of("acc-1"), null, null);

        assertEquals(1, result.size());
        assertEquals("acc-1", result.get(0).accountId());
        assertEquals(new BigDecimal("50"), result.get(0).totalAmount());
    }

    @Test
    void merchants_mapsPageRowsToMerchantSummary() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Object[]> page = new PageImpl<>(List.<Object[]>of(new Object[]{"Amazon", new BigDecimal("30"), 3L}));
        when(repository.merchantBreakdown(eq("acc-1"), any(), any(), eq(pageable))).thenReturn(page);

        var result = service.merchants("acc-1", null, null, pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals("Amazon", result.getContent().get(0).merchant());
        assertEquals(3L, result.getContent().get(0).transactionCount());
    }

    @Test
    void statusSummary_mapsRowsToStatusSummary() {
        when(repository.statusBreakdown(eq("acc-1"), any(), any()))
            .thenReturn(List.<Object[]>of(new Object[]{TransactionStatus.PROCESSED, new BigDecimal("75"), 2L}));

        var result = service.statusSummary("acc-1", null, null);

        assertEquals(1, result.size());
        assertEquals(TransactionStatus.PROCESSED, result.get(0).status());
        assertEquals(2L, result.get(0).transactionCount());
    }

    @Test
    void transactions_mapsEntityToTransactionView() {
        Pageable pageable = PageRequest.of(0, 20);
        TransactionEntity entity = new TransactionEntity();
        entity.setTransactionId(UUID.randomUUID());
        entity.setAmount(new BigDecimal("12.50"));
        entity.setMerchant("Shop");
        entity.setCategory(TransactionType.WITHDRAWAL);
        entity.setStatus(TransactionStatus.PROCESSED);
        Page<TransactionEntity> page = new PageImpl<>(List.of(entity));
        when(repository.findByAccount(eq("acc-1"), any(), any(), eq(pageable))).thenReturn(page);

        var result = service.transactions("acc-1", null, null, pageable);

        assertEquals(entity.getTransactionId(), result.getContent().get(0).transactionId());
        assertEquals("Shop", result.getContent().get(0).merchant());
        assertEquals(TransactionStatus.PROCESSED, result.getContent().get(0).status());
    }
}
