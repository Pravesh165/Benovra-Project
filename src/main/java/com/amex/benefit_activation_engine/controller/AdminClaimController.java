package com.amex.benefit_activation_engine.controller;

import com.amex.benefit_activation_engine.dto.ClaimDecisionRequest;
import com.amex.benefit_activation_engine.dto.ReviewerClaimResponse;
import com.amex.benefit_activation_engine.model.BenefitType;
import com.amex.benefit_activation_engine.model.ClaimStatus;
import com.amex.benefit_activation_engine.service.ClaimService;
import com.amex.benefit_activation_engine.service.ClaimWorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Reviewer-only claims API (requires {@code ROLE_REVIEWER}). Lists claims across
 * all customers with optional filters, and records approve/reject decisions.
 */
@RestController
@RequestMapping("/api/admin/claims")
@RequiredArgsConstructor
@Tag(name = "Admin Claims", description = "Reviewer queue, search, and decisions")
public class AdminClaimController {

    private final ClaimService claimService;
    private final ClaimWorkflowService workflowService;

    @GetMapping
    @Operation(summary = "List / search claims",
            description = "Returns claims across all customers. All filters are optional: status, "
                    + "cardMemberId (partial match), cardProduct, merchantCategory, benefitType. "
                    + "Use status=UNDER_REVIEW for the review queue.")
    public List<ReviewerClaimResponse> list(
            @RequestParam(required = false) ClaimStatus status,
            @RequestParam(required = false) String cardMemberId,
            @RequestParam(required = false) String cardProduct,
            @RequestParam(required = false) String merchantCategory,
            @RequestParam(required = false) BenefitType benefitType) {
        return claimService.findForReviewer(status, cardMemberId, cardProduct, merchantCategory, benefitType)
                .stream()
                .map(ReviewerClaimResponse::from)
                .toList();
    }

    @PostMapping("/{id}/decision")
    @Operation(summary = "Approve or reject a claim",
            description = "Records a reviewer decision on a claim under review. APPROVE moves it to "
                    + "APPROVED and disburses; REJECT moves it to REJECTED.")
    public ReviewerClaimResponse decide(@PathVariable Long id,
                                        @Valid @RequestBody ClaimDecisionRequest request) {
        return ReviewerClaimResponse.from(
                workflowService.decide(id, request.getDecision(), request.getReason()));
    }
}
