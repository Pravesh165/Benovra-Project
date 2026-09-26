package com.amex.benefit_activation_engine.service;

/**
 * Thrown when a claim lookup fails. Mapped to HTTP 404 by the web layer.
 */
public class ClaimNotFoundException extends RuntimeException {

    public ClaimNotFoundException(Long id) {
        super("Claim not found: " + id);
    }
}
