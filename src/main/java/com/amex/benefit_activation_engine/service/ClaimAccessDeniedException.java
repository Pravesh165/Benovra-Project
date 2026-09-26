package com.amex.benefit_activation_engine.service;

/**
 * Thrown when a card member tries to access or act on a claim that belongs to a
 * different card member. Mapped to HTTP 403 by the web layer.
 */
public class ClaimAccessDeniedException extends RuntimeException {

    public ClaimAccessDeniedException(Long claimId) {
        super("Claim " + claimId + " does not belong to the authenticated card member");
    }
}
