package com.amex.benefit_activation_engine.service;

import com.amex.benefit_activation_engine.model.ClaimStatus;

/**
 * Raised when a claim is asked to move to a status that is not a legal next
 * step from its current one (e.g. approving a PREFILLED claim, or deciding a
 * claim that is already PAID). Mapped to HTTP 409 Conflict by the web layer.
 */
public class IllegalClaimTransitionException extends RuntimeException {

    public IllegalClaimTransitionException(Long claimId, ClaimStatus from, ClaimStatus to) {
        super("Illegal claim transition for claim " + claimId + ": " + from + " -> " + to);
    }
}
