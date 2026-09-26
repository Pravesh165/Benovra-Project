package com.amex.benefit_activation_engine.controller;

import com.amex.benefit_activation_engine.model.Benefit;
import com.amex.benefit_activation_engine.model.BenefitType;
import com.amex.benefit_activation_engine.model.Claim;
import com.amex.benefit_activation_engine.model.Transaction;
import com.amex.benefit_activation_engine.model.TransactionStatus;
import com.amex.benefit_activation_engine.repository.BenefitRepository;
import com.amex.benefit_activation_engine.repository.TransactionRepository;
import com.amex.benefit_activation_engine.service.ClaimService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the WORKFLOW API: submit (auto-approve + manual routes), reviewer
 * decision, audit trail, illegal transition (409), and unknown claim (404).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ClaimWorkflowControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ClaimService claimService;
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
    void submitSmallClaim_autoApprovedAndPaid() throws Exception {
        Claim claim = prefilled("400.00");

        mockMvc.perform(post("/api/claims/{id}/submit", claim.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.payoutReference").exists());
    }

    @Test
    void submitLargeClaim_thenReviewerApproves() throws Exception {
        Claim claim = prefilled("800.00");

        mockMvc.perform(post("/api/claims/{id}/submit", claim.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"));

        mockMvc.perform(post("/api/claims/{id}/decision", claim.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\",\"reason\":\"Docs verified\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    void auditTrail_isReturnedForSubmittedClaim() throws Exception {
        Claim claim = prefilled("400.00");
        mockMvc.perform(post("/api/claims/{id}/submit", claim.getId()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/claims/{id}/audit", claim.getId()))
                .andExpect(status().isOk())
                // SUBMITTED, APPROVED, PAID
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].toStatus").value("SUBMITTED"))
                .andExpect(jsonPath("$[2].toStatus").value("PAID"))
                .andExpect(jsonPath("$[2].actor").value("BANK"));
    }

    @Test
    void decidingAClaimNotUnderReview_returns409() throws Exception {
        Claim claim = prefilled("800.00"); // still PREFILLED

        mockMvc.perform(post("/api/claims/{id}/decision", claim.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Illegal claim transition"));
    }

    @Test
    void submittingUnknownClaim_returns404() throws Exception {
        mockMvc.perform(post("/api/claims/{id}/submit", 999999L))
                .andExpect(status().isNotFound());
    }
}
