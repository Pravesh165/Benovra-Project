package com.amex.benefit_activation_engine.service;

import com.amex.benefit_activation_engine.model.Benefit;
import com.amex.benefit_activation_engine.model.BenefitType;
import com.amex.benefit_activation_engine.model.Claim;
import com.amex.benefit_activation_engine.model.ClaimAuditEvent;
import com.amex.benefit_activation_engine.model.ClaimDecision;
import com.amex.benefit_activation_engine.model.ClaimStatus;
import com.amex.benefit_activation_engine.model.Transaction;
import com.amex.benefit_activation_engine.model.TransactionStatus;
import com.amex.benefit_activation_engine.repository.BenefitRepository;
import com.amex.benefit_activation_engine.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the approval state machine: auto-approval + payout, routing to manual
 * review, reviewer approve/reject, entitlement re-check, illegal-transition
 * enforcement, and the immutable audit trail.
 */
@SpringBootTest
@Transactional
class ClaimWorkflowServiceTest {

    @Autowired
    private ClaimWorkflowService workflowService;
    @Autowired
    private ClaimService claimService;
    @Autowired
    private BenefitRepository benefitRepository;
    @Autowired
    private TransactionRepository transactionRepository;

    private Benefit benefit(BenefitType type) {
        return benefitRepository.findByType(type).orElseThrow();
    }

    /** Creates a PREFILLED claim for the given card/category/amount and benefit. */
    private Claim prefilled(String cardProduct, String category, String amount, BenefitType type) {
        Transaction txn = transactionRepository.save(Transaction.builder()
                .cardMemberId("CM-1001")
                .cardProduct(cardProduct)
                .merchantName("Best Electronics")
                .merchantCategory(category)
                .amount(new BigDecimal(amount))
                .currency("USD")
                .purchaseDate(LocalDate.of(2026, 7, 20))
                .description("Test item")
                .status(TransactionStatus.MATCHED)
                .build());
        return claimService.generateFor(txn, benefit(type));
    }

    private List<ClaimStatus> auditToStates(Long claimId) {
        return workflowService.getAuditTrail(claimId).stream()
                .map(ClaimAuditEvent::getToStatus)
                .toList();
    }

    @Test
    void submit_smallClaim_isAutoApprovedAndPaid() {
        // 400 <= 700 threshold -> auto-approve -> disburse -> PAID.
        Claim claim = prefilled("PLATINUM", "ELECTRONICS", "400.00", BenefitType.PURCHASE_PROTECTION);

        Claim result = workflowService.submit(claim.getId());

        assertThat(result.getStatus()).isEqualTo(ClaimStatus.PAID);
        assertThat(result.getSubmittedAt()).isNotNull();
        assertThat(result.getDecidedAt()).isNotNull();
        assertThat(result.getPayoutReference()).startsWith("MOCK-PAYOUT-");
        assertThat(auditToStates(claim.getId()))
                .containsExactly(ClaimStatus.SUBMITTED, ClaimStatus.APPROVED, ClaimStatus.PAID);
    }

    @Test
    void submit_largeClaim_isRoutedToManualReview() {
        // 800 > 700 threshold -> UNDER_REVIEW, no payout yet.
        Claim claim = prefilled("PLATINUM", "ELECTRONICS", "800.00", BenefitType.PURCHASE_PROTECTION);

        Claim result = workflowService.submit(claim.getId());

        assertThat(result.getStatus()).isEqualTo(ClaimStatus.UNDER_REVIEW);
        assertThat(result.getSubmittedAt()).isNotNull();
        assertThat(result.getPayoutReference()).isNull();
        assertThat(auditToStates(claim.getId()))
                .containsExactly(ClaimStatus.SUBMITTED, ClaimStatus.UNDER_REVIEW);
    }

    @Test
    void reviewerApprove_movesToPaid() {
        Claim claim = prefilled("PLATINUM", "ELECTRONICS", "800.00", BenefitType.PURCHASE_PROTECTION);
        workflowService.submit(claim.getId()); // -> UNDER_REVIEW

        Claim result = workflowService.decide(claim.getId(), ClaimDecision.APPROVE, "Docs verified");

        assertThat(result.getStatus()).isEqualTo(ClaimStatus.PAID);
        assertThat(result.getPayoutReference()).startsWith("MOCK-PAYOUT-");
        assertThat(auditToStates(claim.getId()))
                .containsExactly(ClaimStatus.SUBMITTED, ClaimStatus.UNDER_REVIEW,
                        ClaimStatus.APPROVED, ClaimStatus.PAID);
    }

    @Test
    void reviewerReject_movesToRejected_withReason_andNoPayout() {
        Claim claim = prefilled("PLATINUM", "ELECTRONICS", "800.00", BenefitType.PURCHASE_PROTECTION);
        workflowService.submit(claim.getId()); // -> UNDER_REVIEW

        Claim result = workflowService.decide(claim.getId(), ClaimDecision.REJECT, "Insufficient evidence");

        assertThat(result.getStatus()).isEqualTo(ClaimStatus.REJECTED);
        assertThat(result.getDecisionReason()).isEqualTo("Insufficient evidence");
        assertThat(result.getPayoutReference()).isNull();
        assertThat(auditToStates(claim.getId()))
                .containsExactly(ClaimStatus.SUBMITTED, ClaimStatus.UNDER_REVIEW, ClaimStatus.REJECTED);
    }

    @Test
    void deciding_aClaimNotUnderReview_isIllegalTransition() {
        // A freshly PREFILLED claim cannot jump straight to APPROVED.
        Claim claim = prefilled("PLATINUM", "ELECTRONICS", "800.00", BenefitType.PURCHASE_PROTECTION);

        assertThatThrownBy(() -> workflowService.decide(claim.getId(), ClaimDecision.APPROVE, "x"))
                .isInstanceOf(IllegalClaimTransitionException.class);
    }

    @Test
    void resubmitting_aPaidClaim_isIllegalTransition() {
        Claim claim = prefilled("PLATINUM", "ELECTRONICS", "400.00", BenefitType.PURCHASE_PROTECTION);
        workflowService.submit(claim.getId()); // -> PAID (auto)

        assertThatThrownBy(() -> workflowService.submit(claim.getId()))
                .isInstanceOf(IllegalClaimTransitionException.class);
    }

    @Test
    void submit_whenCardNotEntitledToBenefit_isRejected() {
        // GREEN is entitled to purchase protection only; a return-protection claim
        // must be blocked at submission by the entitlement re-check.
        Claim claim = prefilled("GREEN", "ELECTRONICS", "200.00", BenefitType.RETURN_PROTECTION);

        assertThatThrownBy(() -> workflowService.submit(claim.getId()))
                .isInstanceOf(ClaimNotEntitledException.class);
    }

    @Test
    void auditTrail_unknownClaim_throwsNotFound() {
        assertThatThrownBy(() -> workflowService.getAuditTrail(999999L))
                .isInstanceOf(ClaimNotFoundException.class);
    }
}
