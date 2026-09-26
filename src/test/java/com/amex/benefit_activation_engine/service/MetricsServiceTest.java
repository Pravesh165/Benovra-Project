package com.amex.benefit_activation_engine.service;

import com.amex.benefit_activation_engine.dto.MetricsResponse;
import com.amex.benefit_activation_engine.model.Benefit;
import com.amex.benefit_activation_engine.model.BenefitType;
import com.amex.benefit_activation_engine.model.Claim;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the Task 5 metrics aggregation against a known set of claims:
 * detectable value, claimed value, paid value, unclaimed remainder, and the
 * headline % reduction in unclaimed benefits.
 */
@SpringBootTest
@Transactional
class MetricsServiceTest {

    @Autowired
    private MetricsService metricsService;
    @Autowired
    private ClaimService claimService;
    @Autowired
    private ClaimWorkflowService workflowService;
    @Autowired
    private BenefitRepository benefitRepository;
    @Autowired
    private TransactionRepository transactionRepository;

    private Claim prefilled(String amount) {
        Transaction txn = transactionRepository.save(Transaction.builder()
                .cardMemberId("CM-1001")
                .cardProduct("PLATINUM")
                .merchantName("Best Electronics")
                .merchantCategory("ELECTRONICS")
                .amount(new BigDecimal(amount))
                .currency("USD")
                .purchaseDate(LocalDate.of(2026, 7, 20))
                .description("Test item")
                .status(TransactionStatus.MATCHED)
                .build());
        Benefit benefit = benefitRepository.findByType(BenefitType.PURCHASE_PROTECTION).orElseThrow();
        return claimService.generateFor(txn, benefit);
    }

    @Test
    void aggregatesDetectionAndUnclaimedReduction() {
        // A: 400 -> submit -> PAID (<=700)     claimed + paid
        // B: 800 -> submit -> UNDER_REVIEW     claimed, not paid
        // C: 200 -> left PREFILLED             unclaimed
        Claim a = prefilled("400.00");
        Claim b = prefilled("800.00");
        prefilled("200.00");

        workflowService.submit(a.getId());
        workflowService.submit(b.getId());

        MetricsResponse m = metricsService.compute();

        assertThat(m.totalTransactions()).isEqualTo(3);
        assertThat(m.matchedTransactions()).isEqualTo(3);
        assertThat(m.detectionRatePct()).isEqualTo(100.0);

        assertThat(m.totalClaims()).isEqualTo(3);
        assertThat(m.claimsByStatus())
                .containsEntry(ClaimStatus.PAID, 1L)
                .containsEntry(ClaimStatus.UNDER_REVIEW, 1L)
                .containsEntry(ClaimStatus.PREFILLED, 1L);

        assertThat(m.totalDetectableValue()).isEqualByComparingTo("1400.00"); // 400+800+200
        assertThat(m.claimedValue()).isEqualByComparingTo("1200.00");         // 400+800
        assertThat(m.paidValue()).isEqualByComparingTo("400.00");             // 400
        assertThat(m.unclaimedValue()).isEqualByComparingTo("200.00");        // 1400-1200
        assertThat(m.unclaimedReductionPct()).isEqualTo(85.71);               // 1200/1400
        assertThat(m.currency()).isEqualTo("USD");
    }

    @Test
    void emptyState_returnsZeroesWithoutDivideByZero() {
        MetricsResponse m = metricsService.compute();

        assertThat(m.totalClaims()).isEqualTo(0);
        assertThat(m.totalDetectableValue()).isEqualByComparingTo("0");
        assertThat(m.detectionRatePct()).isEqualTo(0.0);
        assertThat(m.unclaimedReductionPct()).isEqualTo(0.0);
    }
}
