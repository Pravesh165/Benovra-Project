package com.amex.benefit_activation_engine.dto;

import com.amex.benefit_activation_engine.model.Benefit;
import com.amex.benefit_activation_engine.model.BenefitType;
import com.amex.benefit_activation_engine.model.Claim;
import com.amex.benefit_activation_engine.model.ClaimStatus;
import com.amex.benefit_activation_engine.model.Transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * API view of a pre-filled {@link Claim}. Flattens the linked transaction and
 * benefit into ids/labels and exposes the auto-populated field map so the
 * frontend can render a ready-to-review claim form.
 */
public record ClaimResponse(
        Long id,
        Long transactionId,
        Long benefitId,
        BenefitType benefitType,
        String benefitName,
        BigDecimal claimAmount,
        String currency,
        LocalDate coverageExpiresOn,
        Long coverageDaysRemaining,
        ClaimStatus status,
        Map<String, String> prefilledData,
        String decisionReason,
        String payoutReference,
        Instant createdAt,
        Instant submittedAt,
        Instant decidedAt
) {

    public static ClaimResponse from(Claim claim) {
        Transaction txn = claim.getTransaction();
        Benefit benefit = claim.getBenefit();

        return new ClaimResponse(
                claim.getId(),
                txn == null ? null : txn.getId(),
                benefit == null ? null : benefit.getId(),
                benefit == null ? null : benefit.getType(),
                benefit == null ? null : benefit.getName(),
                claim.getClaimAmount(),
                txn == null ? null : txn.getCurrency(),
                coverageExpiresOn(benefit, txn),
                coverageDaysRemaining(benefit, txn),
                claim.getStatus(),
                claim.getPrefilledData() == null
                        ? Map.of()
                        : new LinkedHashMap<>(claim.getPrefilledData()),
                claim.getDecisionReason(),
                claim.getPayoutReference(),
                claim.getCreatedAt(),
                claim.getSubmittedAt(),
                claim.getDecidedAt());
    }

        private static LocalDate coverageExpiresOn(Benefit benefit, Transaction transaction) {
                if (benefit == null || transaction == null || transaction.getPurchaseDate() == null
                                || benefit.getType() == BenefitType.TRAVEL_DELAY || benefit.getCoverageWindowDays() == null) {
                        return null;
                }
                return transaction.getPurchaseDate().plusDays(benefit.getCoverageWindowDays());
        }

        private static Long coverageDaysRemaining(Benefit benefit, Transaction transaction) {
                LocalDate expiresOn = coverageExpiresOn(benefit, transaction);
                return expiresOn == null ? null : ChronoUnit.DAYS.between(LocalDate.now(), expiresOn);
        }
}
