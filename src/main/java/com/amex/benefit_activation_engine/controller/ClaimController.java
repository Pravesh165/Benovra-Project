package com.amex.benefit_activation_engine.controller;

import com.amex.benefit_activation_engine.dto.ClaimAuditResponse;
import com.amex.benefit_activation_engine.dto.ClaimDecisionRequest;
import com.amex.benefit_activation_engine.dto.ClaimResponse;
import com.amex.benefit_activation_engine.model.Claim;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * PRE-FILL + WORKFLOW API for claims: list/view pre-filled claims, submit them,
 * record reviewer decisions, and read the immutable audit trail.
 */
@RestController
@RequestMapping("/api/claims")
@RequiredArgsConstructor
@Tag(name = "Claims", description = "Pre-filled benefit claims and approval workflow")
public class ClaimController {

    private final ClaimService claimService;
    private final ClaimWorkflowService workflowService;

    @GetMapping
    @Operation(summary = "List pre-filled claims",
            description = "Returns every claim the engine has generated, newest state included.")
    public List<ClaimResponse> list() {
        return claimService.findAll().stream()
                .map(ClaimResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a single claim",
            description = "Returns the pre-filled claim with the given id (404 if unknown).")
    public ClaimResponse getById(@PathVariable Long id) {
        Claim claim = claimService.getById(id); // 404 if unknown
        return ClaimResponse.from(claim);
    }

    @PostMapping("/{id}/submit")
    @Operation(summary = "Submit a claim",
            description = "Moves a PREFILLED claim to SUBMITTED and applies the auto-decision "
                    + "rule: small claims are auto-approved and paid, larger ones go to review.")
    public ClaimResponse submit(@PathVariable Long id) {
        return ClaimResponse.from(workflowService.submit(id));
    }

    @PostMapping("/{id}/decision")
    @Operation(summary = "Record a reviewer decision",
            description = "Approves or rejects a claim that is UNDER_REVIEW. Approval triggers "
                    + "disbursement via the bank client.")
    public ClaimResponse decide(@PathVariable Long id,
                                @Valid @RequestBody ClaimDecisionRequest request) {
        return ClaimResponse.from(workflowService.decide(id, request.getDecision(), request.getReason()));
    }

    @GetMapping("/{id}/audit")
    @Operation(summary = "Get a claim's audit trail",
            description = "Returns the immutable, timestamped log of every state change for the claim.")
    public List<ClaimAuditResponse> auditTrail(@PathVariable Long id) {
        return workflowService.getAuditTrail(id).stream()
                .map(ClaimAuditResponse::from)
                .toList();
    }
}
