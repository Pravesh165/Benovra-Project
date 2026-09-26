package com.amex.benefit_activation_engine.dto;

import com.amex.benefit_activation_engine.model.Benefit;
import com.amex.benefit_activation_engine.model.BenefitType;
import com.amex.benefit_activation_engine.model.Claim;
import com.amex.benefit_activation_engine.model.ClaimStatus;
import com.amex.benefit_activation_engine.model.Transaction;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Reviewer-facing view of a claim. Unlike the customer {@link ClaimResponse},
 * this flattens the linked transaction fields (card member, card product,
 * merchant, category) so the admin UI can list, search, and act on claims
 * across all customers.
 */
public record ReviewerClaimResponse(
        Long id,
        ClaimStatus status,
        String cardMemberId,
        String cardProduct,
        String merchantName,
        String merchantCategory,
        BenefitType benefitType,
        String benefitName,
        BigDecimal purchaseAmount,
        BigDecimal claimAmount,
        String currency,
        String decisionReason,
        String payoutReference,
        Instant createdAt,
        Instant submittedAt,
        Instant decidedAt
) {

    public static ReviewerClaimResponse from(Claim claim) {
        Transaction txn = claim.getTransaction();
        Benefit benefit = claim.getBenefit();

        return new ReviewerClaimResponse(
                claim.getId(),
                claim.getStatus(),
                txn == null ? null : txn.getCardMemberId(),
                txn == null ? null : txn.getCardProduct(),
                txn == null ? null : txn.getMerchantName(),
                txn == null ? null : txn.getMerchantCategory(),
                benefit == null ? null : benefit.getType(),
                benefit == null ? null : benefit.getName(),
                txn == null ? null : txn.getAmount(),
                claim.getClaimAmount(),
                txn == null ? null : txn.getCurrency(),
                claim.getDecisionReason(),
                claim.getPayoutReference(),
                claim.getCreatedAt(),
                claim.getSubmittedAt(),
                claim.getDecidedAt());
    }
}
