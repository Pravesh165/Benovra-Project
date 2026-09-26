package com.amex.benefit_activation_engine.model;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Lifecycle states of a claim as it moves through the submission and
 * approval workflow.
 *
 * <p>Typical happy path:
 * {@code PREFILLED -> SUBMITTED -> UNDER_REVIEW -> APPROVED -> PAID}.
 * A claim may also end in {@code REJECTED}.</p>
 *
 * <p>The legal transitions are declared here and enforced by the workflow
 * service, so an out-of-order change (e.g. paying a rejected claim) is rejected
 * rather than silently corrupting the claim's state.</p>
 */
public enum ClaimStatus {

    /** Auto-generated and pre-filled by the engine, not yet submitted by the card member. */
    PREFILLED,

    /** Submitted for processing. */
    SUBMITTED,

    /** Being assessed by the claims workflow. */
    UNDER_REVIEW,

    /** Approved for payout. */
    APPROVED,

    /** Declined; see the decision reason. */
    REJECTED,

    /** Approved and reimbursed to the card member. */
    PAID;

    /** Allowed next states for each status (empty set == terminal). */
    private static final Map<ClaimStatus, Set<ClaimStatus>> LEGAL_TRANSITIONS = Map.of(
            PREFILLED, EnumSet.of(SUBMITTED),
            SUBMITTED, EnumSet.of(UNDER_REVIEW, APPROVED, REJECTED),
            UNDER_REVIEW, EnumSet.of(APPROVED, REJECTED),
            APPROVED, EnumSet.of(PAID),
            REJECTED, EnumSet.noneOf(ClaimStatus.class),
            PAID, EnumSet.noneOf(ClaimStatus.class)
    );

    /** Whether a claim may legally move from this status to {@code target}. */
    public boolean canTransitionTo(ClaimStatus target) {
        return LEGAL_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    /** A status from which no further transition is possible. */
    public boolean isTerminal() {
        return LEGAL_TRANSITIONS.getOrDefault(this, Set.of()).isEmpty();
    }
}
