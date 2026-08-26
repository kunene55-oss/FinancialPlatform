package com.example.financial.aggregation.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AccountAccessGuardTest {

    private final AccountAccessGuard guard = new AccountAccessGuard();

    @Test
    void owns_trueWhenAccountIdInClaim() {
        var auth = ownerToken(List.of("acc-1", "acc-2"));

        assertTrue(guard.owns(auth, "acc-1"));
    }

    @Test
    void owns_falseWhenAccountIdNotInClaim() {
        var auth = ownerToken(List.of("acc-2"));

        assertFalse(guard.owns(auth, "acc-1"));
    }

    @Test
    void owns_falseForNonJwtAuthentication() {
        var auth = new UsernamePasswordAuthenticationToken("user", "pw");

        assertFalse(guard.owns(auth, "acc-1"));
    }

    @Test
    void restrictToOwned_staffRoleSeesEverythingRequested() {
        var auth = staffToken("ROLE_account-read");

        var result = guard.restrictToOwned(auth, List.of("acc-1", "acc-2"));

        assertEquals(List.of("acc-1", "acc-2"), result);
    }

    @Test
    void restrictToOwned_adminRoleSeesEverythingRequested() {
        var auth = staffToken("ROLE_account-admin");

        var result = guard.restrictToOwned(auth, List.of("acc-1", "acc-2"));

        assertEquals(List.of("acc-1", "acc-2"), result);
    }

    @Test
    void restrictToOwned_ownerFilteredToOwnedAccountsOnly() {
        var auth = ownerToken(List.of("acc-1"));

        var result = guard.restrictToOwned(auth, List.of("acc-1", "acc-2"));

        assertEquals(List.of("acc-1"), result);
    }

    @Test
    void restrictToOwned_ownerWithNoMatchesReturnsEmpty() {
        var auth = ownerToken(List.of("acc-9"));

        var result = guard.restrictToOwned(auth, List.of("acc-1", "acc-2"));

        assertTrue(result.isEmpty());
    }

    private JwtAuthenticationToken ownerToken(List<String> accountIds) {
        return jwtToken(accountIds, "ROLE_account-owner");
    }

    private JwtAuthenticationToken staffToken(String authority) {
        return jwtToken(List.of(), authority);
    }

    private JwtAuthenticationToken jwtToken(List<String> accountIds, String authority) {
        Jwt jwt = new Jwt(
            "token-value",
            Instant.now(),
            Instant.now().plusSeconds(60),
            Map.of("alg", "none"),
            Map.of("account_ids", accountIds));
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(authority));
        return new JwtAuthenticationToken(jwt, authorities);
    }
}
