package com.amex.benefit_activation_engine.dto;

import com.amex.benefit_activation_engine.model.ClaimStatus;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Task 5 metrics: how well the engine detects covered purchases and how much
 * previously-unclaimed benefit value it has helped recover.
 *
 * @param totalTransactions      purchases ingested so far
 * @param matchedTransactions    purchases matched to at least one benefit
 * @param detectionRatePct       matched / total transactions, as a percentage
 * @param totalClaims            claims generated (one per matched purchase)
 * @param claimsByStatus         claim counts broken down by workflow status
 * @param totalDetectableValue   total reimbursement value surfaced by the engine
 *                               (sum of all claim amounts)
 * @param claimedValue           value the member has acted on (claims past PREFILLED)
 * @param paidValue              value actually disbursed (PAID claims)
 * @param unclaimedValue         detectable value still sitting un-submitted
 * @param unclaimedReductionPct  claimedValue / detectableValue — the headline
 *                               "% reduction in unclaimed benefits"
 * @param currency               currency of the figures ("MIXED" if not uniform)
 */
public record MetricsResponse(
        long totalTransactions,
        long matchedTransactions,
        double detectionRatePct,
        long totalClaims,
        Map<ClaimStatus, Long> claimsByStatus,
        BigDecimal totalDetectableValue,
        BigDecimal claimedValue,
        BigDecimal paidValue,
        BigDecimal unclaimedValue,
        double unclaimedReductionPct,
        String currency
) {
}
