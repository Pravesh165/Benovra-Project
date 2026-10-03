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
 * Verifies the PRE-FILL API: GET /api/claims (list) and GET /api/claims/{id}
 * (single), including the auto-populated field map and a 404 for unknown ids.
 *
 * <p>Auth: GET /api/claims requires REVIEWER role; GET /api/claims/{id} requires
 * any authenticated user. Tests use the JWT token pattern (same as
 * {@link AdminClaimControllerTest}) rather than {@code @WithMockUser} to be
 * compatible with the custom {@link com.amex.benefit_activation_engine.security.JwtAuthFilter}.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ClaimControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private ClaimService claimService;
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

    // ── test data ────────────────────────────────────────────────────────────────

    private Claim prefillPurchaseClaim() {
        Transaction txn = transactionRepository.save(Transaction.builder()
                .cardMemberId("CM-1001")
                .cardProduct("PLATINUM")
                .merchantName("Best Electronics")
                .merchantCategory("ELECTRONICS")
                .amount(new BigDecimal("499.99"))
                .currency("USD")
                .purchaseDate(LocalDate.of(2026, 7, 20))
                .description("MacBook Pro 14")
                .status(TransactionStatus.MATCHED)
                .build());
        Benefit benefit = benefitRepository.findByType(BenefitType.PURCHASE_PROTECTION).orElseThrow();
        return claimService.generateFor(txn, benefit);
    }

    // ── tests ────────────────────────────────────────────────────────────────────

    @Test
    void listReturnsPrefilledClaims() throws Exception {
        Claim claim = prefillPurchaseClaim();

        mockMvc.perform(get("/api/claims")
                        .header("Authorization", reviewerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(claim.getId()))
                .andExpect(jsonPath("$[0].status").value("PREFILLED"))
                .andExpect(jsonPath("$[0].benefitType").value("PURCHASE_PROTECTION"))
                .andExpect(jsonPath("$[0].claimAmount").value(499.99));
    }

    @Test
    void getByIdReturnsClaimWithPrefilledData() throws Exception {
        Claim claim = prefillPurchaseClaim();

        mockMvc.perform(get("/api/claims/{id}", claim.getId())
                        .header("Authorization", memberToken("CM-1001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(claim.getId()))
                .andExpect(jsonPath("$.status").value("PREFILLED"))
                .andExpect(jsonPath("$.benefitName").value("Purchase Protection"))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.coverageExpiresOn").value("2026-10-18"))
                .andExpect(jsonPath("$.prefilledData.incidentType").value("DAMAGE_OR_THEFT"))
                .andExpect(jsonPath("$.prefilledData.itemDescription").value("MacBook Pro 14"));
    }

    @Test
    void unknownClaim_returns404() throws Exception {
        mockMvc.perform(get("/api/claims/{id}", 999999L)
                        .header("Authorization", memberToken("CM-1001")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Not found"));
    }

    @Test
    void listClaims_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/claims"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listClaims_withCardMemberToken_returns403() throws Exception {
        mockMvc.perform(get("/api/claims")
                        .header("Authorization", memberToken("CM-1001")))
                .andExpect(status().isForbidden());
    }
}
