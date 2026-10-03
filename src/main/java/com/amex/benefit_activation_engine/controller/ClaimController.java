package com.amex.benefit_activation_engine.controller;

import com.amex.benefit_activation_engine.dto.ClaimAuditResponse;
import com.amex.benefit_activation_engine.dto.ClaimDecisionRequest;
import com.amex.benefit_activation_engine.dto.ClaimResponse;
import com.amex.benefit_activation_engine.model.Claim;
import com.amex.benefit_activation_engine.security.Roles;
import com.amex.benefit_activation_engine.service.ClaimService;
import com.amex.benefit_activation_engine.service.ClaimWorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * PRE-FILL + WORKFLOW API for claims: list/view pre-filled claims, submit them,
 * record reviewer decisions, and read the immutable audit trail.
 *
 * <p>Ownership enforcement: a CARD_MEMBER may only submit their own claims.
 * A REVIEWER may submit any claim (useful for corrective actions).</p>
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
            description = "Returns every claim the engine has generated, newest state included. "
                    + "Requires REVIEWER role (enforced by SecurityConfig).")
    public List<ClaimResponse> list() {
        return claimService.findAll().stream()
                .map(ClaimResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a single claim",
                        description = "Returns a claim to its owner or a reviewer (404 if unknown, 403 if not owned).")
        public ClaimResponse getById(@PathVariable Long id, Authentication authentication) {
                return ClaimResponse.from(getAccessibleClaim(id, authentication));
    }

    @PostMapping("/{id}/submit")
    @Operation(summary = "Submit a claim",
            description = "Moves a PREFILLED claim to SUBMITTED and applies the auto-decision "
                    + "rule: small claims are auto-approved and paid, larger ones go to review. "
                    + "CARD_MEMBERs may only submit their own claims (BOLA check).")
    public ClaimResponse submit(@PathVariable Long id, Authentication authentication) {
                getAccessibleClaim(id, authentication);
        return ClaimResponse.from(workflowService.submit(id));
    }

    @PostMapping("/{id}/decision")
    @Operation(summary = "Record a reviewer decision",
            description = "Approves or rejects a claim that is UNDER_REVIEW. Approval triggers "
                    + "disbursement via the bank client. Requires REVIEWER role.")
    public ClaimResponse decide(@PathVariable Long id,
                                @Valid @RequestBody ClaimDecisionRequest request) {
        return ClaimResponse.from(workflowService.decide(id, request.getDecision(), request.getReason()));
    }

    @GetMapping("/{id}/audit")
    @Operation(summary = "Get a claim's audit trail",
            description = "Returns the immutable, timestamped log of every state change for the "
                    + "claim. Requires REVIEWER role.")
    public List<ClaimAuditResponse> auditTrail(@PathVariable Long id) {
        return workflowService.getAuditTrail(id).stream()
                .map(ClaimAuditResponse::from)
                .toList();
    }

    // ── helpers ─────────────────────────────────────────────────────────────────

    private boolean isCardMember(Authentication auth) {
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + Roles.CARD_MEMBER));
    }

        private Claim getAccessibleClaim(Long claimId, Authentication authentication) {
                Claim claim = claimService.getById(claimId);
                if (authentication.getAuthorities().stream()
                                .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + Roles.REVIEWER))) {
                        return claim;
                }
                if (isCardMember(authentication)
                                && authentication.getName().equals(claim.getTransaction().getCardMemberId())) {
                        return claim;
                }
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You may only access your own claims.");
        }
}
