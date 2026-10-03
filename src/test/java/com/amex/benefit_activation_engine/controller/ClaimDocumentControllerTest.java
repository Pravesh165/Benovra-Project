package com.amex.benefit_activation_engine.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.amex.benefit_activation_engine.model.Benefit;
import com.amex.benefit_activation_engine.model.BenefitType;
import com.amex.benefit_activation_engine.model.Claim;
import com.amex.benefit_activation_engine.model.ClaimDocumentType;
import com.amex.benefit_activation_engine.model.Transaction;
import com.amex.benefit_activation_engine.model.TransactionStatus;
import com.amex.benefit_activation_engine.repository.BenefitRepository;
import com.amex.benefit_activation_engine.repository.TransactionRepository;
import com.amex.benefit_activation_engine.security.JwtService;
import com.amex.benefit_activation_engine.security.Roles;
import com.amex.benefit_activation_engine.service.ClaimService;
import com.amex.benefit_activation_engine.service.ClaimDocumentService;
import com.amex.benefit_activation_engine.service.ClaimWorkflowService;
import com.amex.benefit_activation_engine.support.ClaimDocumentTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ClaimDocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ClaimService claimService;
        @Autowired
        private ClaimDocumentService claimDocumentService;
        @Autowired
        private ClaimWorkflowService workflowService;

    @Autowired
    private BenefitRepository benefitRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private static final String ALICE = "CM-ALICE";
    private static final String BOB = "CM-BOB";

    private Claim prefillClaimFor(String cardMemberId, String item, String amount) {
                return prefillClaimFor(cardMemberId, item, amount, BenefitType.PURCHASE_PROTECTION);
        }

        private Claim prefillClaimFor(String cardMemberId, String item, String amount, BenefitType benefitType) {
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
        Benefit benefit = benefitRepository.findByType(benefitType).orElseThrow();
        return claimService.generateFor(txn, benefit);
    }

    private String bearerFor(String cardMemberId) {
        return "Bearer " + jwtService.issueToken(cardMemberId);
    }

    private String reviewerBearer() {
        return "Bearer " + jwtService.issueToken("admin", Roles.REVIEWER);
    }

    @Test
    void member_canUploadListAndDeleteOwnDocuments() throws Exception {
        Claim claim = prefillClaimFor(ALICE, "MacBook Pro 14", "499.99");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "receipt.pdf",
                "application/pdf",
                "fake pdf bytes".getBytes()
        );

        String uploadJson = mockMvc.perform(multipart("/api/me/claims/{id}/documents", claim.getId())
                        .file(file)
                        .param("documentType", ClaimDocumentType.PURCHASE_RECEIPT.name())
                        .header("Authorization", bearerFor(ALICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentType").value(ClaimDocumentType.PURCHASE_RECEIPT.name()))
                .andExpect(jsonPath("$.originalFilename").value("receipt.pdf"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long documentId = new ObjectMapper().readTree(uploadJson).get("id").asLong();

        mockMvc.perform(get("/api/me/claims/{id}/documents", claim.getId())
                        .header("Authorization", bearerFor(ALICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].documentType").value(ClaimDocumentType.PURCHASE_RECEIPT.name()));

        mockMvc.perform(get("/api/me/claims/{id}/documents/checklist", claim.getId())
                        .header("Authorization", bearerFor(ALICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].required").value(true))
                .andExpect(jsonPath("$[0].uploaded").value(true))
                .andExpect(jsonPath("$[3].documentType").value(ClaimDocumentType.POLICE_REPORT.name()))
                .andExpect(jsonPath("$[3].required").value(false));

        mockMvc.perform(delete("/api/me/claims/{id}/documents/{documentId}", claim.getId(), documentId)
                        .header("Authorization", bearerFor(ALICE)))
                .andExpect(status().isOk());
    }

    @Test
    void member_cannotAccessAnotherMembersDocuments() throws Exception {
        Claim bobClaim = prefillClaimFor(BOB, "Sony TV", "600.00");

        mockMvc.perform(get("/api/me/claims/{id}/documents", bobClaim.getId())
                        .header("Authorization", bearerFor(ALICE)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/me/claims/{id}/documents/checklist", bobClaim.getId())
                        .header("Authorization", bearerFor(ALICE)))
                .andExpect(status().isForbidden());
    }

    @Test
    void checklistUsesBenefitTypeRequiredAndOptionalDefinitions() throws Exception {
        Claim returnClaim = prefillClaimFor(ALICE, "Headphones", "300.00", BenefitType.RETURN_PROTECTION);
        mockMvc.perform(get("/api/me/claims/{id}/documents/checklist", returnClaim.getId())
                        .header("Authorization", bearerFor(ALICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].documentType").value(ClaimDocumentType.PURCHASE_RECEIPT.name()))
                .andExpect(jsonPath("$[0].required").value(true))
                .andExpect(jsonPath("$[1].documentType").value(ClaimDocumentType.MERCHANT_RETURN_DENIAL.name()))
                .andExpect(jsonPath("$[1].required").value(true))
                .andExpect(jsonPath("$[2].required").value(false));

        Claim travelClaim = prefillClaimFor(ALICE, "Flight", "300.00", BenefitType.TRAVEL_DELAY);
        mockMvc.perform(get("/api/me/claims/{id}/documents/checklist", travelClaim.getId())
                        .header("Authorization", bearerFor(ALICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].documentType").value(ClaimDocumentType.AIRLINE_BOOKING_PROOF.name()))
                .andExpect(jsonPath("$[0].required").value(true))
                .andExpect(jsonPath("$[1].documentType").value(ClaimDocumentType.AIRLINE_DELAY_CERTIFICATE.name()))
                .andExpect(jsonPath("$[1].required").value(true))
                .andExpect(jsonPath("$[2].required").value(false));
    }

    @Test
    void unauthenticatedMemberDocumentAccess_isRejected() throws Exception {
        Claim claim = prefillClaimFor(ALICE, "MacBook Pro 14", "499.99");

        mockMvc.perform(get("/api/me/claims/{id}/documents", claim.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void reviewer_canAccessDocumentsAndAuditTrail() throws Exception {
        Claim claim = prefillClaimFor(ALICE, "MacBook Pro 14", "499.99");
        ClaimDocumentTestSupport.uploadRequiredDocuments(claimDocumentService, claim);
        workflowService.submit(claim.getId());

        mockMvc.perform(get("/api/admin/claims/{id}/documents", claim.getId())
                        .header("Authorization", reviewerBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].required").value(true));

        Long documentId = claimDocumentService.listForClaim(claim.getId()).get(0).id();
        mockMvc.perform(get("/api/admin/claims/{id}/documents/{documentId}/content", claim.getId(), documentId)
                        .header("Authorization", reviewerBearer()))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .contentType("application/pdf"));

        mockMvc.perform(get("/api/claims/{id}/audit", claim.getId())
                        .header("Authorization", reviewerBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].toStatus").value("SUBMITTED"))
                .andExpect(jsonPath("$[2].actor").value("BANK"));
    }

    @Test
    void invalidFileType_andOversizedFiles_areRejected() throws Exception {
        Claim claim = prefillClaimFor(ALICE, "MacBook Pro 14", "499.99");

        MockMultipartFile badContent = new MockMultipartFile(
                "file",
                "receipt.exe",
                "application/x-msdownload",
                "bad".getBytes()
        );

        mockMvc.perform(multipart("/api/me/claims/{id}/documents", claim.getId())
                        .file(badContent)
                        .param("documentType", ClaimDocumentType.PURCHASE_RECEIPT.name())
                        .header("Authorization", bearerFor(ALICE)))
                .andExpect(status().isBadRequest());

        byte[] hugeBytes = new byte[6 * 1024 * 1024];
        MockMultipartFile oversized = new MockMultipartFile(
                "file",
                "big.pdf",
                "application/pdf",
                hugeBytes
        );

        mockMvc.perform(multipart("/api/me/claims/{id}/documents", claim.getId())
                        .file(oversized)
                        .param("documentType", ClaimDocumentType.PURCHASE_RECEIPT.name())
                        .header("Authorization", bearerFor(ALICE)))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void requiredDocumentsMissing_blocksSubmission() throws Exception {
        Claim claim = prefillClaimFor(ALICE, "MacBook Pro 14", "499.99");

        mockMvc.perform(post("/api/me/claims/{id}/submit", claim.getId())
                        .header("Authorization", bearerFor(ALICE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("required documents")));
    }

    @Test
    void requiredDocumentPresent_allowsSubmission() throws Exception {
        Claim claim = prefillClaimFor(ALICE, "MacBook Pro 14", "499.99");

        MockMultipartFile receipt = new MockMultipartFile(
                "file",
                "receipt.pdf",
                "application/pdf",
                "fake pdf".getBytes()
        );

        MockMultipartFile proof = new MockMultipartFile(
                "file",
                "proof.pdf",
                "application/pdf",
                "fake proof".getBytes()
        );

        mockMvc.perform(multipart("/api/me/claims/{id}/documents", claim.getId())
                        .file(receipt)
                        .param("documentType", ClaimDocumentType.PURCHASE_RECEIPT.name())
                        .header("Authorization", bearerFor(ALICE)))
                .andExpect(status().isOk());

        mockMvc.perform(multipart("/api/me/claims/{id}/documents", claim.getId())
                        .file(proof)
                        .param("documentType", ClaimDocumentType.PROOF_OF_LOSS.name())
                        .header("Authorization", bearerFor(ALICE)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/me/claims/{id}/submit", claim.getId())
                        .header("Authorization", bearerFor(ALICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
    }
}
