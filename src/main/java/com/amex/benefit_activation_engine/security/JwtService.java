package com.amex.benefit_activation_engine.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;

/**
 * Signs and validates the identity tokens (HS256). The token subject is the
 * card member id (or {@code "admin"} for a reviewer) and a {@code role} claim
 * carries the authority the {@link JwtAuthFilter} grants.
 */
@Slf4j
@Service
public class JwtService {

    /** The parsed identity carried by a valid token. */
    public record TokenPrincipal(String subject, String role) {
    }

    private static final String ROLE_CLAIM = "role";

    private final SecretKey key;
    private final long expiryMinutes;

    public JwtService(JwtProperties properties) {
        byte[] secretBytes = properties.getSecret().getBytes(StandardCharsets.UTF_8);
        this.key = Keys.hmacShaKeyFor(secretBytes);
        this.expiryMinutes = properties.getExpiryMinutes();
    }

    /** Issues a token for a card member (role {@link Roles#CARD_MEMBER}). */
    public String issueToken(String subject) {
        return issueToken(subject, Roles.CARD_MEMBER);
    }

    /** Issues a signed token with the given subject and role. */
    public String issueToken(String subject, String role) {
        Instant now = Instant.now();
        Instant expiry = now.plus(expiryMinutes, ChronoUnit.MINUTES);
        return Jwts.builder()
                .subject(subject)
                .claim(ROLE_CLAIM, role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }

    /** Minutes until an issued token expires (surfaced to the UI). */
    public long getExpiryMinutes() {
        return expiryMinutes;
    }

    /**
     * Validates the token's signature and expiry and returns its principal
     * (subject + role), or empty if the token is missing/invalid/expired.
     * A token without a role claim defaults to {@link Roles#CARD_MEMBER}.
     */
    public Optional<TokenPrincipal> parse(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            String subject = claims.getSubject();
            if (subject == null) {
                return Optional.empty();
            }
            String role = claims.get(ROLE_CLAIM, String.class);
            return Optional.of(new TokenPrincipal(subject, role == null ? Roles.CARD_MEMBER : role));
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Rejected JWT: {}", ex.getMessage());
            return Optional.empty();
        }
    }
}
