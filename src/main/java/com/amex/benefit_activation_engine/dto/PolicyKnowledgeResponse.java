package com.amex.benefit_activation_engine.dto;

import com.amex.benefit_activation_engine.model.BenefitType;

import java.math.BigDecimal;
import java.util.List;

/**
 * Structured details of a benefit policy in the knowledge base.
 */
public record PolicyKnowledgeResponse(
        Long benefitId,
        BenefitType type,
        String name,
        String description,
        BigDecimal perClaimLimit,
        Integer coverageWindowDays,
        String coverageWindowExplanation,
        List<String> eligibleCategories,
        List<String> exclusions,
        List<String> requiredDocuments,
        List<String> eligibleCards,
        String userEligibilityExplanation
) {
}

