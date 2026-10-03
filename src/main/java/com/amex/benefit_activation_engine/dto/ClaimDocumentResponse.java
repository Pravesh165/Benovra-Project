package com.amex.benefit_activation_engine.dto;

import com.amex.benefit_activation_engine.model.ClaimDocument;
import com.amex.benefit_activation_engine.model.ClaimDocumentType;

import java.time.Instant;

public record ClaimDocumentResponse(
        Long id,
        Long claimId,
        ClaimDocumentType documentType,
        String originalFilename,
        String contentType,
        long sizeBytes,
        Instant uploadedAt,
        boolean required
) {
    public static ClaimDocumentResponse from(ClaimDocument document) {
        return new ClaimDocumentResponse(
                document.getId(),
                document.getClaim().getId(),
                document.getDocumentType(),
                document.getOriginalFilename(),
                document.getContentType(),
                document.getSizeBytes(),
                document.getUploadedAt(),
                document.isRequired()
        );
    }
}
