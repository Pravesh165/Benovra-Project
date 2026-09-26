package com.amex.benefit_activation_engine.controller;

import com.amex.benefit_activation_engine.dto.CreateTransactionRequest;
import com.amex.benefit_activation_engine.model.Claim;
import com.amex.benefit_activation_engine.model.ClaimStatus;
import com.amex.benefit_activation_engine.model.Transaction;
import com.amex.benefit_activation_engine.repository.ClaimAuditRepository;
import com.amex.benefit_activation_engine.repository.ClaimRepository;
import com.amex.benefit_activation_engine.repository.TransactionRepository;
import com.amex.benefit_activation_engine.service.ClaimWorkflowService;
import com.amex.benefit_activation_engine.service.IngestionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end pipeline check (WATCH → MATCH → PRE-FILL → WORKFLOW):
 * ingesting a qualifying purchase auto-generates a PREFILLED claim, which is
 * then submitted, auto-approved, and paid — with a complete audit trail.
 *
 * <p>Not {@code @Transactional}: detection runs on {@code AFTER_COMMIT}, which
 * requires a real commit. Data is cleaned up afterwards.</p>
 */
@SpringBootTest
class FullClaimFlowIntegrationTest {

    @Autowired
    private IngestionService ingestionService;
    @Autowired
    private ClaimWorkflowService workflowService;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private ClaimRepository claimRepository;
    @Autowired
    private ClaimAuditRepository auditRepository;

    @AfterEach
    void cleanUp() {
        auditRepository.deleteAll();
        claimRepository.deleteAll();
        transactionRepository.deleteAll();
    }

    @Test
    void ingestQualifyingPurchase_autoPrefillsClaim_thenSubmitsAndPays() {
        // WATCH + MATCH + PRE-FILL: ingest commits, detection listener runs, claim created.
        Transaction txn = ingestionService.ingest(CreateTransactionRequest.builder()
                .cardMemberId("CM-1001")
                .cardProduct("platinum")
                .merchantName("Best Electronics")
                .merchantCategory("electronics")
                .amount(new BigDecimal("400.00")) // <= 700 -> auto-approve
                .currency("usd")
                .purchaseDate(LocalDate.of(2026, 7, 20))
                .description("Bluetooth speaker")
                .build());

        List<Claim> claims = claimRepository.findByTransactionId(txn.getId());
        assertThat(claims).hasSize(1);
        Claim prefilled = claims.get(0);
        assertThat(prefilled.getStatus()).isEqualTo(ClaimStatus.PREFILLED);

        // WORKFLOW: submit -> auto-approve -> pay.
        Claim paid = workflowService.submit(prefilled.getId());

        assertThat(paid.getStatus()).isEqualTo(ClaimStatus.PAID);
        assertThat(paid.getPayoutReference()).startsWith("MOCK-PAYOUT-");
        assertThat(workflowService.getAuditTrail(paid.getId()))
                .extracting(e -> e.getToStatus())
                .containsExactly(ClaimStatus.SUBMITTED, ClaimStatus.APPROVED, ClaimStatus.PAID);
    }
}
