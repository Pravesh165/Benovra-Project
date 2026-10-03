package com.amex.benefit_activation_engine.controller;

import com.amex.benefit_activation_engine.dto.ClaimDocumentResponse;
import com.amex.benefit_activation_engine.dto.ClaimDocumentChecklistItem;
import com.amex.benefit_activation_engine.dto.ClaimResponse;
import com.amex.benefit_activation_engine.dto.UpdateClaimDraftRequest;
import com.amex.benefit_activation_engine.model.ClaimDocument;
import com.amex.benefit_activation_engine.model.ClaimDocumentType;
import com.amex.benefit_activation_engine.model.ClaimStatus;
import com.amex.benefit_activation_engine.service.ClaimDocumentService;
import com.amex.benefit_activation_engine.service.ClaimExpirationService;
import com.amex.benefit_activation_engine.service.ClaimService;
import com.amex.benefit_activation_engine.service.ClaimWorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Customer-facing, per-member claims API. Every endpoint is scoped to the
 * authenticated card member (the JWT subject), so a member only ever sees and
 * acts on their own claims.
 *
 * <ul>
 *   <li>{@code GET /api/me/claims?status=PREFILLED} — "Claims to make"
 *       (drop the filter, or use other statuses, for "Submitted claims").</li>
 *   <li>{@code PUT /api/me/claims/{id}} — edit draft claim details before submit.</li>
 *   <li>{@code POST /api/me/claims/{id}/submit} — submit one of the member's own
 *       pre-filled claims (403 if the claim belongs to someone else).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/me/claims")
@RequiredArgsConstructor
@Tag(name = "My Claims", description = "Per-card-member claims for the customer UI")
public class MeClaimController {

    private final ClaimService claimService;
    private final ClaimWorkflowService workflowService;
    private final ClaimDocumentService claimDocumentService;
        private final ClaimExpirationService claimExpirationService;

    @GetMapping
    @Operation(summary = "List my claims",
            description = "Returns the authenticated card member's claims, optionally filtered by "
                    + "status (e.g. PREFILLED for claims still to submit). Newest first.")
    public List<ClaimResponse> myClaims(@RequestParam(required = false) ClaimStatus status,
                                        Authentication authentication) {
        String cardMemberId = authentication.getName();
        return claimService.findForCardMember(cardMemberId, status).stream()
                .map(ClaimResponse::from)
                .toList();
    }

    @GetMapping("/expiring")
    @Operation(summary = "List claims nearing coverage expiration",
            description = "Returns the member's unsubmitted claims expiring within the requested day window.")
    public List<ClaimResponse> expiringClaims(
            @RequestParam(defaultValue = "7") int withinDays,
            Authentication authentication) {
        return claimExpirationService.findExpiringForMember(authentication.getName(), withinDays).stream()
                .map(ClaimResponse::from)
                .toList();
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a draft claim",
            description = "Updates editable details (item description, incident date, incident explanation) "
                    + "of a PREFILLED claim owned by the authenticated card member. Returns 403 if owned by another member.")
    public ClaimResponse updateDraft(@PathVariable Long id,
                                     @Valid @RequestBody UpdateClaimDraftRequest request,
                                     Authentication authentication) {
        String cardMemberId = authentication.getName();
        claimService.getOwnedBy(id, cardMemberId); // 404 if unknown, 403 if not owned
        return ClaimResponse.from(claimService.updateDraft(
                id,
                request.itemDescription(),
                request.incidentDate(),
                request.incidentExplanation(),
                request.additionalFields()
        ));
    }

    @PostMapping("/{id}/switch-benefit")
    @Operation(summary = "Switch a claim to an alternative benefit",
            description = "Switches a PREFILLED claim to another benefit that the purchase qualifies for. "
                    + "Returns 403 if owned by another member, or 409 if the benefit is not entitled.")
    public ClaimResponse switchBenefit(@PathVariable Long id,
                                       @RequestParam Long benefitId,
                                       Authentication authentication) {
        String cardMemberId = authentication.getName();
        claimService.getOwnedBy(id, cardMemberId); // 404 if unknown, 403 if not owned
        return ClaimResponse.from(claimService.switchBenefit(id, benefitId));
    }

    @PostMapping("/{id}/submit")
    @Operation(summary = "Submit one of my claims",
            description = "Submits a PREFILLED claim owned by the authenticated card member. "
                    + "Returns 403 if the claim belongs to another member.")
    public ClaimResponse submit(@PathVariable Long id, Authentication authentication) {
        String cardMemberId = authentication.getName();
        claimService.getOwnedBy(id, cardMemberId); // 404 if unknown, 403 if not owned
        return ClaimResponse.from(workflowService.submit(id));
    }

    @PostMapping(value = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload supporting document",
            description = "Uploads a supporting document for one of the authenticated member's claims. "
                    + "Files are validated by type and size, then stored in a local filesystem-backed mock store.")
    public ClaimDocumentResponse uploadDocument(@PathVariable Long id,
                                              @RequestParam ClaimDocumentType documentType,
                                              @RequestPart("file") MultipartFile file,
                                              Authentication authentication) {
        String cardMemberId = authentication.getName();
        ClaimDocument saved = claimDocumentService.uploadDocument(id, cardMemberId, file, documentType);
        return ClaimDocumentResponse.from(saved);
    }

    @GetMapping("/{id}/documents")
    @Operation(summary = "List my claim documents",
            description = "Returns the uploaded supporting documents for a claim owned by the authenticated member.")
    public List<ClaimDocumentResponse> listDocuments(@PathVariable Long id,
                                                     Authentication authentication) {
        String cardMemberId = authentication.getName();
        claimDocumentService.requireOwnedClaim(id, cardMemberId);
        return claimDocumentService.listForClaim(id);
    }

        @GetMapping("/{id}/documents/checklist")
        @Operation(summary = "Get supporting document checklist",
                        description = "Returns required and optional document types for the claim benefit, with upload state.")
        public List<ClaimDocumentChecklistItem> documentChecklist(@PathVariable Long id,
                                                                                                                           Authentication authentication) {
                String cardMemberId = authentication.getName();
                claimDocumentService.requireOwnedClaim(id, cardMemberId);
                return claimDocumentService.checklistForClaim(id);
        }

    @DeleteMapping("/{id}/documents/{documentId}")
    @Operation(summary = "Delete a supporting document",
            description = "Deletes a supporting document from a claim owned by the authenticated member.")
    public void deleteDocument(@PathVariable Long id,
                              @PathVariable Long documentId,
                              Authentication authentication) {
        String cardMemberId = authentication.getName();
        claimDocumentService.deleteDocument(id, cardMemberId, documentId);
    }

    @GetMapping("/{id}/documents/{documentId}/content")
    @Operation(summary = "Fetch a document file for a claim",
            description = "Returns the stored document bytes for an authenticated member-owned claim.")
    public ResponseEntity<byte[]> getDocumentContent(@PathVariable Long id,
                                                   @PathVariable Long documentId,
                                                   Authentication authentication) {
        String cardMemberId = authentication.getName();
        claimDocumentService.requireOwnedClaim(id, cardMemberId);
        ClaimDocument document = claimDocumentService.getDocumentForClaim(id, documentId);
        return ResponseEntity.ok()
                .header("Content-Type", document.getContentType())
                .header("Content-Disposition", "inline; filename=\"" + document.getOriginalFilename() + "\"")
                .body(claimDocumentService.readDocumentBytes(id, documentId));
    }
}
