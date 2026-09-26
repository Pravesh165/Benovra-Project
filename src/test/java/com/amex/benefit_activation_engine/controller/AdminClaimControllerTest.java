package com.amex.benefit_activation_engine.controller;

import com.amex.benefit_activation_engine.model.Benefit;
import com.amex.benefit_activation_engine.model.BenefitType;
import com.amex.benefit_activation_engine.model.Claim;
import com.amex.benefit_activation_engine.model.ClaimDecision;
import com.amex.benefit_activation_engine.model.Transaction;
import com.amex.benefit_activation_engine.model.TransactionStatus;
import com.amex.benefit_activation_engine.repository.BenefitRepository;
import com.amex.benefit_activation_engine.repository.TransactionRepository;
import com.amex.benefit_activation_engine.security.JwtService;
import com.amex.benefit_activation_engine.security.Roles;
import com.amex.benefit_activation_engine.service.ClaimService;
import com.amex.benefit_activation_engine.service.ClaimWorkflowService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the reviewer API: role protection on /api/admin/**, cross-customer
 * listing with filters, and approve/reject decisions.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminClaimControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private ClaimService claimService;
    @Autowired
    private ClaimWorkflowService workflowService;
    @Autowired
    private BenefitRepository benefitRepository;
    @Autowired
    private TransactionRepository transactionRepository;

    private Claim prefillClaimFor(String cardMemberId, String cardProduct, String amount) {
        Transaction txn = transactionRepository.save(Transaction.builder()
                .cardMemberId(cardMemberId)
                .cardProduct(cardProduct)
                .merchantName("Best Electronics")
                .merchantCategory("ELECTRONICS")
                .amount(new BigDecimal(amount))
                .currency("USD")
                .purchaseDate(LocalDate.of(2026, 7, 20))
                .description("Test purchase")
                .status(TransactionStatus.MATCHED)
                .build());
        Benefit benefit = benefitRepository.findByType(BenefitType.PURCHASE_PROTECTION).orElseThrow();
        return claimService.generateFor(txn, benefit);
    }

    private String reviewerToken() {
        return "Bearer " + jwtService.issueToken("admin", Roles.REVIEWER);
    }

    private String memberToken(String id) {
        return "Bearer " + jwtService.issueToken(id);
    }

    @Test
    void adminClaims_withoutToken_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/claims"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminClaims_withCardMemberToken_isForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/claims")
                        .header("Authorization", memberToken("CM-1001")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminClaims_reviewer_seesAllCustomers() throws Exception {
        prefillClaimFor("CM-1001", "PLATINUM", "499.99");
        prefillClaimFor("CM-1002", "GOLD", "300.00");

        mockMvc.perform(get("/api/admin/claims")
                        .header("Authorization", reviewerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].cardMemberId").exists())
                .andExpect(jsonPath("$[0].cardProduct").exists())
                .andExpect(jsonPath("$[0].merchantCategory").value("ELECTRONICS"));
    }

    @Test
    void adminClaims_filtersByCardMemberId() throws Exception {
        prefillClaimFor("CM-1001", "PLATINUM", "499.99");
        prefillClaimFor("CM-1002", "GOLD", "300.00");

        mockMvc.perform(get("/api/admin/claims").param("cardMemberId", "CM-1002")
                        .header("Authorization", reviewerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].cardMemberId").value("CM-1002"));
    }

    @Test
    void adminClaims_filtersByStatus_reviewQueue() throws Exception {
        // 900 > auto-approve threshold (700) -> routed to UNDER_REVIEW on submit.
        Claim big = prefillClaimFor("CM-1001", "PLATINUM", "900.00");
        workflowService.submit(big.getId());
        // 100 <= threshold -> auto-approved & PAID, so not in the review queue.
        Claim small = prefillClaimFor("CM-1002", "GOLD", "100.00");
        workflowService.submit(small.getId());

        mockMvc.perform(get("/api/admin/claims").param("status", "UNDER_REVIEW")
                        .header("Authorization", reviewerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(big.getId()))
                .andExpect(jsonPath("$[0].status").value("UNDER_REVIEW"));
    }

    @Test
    void adminClaims_filtersBySubmitted_returnsAllSubmittedOnwards() throws Exception {
        // Not submitted -> stays PREFILLED, must be excluded.
        prefillClaimFor("CM-1001", "PLATINUM", "250.00");
        // Submitted & auto-processed (<=700) -> PAID.
        Claim small = prefillClaimFor("CM-1002", "GOLD", "120.00");
        workflowService.submit(small.getId());
        // Submitted & routed to review (>700) -> UNDER_REVIEW.
        Claim big = prefillClaimFor("CM-1003", "PLATINUM", "950.00");
        workflowService.submit(big.getId());

        mockMvc.perform(get("/api/admin/claims").param("status", "SUBMITTED")
                        .header("Authorization", reviewerToken()))
                .andExpect(status().isOk())
                // both submitted claims, regardless of downstream status; PREFILLED excluded
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void adminClaims_filterApproved_showsReviewerApprovedEvenAfterProcessed() throws Exception {
        Claim big = prefillClaimFor("CM-1001", "PLATINUM", "900.00");
        workflowService.submit(big.getId());                                        // -> UNDER_REVIEW
        workflowService.decide(big.getId(), ClaimDecision.APPROVE, "receipt ok");   // APPROVED -> PAID

        // Shows under "Approved" (resolved from the reviewer's audit event) ...
        mockMvc.perform(get("/api/admin/claims").param("status", "APPROVED")
                        .header("Authorization", reviewerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + big.getId() + ")]", hasSize(1)));

        // ... and also under "Processed" (it was disbursed).
        mockMvc.perform(get("/api/admin/claims").param("status", "PAID")
                        .header("Authorization", reviewerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + big.getId() + ")]", hasSize(1)));
    }

    @Test
    void adminClaims_filterApproved_excludesAutoProcessed() throws Exception {
        Claim small = prefillClaimFor("CM-1002", "GOLD", "100.00");
        workflowService.submit(small.getId()); // auto-approved by SYSTEM -> PAID

        // Auto-processed claims were never reviewer-approved.
        mockMvc.perform(get("/api/admin/claims").param("status", "APPROVED")
                        .header("Authorization", reviewerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + small.getId() + ")]", hasSize(0)));

        // But they are processed.
        mockMvc.perform(get("/api/admin/claims").param("status", "PAID")
                        .header("Authorization", reviewerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + small.getId() + ")]", hasSize(1)));
    }

    @Test
    void decision_approve_movesClaimForward() throws Exception {
        Claim big = prefillClaimFor("CM-1001", "PLATINUM", "900.00");
        workflowService.submit(big.getId()); // -> UNDER_REVIEW

        String body = "{\"decision\":\"APPROVE\",\"reason\":\"Verified receipt\"}";
        mockMvc.perform(post("/api/admin/claims/{id}/decision", big.getId())
                        .header("Authorization", reviewerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(big.getId()))
                // approval disburses -> PAID
                .andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    void decision_reject_marksRejected() throws Exception {
        Claim big = prefillClaimFor("CM-1001", "PLATINUM", "900.00");
        workflowService.submit(big.getId()); // -> UNDER_REVIEW

        String body = "{\"decision\":\"REJECT\",\"reason\":\"Outside coverage window\"}";
        mockMvc.perform(post("/api/admin/claims/{id}/decision", big.getId())
                        .header("Authorization", reviewerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }
}
