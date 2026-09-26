package com.amex.benefit_activation_engine.service;

import com.amex.benefit_activation_engine.config.WorkflowProperties;
import com.amex.benefit_activation_engine.integration.bank.BankClient;
import com.amex.benefit_activation_engine.integration.bank.DisbursementResult;
import com.amex.benefit_activation_engine.model.Claim;
import com.amex.benefit_activation_engine.model.ClaimAuditEvent;
import com.amex.benefit_activation_engine.model.ClaimDecision;
import com.amex.benefit_activation_engine.model.ClaimStatus;
import com.amex.benefit_activation_engine.repository.ClaimAuditRepository;
import com.amex.benefit_activation_engine.repository.ClaimRepository;
import com.amex.benefit_activation_engine.repository.EntitlementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;

/**
 * WORKFLOW stage (Task 4): drives a pre-filled claim through the submission and
 * approval state machine.
 *
 * <p>Every state change is:</p>
 * <ul>
 *   <li><b>validated</b> against the legal transitions declared on
 *       {@link ClaimStatus} (illegal moves are rejected), and</li>
 *   <li><b>recorded</b> as an immutable {@link ClaimAuditEvent}.</li>
 * </ul>
 *
 * <p>Flow: {@code submit} moves PREFILLED → SUBMITTED, then the auto-decision
 * rule either auto-approves small claims (→ APPROVED → PAID via the
 * {@link BankClient}) or routes larger ones to manual review (→ UNDER_REVIEW).
 * A reviewer then calls {@code decide} to APPROVE (→ APPROVED → PAID) or
 * REJECT (→ REJECTED).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClaimWorkflowService {

    private static final String ACTOR_SYSTEM = "SYSTEM";
    private static final String ACTOR_REVIEWER = "REVIEWER";
    private static final String ACTOR_BANK = "BANK";

    private final ClaimRepository claimRepository;
    private final ClaimAuditRepository auditRepository;
    private final EntitlementRepository entitlementRepository;
    private final BankClient bankClient;
    private final WorkflowProperties workflowProperties;

    /**
     * Submits a PREFILLED claim (→ SUBMITTED), then applies the auto-decision
     * rule. Re-checks the card's entitlement first so a revoked benefit can't be
     * claimed.
     */
    @Transactional
    public Claim submit(Long claimId) {
        Claim claim = load(claimId);
        assertEntitled(claim);

        transition(claim, ClaimStatus.SUBMITTED, ACTOR_SYSTEM, "Claim submitted by card member");

        boolean autoApprove = claim.getClaimAmount().compareTo(workflowProperties.getAutoApproveThreshold()) <= 0;
        if (autoApprove) {
            claim.setDecisionReason("Auto-approved: amount within auto-approve threshold ("
                    + workflowProperties.getAutoApproveThreshold() + ")");
            transition(claim, ClaimStatus.APPROVED, ACTOR_SYSTEM, claim.getDecisionReason());
            payout(claim);
        } else {
            transition(claim, ClaimStatus.UNDER_REVIEW, ACTOR_SYSTEM,
                    "Amount exceeds auto-approve threshold (" + workflowProperties.getAutoApproveThreshold()
                            + "); routed to manual review");
        }
        return claimRepository.save(claim);
    }

    /**
     * Records a reviewer's decision on a claim that is UNDER_REVIEW. APPROVE
     * moves it to APPROVED and disburses; REJECT moves it to REJECTED.
     */
    @Transactional
    public Claim decide(Long claimId, ClaimDecision decision, String reason) {
        Claim claim = load(claimId);
        String note = StringUtils.hasText(reason) ? reason.trim() : "(no reason provided)";
        claim.setDecisionReason(note);

        if (decision == ClaimDecision.APPROVE) {
            transition(claim, ClaimStatus.APPROVED, ACTOR_REVIEWER, "Reviewer approved: " + note);
            payout(claim);
        } else {
            transition(claim, ClaimStatus.REJECTED, ACTOR_REVIEWER, "Reviewer rejected: " + note);
        }
        return claimRepository.save(claim);
    }

    /** Returns the full immutable audit trail for a claim (oldest first). */
    @Transactional(readOnly = true)
    public List<ClaimAuditEvent> getAuditTrail(Long claimId) {
        load(claimId); // 404 if the claim doesn't exist
        return auditRepository.findByClaimIdOrderByOccurredAtAscIdAsc(claimId);
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private Claim load(Long claimId) {
        return claimRepository.findById(claimId)
                .orElseThrow(() -> new ClaimNotFoundException(claimId));
    }

    /**
     * Verifies the claim's card product still holds an active entitlement for
     * the claimed benefit — the functional link between claims and entitlements.
     */
    private void assertEntitled(Claim claim) {
        String cardProduct = claim.getTransaction().getCardProduct();
        Long benefitId = claim.getBenefit().getId();
        boolean entitled = entitlementRepository.findByCardProductAndActiveTrue(cardProduct).stream()
                .anyMatch(e -> e.getBenefit() != null && benefitId.equals(e.getBenefit().getId()));
        if (!entitled) {
            throw new ClaimNotEntitledException(claim.getId(), cardProduct, claim.getBenefit().getType().name());
        }
    }

    /**
     * Performs a single guarded state change: enforces the legal transition,
     * updates timestamps, and appends an immutable audit event.
     */
    private void transition(Claim claim, ClaimStatus target, String actor, String detail) {
        ClaimStatus from = claim.getStatus();
        if (!from.canTransitionTo(target)) {
            throw new IllegalClaimTransitionException(claim.getId(), from, target);
        }

        claim.setStatus(target);
        Instant now = Instant.now();
        switch (target) {
            case SUBMITTED -> claim.setSubmittedAt(now);
            case APPROVED, REJECTED -> claim.setDecidedAt(now);
            default -> { /* no timestamp field for UNDER_REVIEW / PAID */ }
        }

        auditRepository.save(ClaimAuditEvent.builder()
                .claimId(claim.getId())
                .fromStatus(from)
                .toStatus(target)
                .actor(actor)
                .detail(detail)
                .build());

        log.info("Claim {} transition {} -> {} by {} ({})",
                claim.getId(), from, target, actor, detail);
    }

    /** Disburses an approved claim via the bank client and moves it to PAID. */
    private void payout(Claim claim) {
        DisbursementResult result = bankClient.disburse(claim);
        if (!result.success()) {
            log.warn("Disbursement declined for claim {}: {}", claim.getId(), result.message());
            return; // stays APPROVED; a real system would retry / escalate
        }
        claim.setPayoutReference(result.reference());
        transition(claim, ClaimStatus.PAID, ACTOR_BANK,
                "Reimbursement disbursed (" + result.amount() + ", ref " + result.reference() + ")");
    }
}
