package com.amex.benefit_activation_engine.support;

import com.amex.benefit_activation_engine.model.Claim;
import com.amex.benefit_activation_engine.model.ClaimDocumentType;
import com.amex.benefit_activation_engine.service.ClaimDocumentService;
import org.springframework.mock.web.MockMultipartFile;

public final class ClaimDocumentTestSupport {

    private ClaimDocumentTestSupport() {
    }

    public static void uploadRequiredDocuments(ClaimDocumentService documentService, Claim claim) {
        uploadRequiredDocuments(documentService, claim, claim.getTransaction().getCardMemberId());
    }

    public static void uploadRequiredDocuments(ClaimDocumentService documentService, Claim claim, String cardMemberId) {
        uploadDocuments(documentService, claim, cardMemberId, documentService.requiredDocumentsFor(claim));
    }

    public static void uploadDocuments(ClaimDocumentService documentService, Claim claim, String cardMemberId,
                                       ClaimDocumentType... documentTypes) {
        uploadDocuments(documentService, claim, cardMemberId, java.util.List.of(documentTypes));
    }

    private static void uploadDocuments(ClaimDocumentService documentService, Claim claim, String cardMemberId,
                                        java.util.List<ClaimDocumentType> documentTypes) {
        for (ClaimDocumentType documentType : documentTypes) {
            documentService.uploadDocument(
                    claim.getId(),
                    cardMemberId,
                    new MockMultipartFile("file", documentType.name() + ".pdf", "application/pdf", new byte[]{1}),
                    documentType);
        }
    }
}
