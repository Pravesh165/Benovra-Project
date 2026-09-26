package com.amex.benefit_activation_engine.dto;

/**
 * Response returned on successful login: the signed JWT, the subject it is
 * scoped to, the granted role, and how long (in minutes) it stays valid.
 */
public record LoginResponse(
        String token,
        String tokenType,
        String cardMemberId,
        String role,
        long expiresInMinutes
) {
    public static LoginResponse bearer(String token, String cardMemberId, String role, long expiresInMinutes) {
        return new LoginResponse(token, "Bearer", cardMemberId, role, expiresInMinutes);
    }
}
