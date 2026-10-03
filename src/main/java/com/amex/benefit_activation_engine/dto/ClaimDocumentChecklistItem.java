package com.amex.benefit_activation_engine.dto;

import com.amex.benefit_activation_engine.model.ClaimDocumentType;

public record ClaimDocumentChecklistItem(
        ClaimDocumentType documentType,
        boolean required,
        boolean uploaded
) {
}