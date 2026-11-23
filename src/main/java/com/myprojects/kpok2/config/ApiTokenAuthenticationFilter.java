package com.myprojects.kpok2.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

final class ApiTokenAuthenticationFilter extends OncePerRequestFilter {
    private static final String BEARER_PREFIX = "Bearer ";
    private static final List<SimpleGrantedAuthority> API_AUTHORITIES =
            List.of(new SimpleGrantedAuthority("ROLE_API"));

    private final ApiSecurityProperties properties;

    ApiTokenAuthenticationFilter(ApiSecurityProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (properties.isConfigured()
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String suppliedToken = readBearerToken(request);
            if (suppliedToken != null && tokensMatch(properties.getToken(), suppliedToken)) {
                var authentication = new UsernamePasswordAuthenticationToken(
                        "api-token", null, API_AUTHORITIES);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        filterChain.doFilter(request, response);
    }

    private static String readBearerToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null
                || !authorization.regionMatches(true, 0, BEARER_PREFIX, 0,
                        BEARER_PREFIX.length())) {
            return null;
        }
        String token = authorization.substring(BEARER_PREFIX.length());
        return token.isBlank() ? null : token;
    }

    private static boolean tokensMatch(String expected, String supplied) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8));
    }
}
