package com.amex.benefit_activation_engine.model;

/**
 * A reviewer's verdict on a claim that is under manual review.
 */
public enum ClaimDecision {

    /** Approve the claim for payout. */
    APPROVE,

    /** Decline the claim. */
    REJECT
}
