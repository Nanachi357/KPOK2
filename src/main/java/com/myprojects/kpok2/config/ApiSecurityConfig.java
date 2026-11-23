package com.myprojects.kpok2.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@Slf4j
@EnableConfigurationProperties({
        ApiSecurityProperties.class,
        ApiImportProperties.class
})
public class ApiSecurityConfig {
    private static final int MINIMUM_TOKEN_LENGTH = 32;
    private final ApiSecurityProperties properties;

    @Bean
    ApiTokenAuthenticationFilter apiTokenAuthenticationFilter() {
        if (!properties.isConfigured()) {
            log.warn("REST API is locked because KPOK2_API_TOKEN is not configured");
        }
        if (properties.isConfigured()
                && properties.getToken().length() < MINIMUM_TOKEN_LENGTH) {
            throw new IllegalStateException(
                    "KPOK2_API_TOKEN must contain at least "
                            + MINIMUM_TOKEN_LENGTH + " characters");
        }
        return new ApiTokenAuthenticationFilter(properties);
    }

    @Bean
    ApiImportPayloadLimitFilter apiImportPayloadLimitFilter(
            ApiImportProperties importProperties) {
        return new ApiImportPayloadLimitFilter(importProperties.getMaxBytes());
    }

    @Bean
    SecurityFilterChain apiSecurityFilterChain(
            HttpSecurity http,
            ApiTokenAuthenticationFilter apiTokenAuthenticationFilter,
            ApiImportPayloadLimitFilter apiImportPayloadLimitFilter) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .requestCache(cache -> cache.disable())
                .logout(logout -> logout.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/**").hasRole("API")
                        .anyRequest().denyAll())
                .addFilterBefore(
                        apiTokenAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(apiImportPayloadLimitFilter, AuthorizationFilter.class)
                .build();
    }
}
