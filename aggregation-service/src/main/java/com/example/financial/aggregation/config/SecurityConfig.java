package com.example.financial.aggregation.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Profile("!local")
public class SecurityConfig {

    // aggregation-service: aggregation-service's own service account.
    // processing-service: staff/service-to-service caller, granted account-read/account-admin
    // under aggregation-service's own client-role namespace in Keycloak.
    // customer-portal: end-user tokens carrying an account_ids claim for self-service access.
    private static final Set<String> EXPECTED_CLIENT_IDS =
        Set.of("aggregation-service", "processing-service", "customer-portal");

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
        .sessionManagement(session -> {
            session.sessionCreationPolicy(SessionCreationPolicy.STATELESS);})
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/actuator/health/**").permitAll()
            .requestMatchers("/actuator/**")
            .authenticated()
            .anyRequest()
            .authenticated() )
        .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt
            .decoder(jwtDecoder())
            .jwtAuthenticationConverter(jwtAuthenticationConverter())));

        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(this::clientRoleAuthorities);
        return converter;
    }

    private Collection<GrantedAuthority> clientRoleAuthorities(Jwt jwt) {
        Map<String, Object> resourceAccess = jwt.getClaimAsMap("resource_access");
        if (resourceAccess == null) {
            return List.of();
        }
        // Roles relevant to this service can live under different Keycloak client
        // namespaces depending on the caller: aggregation-service's own namespace holds
        // staff roles (account-read/account-admin), while customer-portal's namespace
        // holds account-owner. Merge across all buckets rather than keying off azp alone,
        // since resource_access is populated by Keycloak from the token subject's actual
        // granted roles and isn't attacker-controlled.
        List<GrantedAuthority> authorities = new ArrayList<>();
        for (Object clientAccess : resourceAccess.values()) {
            if (!(clientAccess instanceof Map<?, ?> clientAccessMap)) {
                continue;
            }
            if (!(clientAccessMap.get("roles") instanceof Collection<?> roleNames)) {
                continue;
            }
            roleNames.forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
        }
        return authorities;
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(issuerUri).build();

        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuerUri);
        OAuth2TokenValidator<Jwt> withExpectedClient = token -> {
            if (EXPECTED_CLIENT_IDS.contains(token.getClaimAsString("azp"))) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", "Token was not issued for an expected client", null));
        };

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(withIssuer, withExpectedClient));
        return decoder;
    }
}
