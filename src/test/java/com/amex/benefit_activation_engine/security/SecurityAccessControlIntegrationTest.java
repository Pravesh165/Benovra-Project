package com.amex.benefit_activation_engine.security;

import com.amex.benefit_activation_engine.model.Benefit;
import com.amex.benefit_activation_engine.model.BenefitType;
import com.amex.benefit_activation_engine.model.Claim;
import com.amex.benefit_activation_engine.model.Transaction;
import com.amex.benefit_activation_engine.model.TransactionStatus;
import com.amex.benefit_activation_engine.repository.BenefitRepository;
import com.amex.benefit_activation_engine.repository.TransactionRepository;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security boundary / BOLA integration tests.
 *
 * <p>Validates that the {@link SecurityConfig} access control rules are correctly
 * enforced across every protected endpoint. Uses the JWT token helper pattern
 * (same as {@code AdminClaimControllerTest}) — {@code @WithMockUser} is NOT used
 * because the custom {@link JwtAuthFilter} handles authentication and must see a
 * real Bearer header.</p>
 *
 * <p>Each test name follows the pattern:
 * {@code endpoint_role_expectedResult}.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SecurityAccessControlIntegrationTest {

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

    private String reviewerToken() {
        return "Bearer " + jwtService.issueToken("admin", Roles.REVIEWER);
    }

    private String memberToken(String cardMemberId) {
        return "Bearer " + jwtService.issueToken(cardMemberId);
    }

    // ── test data helper ─────────────────────────────────────────────────────────

    private Claim createClaimForMember(String cardMemberId) {
        Transaction txn = transactionRepository.save(Transaction.builder()
                .cardMemberId(cardMemberId)
                .cardProduct("PLATINUM")
                .merchantName("Security Test Store")
                .merchantCategory("ELECTRONICS")
                .amount(new BigDecimal("299.99"))
                .currency("USD")
                .purchaseDate(LocalDate.of(2026, 7, 20))
                .description("Security test item")
                .status(TransactionStatus.MATCHED)
                .build());
        Benefit benefit = benefitRepository.findByType(BenefitType.PURCHASE_PROTECTION).orElseThrow();
        return claimService.generateFor(txn, benefit);
    }

    // ── GET /api/claims (list) ────────────────────────────────────────────────────

    @Test
    void claimsList_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/claims"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void claimsList_cardMemberToken_returns403() throws Exception {
        mockMvc.perform(get("/api/claims")
                        .header("Authorization", memberToken("CM-1001")))
                .andExpect(status().isForbidden());
    }

    @Test
    void claimsList_reviewerToken_returns200() throws Exception {
        mockMvc.perform(get("/api/claims")
                        .header("Authorization", reviewerToken()))
                .andExpect(status().isOk());
    }

    // ── GET /api/claims/{id} (single) ─────────────────────────────────────────────

    @Test
    void claimById_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/claims/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void claimById_ownerMemberToken_returns200() throws Exception {
        Claim claim = createClaimForMember("CM-5001");
        mockMvc.perform(get("/api/claims/{id}", claim.getId())
                        .header("Authorization", memberToken("CM-5001")))
                .andExpect(status().isOk());
    }

    @Test
    void claimById_wrongMemberToken_returns403() throws Exception {
        Claim claim = createClaimForMember("CM-5001");
        mockMvc.perform(get("/api/claims/{id}", claim.getId())
                        .header("Authorization", memberToken("CM-9999")))
                .andExpect(status().isForbidden());
    }

    @Test
    void claimById_reviewerToken_returns200() throws Exception {
        Claim claim = createClaimForMember("CM-5001");
        mockMvc.perform(get("/api/claims/{id}", claim.getId())
                        .header("Authorization", reviewerToken()))
                .andExpect(status().isOk());
    }

    // ── POST /api/claims/{id}/submit ─────────────────────────────────────────────

    @Test
    void submit_noToken_returns401() throws Exception {
        Claim claim = createClaimForMember("CM-5002");
        mockMvc.perform(post("/api/claims/{id}/submit", claim.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void submit_wrongOwner_returns403() throws Exception {
        // Claim belongs to CM-5003; caller is CM-9999 → forbidden.
        Claim claim = createClaimForMember("CM-5003");
        mockMvc.perform(post("/api/claims/{id}/submit", claim.getId())
                        .header("Authorization", memberToken("CM-9999")))
                .andExpect(status().isForbidden());
    }

    @Test
    void submit_correctOwner_returns200() throws Exception {
        Claim claim = createClaimForMember("CM-5004");
        ClaimDocumentTestSupport.uploadRequiredDocuments(claimDocumentService, claim);
        mockMvc.perform(post("/api/claims/{id}/submit", claim.getId())
                        .header("Authorization", memberToken("CM-5004")))
                .andExpect(status().isOk());
    }

    // ── POST /api/claims/{id}/decision ────────────────────────────────────────────

    @Test
    void decision_noToken_returns401() throws Exception {
        mockMvc.perform(post("/api/claims/1/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void decision_cardMemberToken_returns403() throws Exception {
        Claim claim = createClaimForMember("CM-5005");
        mockMvc.perform(post("/api/claims/{id}/decision", claim.getId())
                        .header("Authorization", memberToken("CM-5005"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\"}"))
                .andExpect(status().isForbidden());
    }

    // ── GET /api/claims/{id}/audit ────────────────────────────────────────────────

    @Test
    void audit_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/claims/1/audit"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void audit_cardMemberToken_returns403() throws Exception {
        Claim claim = createClaimForMember("CM-5006");
        mockMvc.perform(get("/api/claims/{id}/audit", claim.getId())
                        .header("Authorization", memberToken("CM-5006")))
                .andExpect(status().isForbidden());
    }

    @Test
    void audit_reviewerToken_returns200() throws Exception {
        Claim claim = createClaimForMember("CM-5007");
        mockMvc.perform(get("/api/claims/{id}/audit", claim.getId())
                        .header("Authorization", reviewerToken()))
                .andExpect(status().isOk());
    }

    // ── GET /api/transactions ─────────────────────────────────────────────────────

    @Test
    void transactionsList_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/transactions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void transactionsList_cardMemberToken_returns403() throws Exception {
        mockMvc.perform(get("/api/transactions")
                        .header("Authorization", memberToken("CM-1001")))
                .andExpect(status().isForbidden());
    }

    @Test
    void transactionsList_reviewerToken_returns200() throws Exception {
        mockMvc.perform(get("/api/transactions")
                        .header("Authorization", reviewerToken()))
                .andExpect(status().isOk());
    }

    // ── Admin claims API ──────────────────────────────────────────────────────────

    @Test
    void adminClaims_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/admin/claims"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminClaims_cardMemberToken_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/claims")
                        .header("Authorization", memberToken("CM-1001")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminClaims_reviewerToken_returns200() throws Exception {
        mockMvc.perform(get("/api/admin/claims")
                        .header("Authorization", reviewerToken()))
                .andExpect(status().isOk());
    }

    // ── Public endpoints should not require auth ──────────────────────────────────

    @Test
    void feedAdmin_noToken_returns200() throws Exception {
        mockMvc.perform(get("/api/admin/feed"))
                .andExpect(status().isOk());
    }

    @Test
    void benefits_noToken_returns200() throws Exception {
        Claim claim = createClaimForMember("CM-PUBLIC-BENEFITS");
        mockMvc.perform(get("/api/benefits/{transactionId}", claim.getTransaction().getId()))
                .andExpect(status().isOk());
    }

    @Test
    void transactionsPost_noToken_returns201OrBadRequest() throws Exception {
        // POST /api/transactions is public. An empty body → 400, not 401.
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
