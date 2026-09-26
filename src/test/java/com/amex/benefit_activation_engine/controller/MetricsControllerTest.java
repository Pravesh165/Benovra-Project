package com.amex.benefit_activation_engine.controller;

import com.amex.benefit_activation_engine.model.Benefit;
import com.amex.benefit_activation_engine.model.BenefitType;
import com.amex.benefit_activation_engine.model.Claim;
import com.amex.benefit_activation_engine.model.Transaction;
import com.amex.benefit_activation_engine.model.TransactionStatus;
import com.amex.benefit_activation_engine.repository.BenefitRepository;
import com.amex.benefit_activation_engine.repository.TransactionRepository;
import com.amex.benefit_activation_engine.service.ClaimService;
import com.amex.benefit_activation_engine.service.ClaimWorkflowService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies GET /api/metrics returns the aggregated detection and
 * unclaimed-reduction figures.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MetricsControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ClaimService claimService;
    @Autowired
    private ClaimWorkflowService workflowService;
    @Autowired
    private BenefitRepository benefitRepository;
    @Autowired
    private TransactionRepository transactionRepository;

    @Test
    void metricsReflectAPaidClaim() throws Exception {
        Transaction txn = transactionRepository.save(Transaction.builder()
                .cardMemberId("CM-1001")
                .cardProduct("PLATINUM")
                .merchantName("Best Electronics")
                .merchantCategory("ELECTRONICS")
                .amount(new BigDecimal("400.00"))
                .currency("USD")
                .purchaseDate(LocalDate.of(2026, 7, 20))
                .description("Speaker")
                .status(TransactionStatus.MATCHED)
                .build());
        Benefit benefit = benefitRepository.findByType(BenefitType.PURCHASE_PROTECTION).orElseThrow();
        Claim claim = claimService.generateFor(txn, benefit);
        workflowService.submit(claim.getId()); // 400 <= 700 -> PAID

        mockMvc.perform(get("/api/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTransactions").value(1))
                .andExpect(jsonPath("$.matchedTransactions").value(1))
                .andExpect(jsonPath("$.detectionRatePct").value(100.0))
                .andExpect(jsonPath("$.totalClaims").value(1))
                .andExpect(jsonPath("$.totalDetectableValue").value(400.00))
                .andExpect(jsonPath("$.claimedValue").value(400.00))
                .andExpect(jsonPath("$.paidValue").value(400.00))
                .andExpect(jsonPath("$.unclaimedValue").value(0.0))
                .andExpect(jsonPath("$.unclaimedReductionPct").value(100.0))
                .andExpect(jsonPath("$.claimsByStatus.PAID").value(1));
    }
}
