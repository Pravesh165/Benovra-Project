package com.amex.benefit_activation_engine.security;

/**
 * Role names carried in the JWT {@code role} claim and mapped to Spring
 * authorities ({@code ROLE_<name>}) by {@link JwtAuthFilter}.
 */
public final class Roles {

    /** A card member using the customer UI ({@code /api/me/**}). */
    public static final String CARD_MEMBER = "CARD_MEMBER";

    /** A claims reviewer using the admin UI ({@code /api/admin/**}). */
    public static final String REVIEWER = "REVIEWER";

    private Roles() {
    }
}
