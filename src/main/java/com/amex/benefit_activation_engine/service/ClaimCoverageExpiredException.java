package com.amex.benefit_activation_engine.service;

import java.time.LocalDate;

public class ClaimCoverageExpiredException extends RuntimeException {

    public ClaimCoverageExpiredException(Long claimId, LocalDate expiresOn) {
        super("Claim " + claimId + " coverage expired on " + expiresOn + ".");
    }
}
