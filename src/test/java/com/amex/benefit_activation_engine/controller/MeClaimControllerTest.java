package com.amex.benefit_activation_engine.controller;

import com.amex.benefit_activation_engine.model.Benefit;
import com.amex.benefit_activation_engine.model.BenefitType;
import com.amex.benefit_activation_engine.model.Claim;
import com.amex.benefit_activation_engine.model.Transaction;
import com.amex.benefit_activation_engine.model.TransactionStatus;
import com.amex.benefit_activation_engine.repository.BenefitRepository;
import com.amex.benefit_activation_engine.repository.TransactionRepository;
import com.amex.benefit_activation_engine.security.JwtService;
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

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the customer auth + scoped claims flow:
 * <ul>
 *   <li>login issues a usable Bearer token,</li>
 *   <li>{@code /api/me/claims} is protected and returns only the caller's claims
 *       (optionally filtered by status), and</li>
 *   <li>submitting another member's claim is rejected with 403.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MeClaimControllerTest {

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

    private static final String ALICE = "CM-ALICE";
    private static final String BOB = "CM-BOB";

    private Claim prefillClaimFor(String cardMemberId, String item, String amount) {
        Transaction txn = transactionRepository.save(Transaction.builder()
                .cardMemberId(cardMemberId)
                .cardProduct("PLATINUM")
                .merchantName("Best Electronics")
                .merchantCategory("ELECTRONICS")
                .amount(new BigDecimal(amount))
                .currency("USD")
                .purchaseDate(LocalDate.of(2026, 7, 20))
                .description(item)
                .status(TransactionStatus.MATCHED)
                .build());
        Benefit benefit = benefitRepository.findByType(BenefitType.PURCHASE_PROTECTION).orElseThrow();
        return claimService.generateFor(txn, benefit);
    }

    private String bearerFor(String cardMemberId) {
        return "Bearer " + jwtService.issueToken(cardMemberId);
    }

    @Test
    void login_issuesToken() throws Exception {
        String body = "{\"cardMemberId\":\"" + ALICE + "\"}";

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.cardMemberId").value(ALICE));
    }

    @Test
    void myClaims_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/me/claims"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void myClaims_returnsOnlyCallersClaims() throws Exception {
        prefillClaimFor(ALICE, "MacBook Pro 14", "499.99");
        prefillClaimFor(BOB, "Sony TV", "600.00");

        mockMvc.perform(get("/api/me/claims")
                        .header("Authorization", bearerFor(ALICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].prefilledData.cardMemberId").value(ALICE));
    }

    @Test
    void myClaims_filtersByStatus() throws Exception {
        prefillClaimFor(ALICE, "MacBook Pro 14", "499.99");

        mockMvc.perform(get("/api/me/claims").param("status", "PREFILLED")
                        .header("Authorization", bearerFor(ALICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status").value("PREFILLED"));

        mockMvc.perform(get("/api/me/claims").param("status", "SUBMITTED")
                        .header("Authorization", bearerFor(ALICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void submit_ownClaim_succeeds() throws Exception {
        Claim claim = prefillClaimFor(ALICE, "MacBook Pro 14", "499.99");

        mockMvc.perform(post("/api/me/claims/{id}/submit", claim.getId())
                        .header("Authorization", bearerFor(ALICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(claim.getId()))
                // 499.99 <= 700 auto-approve threshold -> auto-approved & paid
                .andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    void submit_othersClaim_isForbidden() throws Exception {
        Claim bobsClaim = prefillClaimFor(BOB, "Sony TV", "600.00");

        mockMvc.perform(post("/api/me/claims/{id}/submit", bobsClaim.getId())
                        .header("Authorization", bearerFor(ALICE)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Forbidden"));
    }
}
