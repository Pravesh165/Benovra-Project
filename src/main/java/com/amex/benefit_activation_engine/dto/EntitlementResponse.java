package com.amex.benefit_activation_engine.dto;

import com.amex.benefit_activation_engine.model.Benefit;
import com.amex.benefit_activation_engine.model.BenefitType;
import com.amex.benefit_activation_engine.model.Entitlement;

import java.math.BigDecimal;

/**
 * API view of a card-product {@link Entitlement}: which benefit a card product
 * is entitled to claim, and the per-claim payout cap for that benefit.
 */
public record EntitlementResponse(
        Long id,
        String cardProduct,
        Long benefitId,
        BenefitType benefitType,
        String benefitName,
        BigDecimal perClaimLimit,
        boolean active
) {

    public static EntitlementResponse from(Entitlement entitlement) {
        Benefit benefit = entitlement.getBenefit();
        return new EntitlementResponse(
                entitlement.getId(),
                entitlement.getCardProduct(),
                benefit == null ? null : benefit.getId(),
                benefit == null ? null : benefit.getType(),
                benefit == null ? null : benefit.getName(),
                benefit == null ? null : benefit.getPerClaimLimit(),
                entitlement.isActive());
    }
}
