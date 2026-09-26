package com.amex.benefit_activation_engine.controller;

import com.amex.benefit_activation_engine.dto.ClaimResponse;
import com.amex.benefit_activation_engine.model.ClaimStatus;
import com.amex.benefit_activation_engine.service.ClaimService;
import com.amex.benefit_activation_engine.service.ClaimWorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Customer-facing, per-member claims API. Every endpoint is scoped to the
 * authenticated card member (the JWT subject), so a member only ever sees and
 * acts on their own claims.
 *
 * <ul>
 *   <li>{@code GET /api/me/claims?status=PREFILLED} — "Claims to make"
 *       (drop the filter, or use other statuses, for "Submitted claims").</li>
 *   <li>{@code POST /api/me/claims/{id}/submit} — submit one of the member's own
 *       pre-filled claims (403 if the claim belongs to someone else).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/me/claims")
@RequiredArgsConstructor
@Tag(name = "My Claims", description = "Per-card-member claims for the customer UI")
public class MeClaimController {

    private final ClaimService claimService;
    private final ClaimWorkflowService workflowService;

    @GetMapping
    @Operation(summary = "List my claims",
            description = "Returns the authenticated card member's claims, optionally filtered by "
                    + "status (e.g. PREFILLED for claims still to submit). Newest first.")
    public List<ClaimResponse> myClaims(@RequestParam(required = false) ClaimStatus status,
                                        Authentication authentication) {
        String cardMemberId = authentication.getName();
        return claimService.findForCardMember(cardMemberId, status).stream()
                .map(ClaimResponse::from)
                .toList();
    }

    @PostMapping("/{id}/submit")
    @Operation(summary = "Submit one of my claims",
            description = "Submits a PREFILLED claim owned by the authenticated card member. "
                    + "Returns 403 if the claim belongs to another member.")
    public ClaimResponse submit(@PathVariable Long id, Authentication authentication) {
        String cardMemberId = authentication.getName();
        claimService.getOwnedBy(id, cardMemberId); // 404 if unknown, 403 if not owned
        return ClaimResponse.from(workflowService.submit(id));
    }
}
