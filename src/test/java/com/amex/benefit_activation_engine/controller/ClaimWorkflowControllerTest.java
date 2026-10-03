package com.amex.benefit_activation_engine.controller;

import com.amex.benefit_activation_engine.model.Benefit;
import com.amex.benefit_activation_engine.model.BenefitType;
import com.amex.benefit_activation_engine.model.Claim;
import com.amex.benefit_activation_engine.model.Transaction;
import com.amex.benefit_activation_engine.model.TransactionStatus;
import com.amex.benefit_activation_engine.repository.BenefitRepository;
import com.amex.benefit_activation_engine.repository.TransactionRepository;
import com.amex.benefit_activation_engine.security.JwtService;
import com.amex.benefit_activation_engine.security.Roles;
import com.amex.benefit_activation_engine.service.ClaimService;
import com.amex.benefit_activation_engine.service.ClaimDocumentService;
import com.amex.benefit_activation_engine.support.ClaimDocumentTestSupport;
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
 *
 * <p>Auth pattern: uses JWT Bearer tokens (same as {@link AdminClaimControllerTest})
 * rather than {@code @WithMockUser} — required for compatibility with the custom
 * {@link com.amex.benefit_activation_engine.security.JwtAuthFilter}.</p>
 *
 * <p>Role assignments:
 * <ul>
 *   <li>{@code POST /api/claims/{id}/submit} — CARD_MEMBER who owns the claim
 *   <li>{@code POST /api/claims/{id}/decision} — REVIEWER
 *   <li>{@code GET  /api/claims/{id}/audit}   — REVIEWER
 * </ul></p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ClaimWorkflowControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private ClaimService claimService;
    @Autowired
    private ClaimDocumentService claimDocumentService;
    @Autowired
    private BenefitRepository benefitRepository;
    @Autowired
    private TransactionRepository transactionRepository;

    // ── token helpers ────────────────────────────────────────────────────────────

    /** Token for the card member who owns the test claims (cardMemberId = "CM-1001"). */
    private String ownerToken() {
        return "Bearer " + jwtService.issueToken("CM-1001");
    }

    private String reviewerToken() {
        return "Bearer " + jwtService.issueToken("admin", Roles.REVIEWER);
    }

    // ── test data ────────────────────────────────────────────────────────────────

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

    private void uploadRequiredDocuments(Claim claim) {
        ClaimDocumentTestSupport.uploadRequiredDocuments(claimDocumentService, claim);
    }

    // ── tests ────────────────────────────────────────────────────────────────────

    @Test
    void submitSmallClaim_autoApprovedAndPaid() throws Exception {
        Claim claim = prefilled("400.00");
        uploadRequiredDocuments(claim);

        mockMvc.perform(post("/api/claims/{id}/submit", claim.getId())
                        .header("Authorization", ownerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.payoutReference").exists());
    }

            @Test
            void duplicateSubmission_returnsConflictAndDoesNotAppendAuditEvents() throws Exception {
            Claim claim = prefilled("400.00");
            uploadRequiredDocuments(claim);

            mockMvc.perform(post("/api/claims/{id}/submit", claim.getId())
                    .header("Authorization", ownerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));

            mockMvc.perform(post("/api/claims/{id}/submit", claim.getId())
                    .header("Authorization", ownerToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Illegal claim transition"));

            mockMvc.perform(get("/api/claims/{id}/audit", claim.getId())
                    .header("Authorization", reviewerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[2].toStatus").value("PAID"));
            }

    @Test
    void submitLargeClaim_thenReviewerApproves() throws Exception {
        Claim claim = prefilled("800.00");
        uploadRequiredDocuments(claim);

        mockMvc.perform(post("/api/claims/{id}/submit", claim.getId())
                        .header("Authorization", ownerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"));

        mockMvc.perform(post("/api/claims/{id}/decision", claim.getId())
                        .header("Authorization", reviewerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\",\"reason\":\"Docs verified\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    void auditTrail_isReturnedForSubmittedClaim() throws Exception {
        Claim claim = prefilled("400.00");
        uploadRequiredDocuments(claim);
        mockMvc.perform(post("/api/claims/{id}/submit", claim.getId())
                        .header("Authorization", ownerToken()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/claims/{id}/audit", claim.getId())
                        .header("Authorization", reviewerToken()))
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
                        .header("Authorization", reviewerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Illegal claim transition"));
    }

    @Test
    void submittingUnknownClaim_returns404() throws Exception {
        mockMvc.perform(post("/api/claims/{id}/submit", 999999L)
                        .header("Authorization", ownerToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void submittingExpiredPurchaseClaim_returnsConflict() throws Exception {
        Claim claim = prefilled("400.00");
        claim.getTransaction().setPurchaseDate(LocalDate.now().minusDays(91));
        transactionRepository.save(claim.getTransaction());

        mockMvc.perform(post("/api/claims/{id}/submit", claim.getId())
                        .header("Authorization", ownerToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Claim coverage expired"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("expired")));
    }

    @Test
    void submit_withoutToken_returns401() throws Exception {
        Claim claim = prefilled("400.00");
        mockMvc.perform(post("/api/claims/{id}/submit", claim.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void decision_withCardMemberToken_returns403() throws Exception {
        Claim claim = prefilled("800.00");
        mockMvc.perform(post("/api/claims/{id}/decision", claim.getId())
                        .header("Authorization", ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\"}"))
                .andExpect(status().isForbidden());
    }
}
