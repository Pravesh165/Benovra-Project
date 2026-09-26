package com.amex.benefit_activation_engine.dto;

import com.amex.benefit_activation_engine.model.ClaimDecision;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload for the mock reviewer decision endpoint
 * (POST /api/claims/{id}/decision).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClaimDecisionRequest {

    @NotNull(message = "decision is required (APPROVE or REJECT)")
    private ClaimDecision decision;

    /** Optional reviewer note recorded on the claim and in the audit log. */
    private String reason;
}
