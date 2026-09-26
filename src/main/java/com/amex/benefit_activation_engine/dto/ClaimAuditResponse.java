package com.amex.benefit_activation_engine.dto;

import com.amex.benefit_activation_engine.model.ClaimAuditEvent;
import com.amex.benefit_activation_engine.model.ClaimStatus;

import java.time.Instant;

/**
 * API view of a single {@link ClaimAuditEvent} in a claim's audit trail.
 */
public record ClaimAuditResponse(
        Long id,
        Long claimId,
        ClaimStatus fromStatus,
        ClaimStatus toStatus,
        String actor,
        String detail,
        Instant occurredAt
) {

    public static ClaimAuditResponse from(ClaimAuditEvent event) {
        return new ClaimAuditResponse(
                event.getId(),
                event.getClaimId(),
                event.getFromStatus(),
                event.getToStatus(),
                event.getActor(),
                event.getDetail(),
                event.getOccurredAt());
    }
}
