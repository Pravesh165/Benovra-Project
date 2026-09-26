package com.amex.benefit_activation_engine.service;

/**
 * Raised when a claim is submitted but the card product no longer holds an
 * active entitlement for the claimed benefit (e.g. the entitlement was revoked
 * after the claim was pre-filled). Mapped to HTTP 409 Conflict.
 */
public class ClaimNotEntitledException extends RuntimeException {

    public ClaimNotEntitledException(Long claimId, String cardProduct, String benefitType) {
        super("Claim " + claimId + " cannot be submitted: card product " + cardProduct
                + " has no active entitlement for " + benefitType);
    }
}
