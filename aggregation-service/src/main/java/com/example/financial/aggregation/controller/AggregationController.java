package com.example.financial.aggregation.controller;

import com.example.financial.aggregation.dto.*;
import com.example.financial.aggregation.security.AccountAccessGuard;
import com.example.financial.aggregation.service.AggregationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/aggregations")
public class AggregationController {

    // Staff roles (service tokens) may access any account.
    // account-owner (customer-portal user tokens) is additionally checked against
    // the caller's own account_ids claim via AccountAccessGuard.
    private static final String STAFF_ACCESS = "hasRole('account-read') or hasRole('account-admin')";
    private static final String READ_ACCESS =
        STAFF_ACCESS + " or (hasRole('account-owner') and @accountAccess.owns(authentication, #accountId))";
    private static final String READ_ACCESS_ANY_OWNER =
        STAFF_ACCESS + " or hasRole('account-owner')";

    private final AggregationService service;
    private final AccountAccessGuard accountAccess;

    @Operation(summary = "Sum of processed transactions by category for an account, optionally within a date range")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Category totals",
            content = @Content(schema = @Schema(implementation = Map.class))),
        @ApiResponse(responseCode = "400", description = "'from' is after 'to'",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content),
        @ApiResponse(responseCode = "403", description = "Caller lacks access to this account", content = @Content)
    })
    @GetMapping("/{accountId}/summary")
    @PreAuthorize(READ_ACCESS)
    public Map<String, BigDecimal> summary(
        @PathVariable String accountId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return service.summary(accountId, from, to);
    }

    @Operation(summary = "Spend/deposit trend for an account bucketed by day, week or month")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Trend buckets",
            content = @Content(schema = @Schema(implementation = TrendPoint.class))),
        @ApiResponse(responseCode = "400", description = "Invalid interval or 'from' after 'to'",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content),
        @ApiResponse(responseCode = "403", description = "Caller lacks access to this account", content = @Content)
    })
    @GetMapping("/{accountId}/trend")
    @PreAuthorize(READ_ACCESS)
    public List<TrendPoint> trend(
        @PathVariable String accountId,
        @RequestParam(defaultValue = "DAILY") TrendInterval interval,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return service.trend(accountId, interval, from, to);
    }

    @Operation(summary = "Income vs expense totals for an account, optionally within a date range")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Income, expense and net totals",
            content = @Content(schema = @Schema(implementation = TotalsResponse.class))),
        @ApiResponse(responseCode = "400", description = "'from' is after 'to'",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content),
        @ApiResponse(responseCode = "403", description = "Caller lacks access to this account", content = @Content)
    })
    @GetMapping("/{accountId}/totals")
    @PreAuthorize(READ_ACCESS)
    public TotalsResponse totals(
        @PathVariable String accountId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return service.totals(accountId, from, to);
    }

    @Operation(summary = "Spend by merchant for an account, paginated and sortable")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Page of merchant totals",
            content = @Content(schema = @Schema(implementation = MerchantSummary.class))),
        @ApiResponse(responseCode = "400", description = "'from' is after 'to'",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content),
        @ApiResponse(responseCode = "403", description = "Caller lacks access to this account", content = @Content)
    })
    @GetMapping("/{accountId}/merchants")
    @PreAuthorize(READ_ACCESS)
    public Page<MerchantSummary> merchants(
        @PathVariable String accountId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
        @Parameter(hidden = true) @PageableDefault(size = 20) Pageable pageable) {
        return service.merchants(accountId, from, to, pageable);
    }

    @Operation(summary = "Transaction counts and totals by status for an account (includes pending/failed)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Status breakdown",
            content = @Content(schema = @Schema(implementation = StatusSummary.class))),
        @ApiResponse(responseCode = "400", description = "'from' is after 'to'",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content),
        @ApiResponse(responseCode = "403", description = "Caller lacks access to this account", content = @Content)
    })
    @GetMapping("/{accountId}/status-summary")
    @PreAuthorize(READ_ACCESS)
    public List<StatusSummary> statusSummary(
        @PathVariable String accountId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return service.statusSummary(accountId, from, to);
    }

    @Operation(summary = "Paginated transaction ledger for an account, most recent first")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Page of transactions",
            content = @Content(schema = @Schema(implementation = TransactionView.class))),
        @ApiResponse(responseCode = "400", description = "'from' is after 'to'",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content),
        @ApiResponse(responseCode = "403", description = "Caller lacks access to this account", content = @Content)
    })
    @GetMapping("/{accountId}/transactions")
    @PreAuthorize(READ_ACCESS)
    public Page<TransactionView> transactions(
        @PathVariable String accountId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
        @Parameter(hidden = true) @PageableDefault(size = 20) Pageable pageable) {
        return service.transactions(accountId, from, to, pageable);
    }

    @Operation(summary = "Processed totals across multiple accounts")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Per-account totals",
            content = @Content(schema = @Schema(implementation = AccountTotal.class))),
        @ApiResponse(responseCode = "400", description = "accountIds missing/empty or 'from' after 'to'",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content),
        @ApiResponse(responseCode = "403", description = "Caller (account-owner) owns none of the requested accounts", content = @Content)
    })
    @GetMapping("/summary")
    @PreAuthorize(READ_ACCESS_ANY_OWNER)
    public List<AccountTotal> accountTotals(
        @RequestParam List<String> accountIds,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
        Authentication authentication) {
        List<String> allowed = accountAccess.restrictToOwned(authentication, accountIds);
        if (allowed.isEmpty() && !accountIds.isEmpty()) {
            throw new AccessDeniedException("Caller owns none of the requested accounts");
        }
        return service.accountTotals(allowed, from, to);
    }
}
