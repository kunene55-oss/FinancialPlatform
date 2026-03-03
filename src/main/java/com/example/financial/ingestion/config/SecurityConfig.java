package com.example.financial.ingestion.config;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.context.annotation.*;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(auth -> 
            auth.requestMatchers("/actuator/**")
            .permitAll()
            .anyRequest()
            .authenticated()
        ).oauth2ResourceServer(oauth -> oauth.jwt());

        return http.build();
    }
}
