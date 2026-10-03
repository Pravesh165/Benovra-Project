package com.amex.benefit_activation_engine.dto;

import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Request payload for updating editable details of a PREFILLED claim before submission.
 */
public record UpdateClaimDraftRequest(
        @Size(max = 255, message = "Item description must not exceed 255 characters")
        String itemDescription,

        @Size(max = 50, message = "Incident date must not exceed 50 characters")
        String incidentDate,

        @Size(max = 1000, message = "Incident explanation must not exceed 1000 characters")
        String incidentExplanation,

        Map<String, String> additionalFields
) {
}
