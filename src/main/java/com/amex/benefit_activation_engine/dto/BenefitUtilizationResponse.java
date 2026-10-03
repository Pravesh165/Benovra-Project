package com.amex.benefit_activation_engine.dto;

import java.math.BigDecimal;

public record BenefitUtilizationResponse(
        long eligibleClaims,
        long claimedClaims,
        long paidClaims,
        double utilizationPct,
        BigDecimal claimableValue,
        BigDecimal claimedValue,
        BigDecimal paidValue
) {
}