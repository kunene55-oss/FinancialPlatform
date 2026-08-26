package com.example.financial.aggregation.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component("accountAccess")
public class AccountAccessGuard {

    public boolean owns(Authentication authentication, String accountId) {
        return ownedAccountIds(authentication).contains(accountId);
    }

    public List<String> restrictToOwned(Authentication authentication, List<String> requestedAccountIds) {
        if (hasStaffAccess(authentication)) {
            return requestedAccountIds;
        }
        Set<String> owned = Set.copyOf(ownedAccountIds(authentication));
        return requestedAccountIds.stream().filter(owned::contains).toList();
    }

    private boolean hasStaffAccess(Authentication authentication) {
        return authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch(a -> a.equals("ROLE_account-admin") || a.equals("ROLE_account-read"));
    }

    private List<String> ownedAccountIds(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
            return List.of();
        }
        List<String> ids = jwtAuth.getToken().getClaimAsStringList("account_ids");
        return ids == null ? List.of() : ids;
    }
}
