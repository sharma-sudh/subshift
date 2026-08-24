package com.subshift.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * TEMPORARY. Adding spring-boot-starter-security locks every endpoint behind
 * a default login form unless we explicitly configure it. This permits
 * everything for now, purely so /api/health and early CRUD endpoints are
 * reachable during initial setup.
 *
 * This gets REPLACED in the JWT auth task (Review 1, Sudhanshu Task 6) with
 * real rules: public endpoints (login/register) open, everything else
 * requiring a valid JWT, and role-based rules layered on top later for RBAC.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
