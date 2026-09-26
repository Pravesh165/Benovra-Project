package com.amex.benefit_activation_engine.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the {@code auth.jwt.*} configuration (signing secret + token lifetime)
 * used to mint and validate customer identity tokens.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "auth.jwt")
public class JwtProperties {

    /** HS256 signing secret; must be at least 32 bytes. */
    private String secret;

    /** Token lifetime, in minutes. */
    private long expiryMinutes = 120;
}
