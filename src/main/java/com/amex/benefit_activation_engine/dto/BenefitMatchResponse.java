package com.amex.benefit_activation_engine.dto;

import com.amex.benefit_activation_engine.model.Benefit;
import com.amex.benefit_activation_engine.model.BenefitType;
import com.amex.benefit_activation_engine.model.Transaction;

import java.math.BigDecimal;

/**
 * A single matched benefit for a transaction, including the estimated
 * recoverable amount and a short human-readable reason.
 */
public record BenefitMatchResponse(
        Long benefitId,
        BenefitType type,
        String name,
        String description,
        BigDecimal perClaimLimit,
        BigDecimal estimatedClaimAmount,
        String reason
) {

    public static BenefitMatchResponse of(Benefit benefit, Transaction transaction) {
        BigDecimal estimated = benefit.getPerClaimLimit() == null
                ? transaction.getAmount()
                : transaction.getAmount().min(benefit.getPerClaimLimit());

        return new BenefitMatchResponse(
                benefit.getId(),
                benefit.getType(),
                benefit.getName(),
                benefit.getDescription(),
                benefit.getPerClaimLimit(),
                estimated,
                reasonFor(benefit.getType(), transaction));
    }

    private static String reasonFor(BenefitType type, Transaction txn) {
        return switch (type) {
            case PURCHASE_PROTECTION -> "Eligible " + txn.getMerchantCategory()
                    + " purchase within the coverage window (reimbursed up to the per-claim limit)";
            case RETURN_PROTECTION -> "Retail purchase covered by return protection within the return window";
            case TRAVEL_DELAY -> "Travel purchase covered by travel-delay insurance";
        };
    }
}
