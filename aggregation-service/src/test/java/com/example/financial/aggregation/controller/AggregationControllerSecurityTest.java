package com.example.financial.aggregation.controller;

import com.example.financial.aggregation.security.AccountAccessGuard;
import com.example.financial.aggregation.service.AggregationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Exercises the {@code @PreAuthorize} rules on AggregationController end-to-end
 * (SpEL + AccountAccessGuard) without needing HTTP or JWT decoding.
 */
@SpringJUnitConfig
@ContextConfiguration(classes = {
    AggregationController.class,
    AccountAccessGuard.class,
    AggregationControllerSecurityTest.MethodSecurityConfig.class
})
class AggregationControllerSecurityTest {

    @Configuration
    @EnableMethodSecurity
    static class MethodSecurityConfig {
        @Bean
        AggregationService aggregationService() {
            return Mockito.mock(AggregationService.class);
        }
    }

    @Autowired
    private AggregationController controller;

    @Autowired
    private AggregationService service;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void summary_deniedForCallerWithNoRelevantRole() {
        authenticateAs(List.of());

        assertThrows(AccessDeniedException.class, () -> controller.summary("acc-1", null, null));
    }

    @Test
    void summary_allowedForStaffRole() {
        authenticateAs(List.of("ROLE_account-read"));
        when(service.summary(any(), any(), any())).thenReturn(Map.of());

        assertDoesNotThrow(() -> controller.summary("acc-1", null, null));
    }

    @Test
    void summary_allowedForOwnerOfMatchingAccount() {
        authenticateAsOwner(List.of("acc-1"));
        when(service.summary(any(), any(), any())).thenReturn(Map.of());

        assertDoesNotThrow(() -> controller.summary("acc-1", null, null));
    }

    @Test
    void summary_deniedForOwnerOfDifferentAccount() {
        authenticateAsOwner(List.of("acc-2"));

        assertThrows(AccessDeniedException.class, () -> controller.summary("acc-1", null, null));
    }

    @Test
    void accountTotals_ownerRequestFilteredToOwnedAccountsOnly() {
        authenticateAsOwner(List.of("acc-1"));
        when(service.accountTotals(List.of("acc-1"), null, null)).thenReturn(List.of());

        assertDoesNotThrow(() ->
            controller.accountTotals(List.of("acc-1", "acc-2"), null, null,
                SecurityContextHolder.getContext().getAuthentication()));
    }

    @Test
    void accountTotals_ownerWithNoMatchingAccountsIsDenied() {
        authenticateAsOwner(List.of("acc-9"));

        assertThrows(AccessDeniedException.class, () ->
            controller.accountTotals(List.of("acc-1", "acc-2"), null, null,
                SecurityContextHolder.getContext().getAuthentication()));
    }

    private void authenticateAs(List<String> authorities) {
        setAuthentication(List.of(), authorities);
    }

    private void authenticateAsOwner(List<String> accountIds) {
        setAuthentication(accountIds, List.of("ROLE_account-owner"));
    }

    private void setAuthentication(List<String> accountIds, List<String> authorities) {
        Jwt jwt = new Jwt(
            "token-value",
            Instant.now(),
            Instant.now().plusSeconds(60),
            Map.of("alg", "none"),
            Map.of("account_ids", accountIds));
        List<GrantedAuthority> granted = authorities.stream()
            .<GrantedAuthority>map(SimpleGrantedAuthority::new)
            .toList();
        var auth = new JwtAuthenticationToken(jwt, granted);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
