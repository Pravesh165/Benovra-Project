package com.amex.benefit_activation_engine.service;

import com.amex.benefit_activation_engine.dto.ClaimDocumentResponse;
import com.amex.benefit_activation_engine.dto.ClaimDocumentChecklistItem;
import com.amex.benefit_activation_engine.model.BenefitType;
import com.amex.benefit_activation_engine.model.Claim;
import com.amex.benefit_activation_engine.model.ClaimDocument;
import com.amex.benefit_activation_engine.model.ClaimDocumentType;
import com.amex.benefit_activation_engine.repository.ClaimDocumentRepository;
import com.amex.benefit_activation_engine.repository.ClaimRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ClaimDocumentService {

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "application/pdf",
            "image/png",
            "image/jpeg",
            "image/webp",
            "image/gif"
    );

    private final ClaimDocumentRepository documentRepository;
    private final ClaimRepository claimRepository;

    @Value("${app.documents.storage-dir:./claim-documents}")
    private String storageDir;

    @Value("${app.documents.max-file-size-bytes:5242880}")
    private long maxFileSizeBytes;

    public ClaimDocumentService(ClaimDocumentRepository documentRepository, ClaimRepository claimRepository) {
        this.documentRepository = documentRepository;
        this.claimRepository = claimRepository;
    }

    @Transactional(readOnly = true)
    public List<ClaimDocumentResponse> listForClaim(Long claimId) {
        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Claim not found: " + claimId));
        return documentRepository.findByClaimIdOrderByUploadedAtAscIdAsc(claim.getId()).stream()
                .map(ClaimDocumentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ClaimDocumentType> requiredDocumentsFor(Claim claim) {
        if (claim == null || claim.getBenefit() == null || claim.getBenefit().getType() == null) {
            return List.of();
        }
        return requiredTypesFor(claim.getBenefit().getType());
    }

        @Transactional(readOnly = true)
        public List<ClaimDocumentChecklistItem> checklistForClaim(Long claimId) {
        Claim claim = claimRepository.findById(claimId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Claim not found: " + claimId));
        Set<ClaimDocumentType> uploadedTypes = documentRepository
            .findByClaimIdOrderByUploadedAtAscIdAsc(claimId).stream()
            .map(ClaimDocument::getDocumentType)
            .collect(java.util.stream.Collectors.toSet());
        Set<ClaimDocumentType> requiredTypes = Set.copyOf(requiredDocumentsFor(claim));
        return allowedDocumentTypesFor(claim).stream()
            .map(type -> new ClaimDocumentChecklistItem(
                type,
                requiredTypes.contains(type),
                uploadedTypes.contains(type)))
            .toList();
        }

    @Transactional(readOnly = true)
    public boolean hasRequiredDocuments(Claim claim) {
        if (claim == null || claim.getId() == null) {
            return false;
        }
        List<ClaimDocumentType> missing = requiredDocumentsMissing(claim);
        return missing.isEmpty();
    }

    @Transactional(readOnly = true)
    public List<ClaimDocumentType> requiredDocumentsMissing(Claim claim) {
        if (claim == null || claim.getId() == null) {
            return List.of();
        }
        List<ClaimDocumentType> required = requiredDocumentsFor(claim);
        List<ClaimDocumentType> uploaded = documentRepository.findByClaimIdOrderByUploadedAtAscIdAsc(claim.getId()).stream()
                .map(ClaimDocument::getDocumentType)
                .toList();
        return required.stream()
                .filter(type -> uploaded.stream().noneMatch(type::equals))
                .toList();
    }

    @Transactional
    public ClaimDocument uploadDocument(Long claimId, String cardMemberId, MultipartFile file, ClaimDocumentType documentType) {
        Claim claim = requireOwnedClaim(claimId, cardMemberId);
        validateFile(file, documentType, claim);

        Path claimDir = pathForClaim(claimId);
        try {
            Files.createDirectories(claimDir);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to create document store", ex);
        }

        String originalName = sanitizeFilename(file.getOriginalFilename());
        String storageKey = claimDir.resolve(UUID.randomUUID() + "_" + originalName).toString();
        try {
            Files.write(Path.of(storageKey), file.getBytes());
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to save uploaded document", ex);
        }

        ClaimDocument document = ClaimDocument.builder()
                .claim(claim)
                .documentType(documentType)
                .originalFilename(originalName)
                .contentType(normalizedContentType(file))
                .sizeBytes(file.getSize())
                .storageKey(storageKey)
                .required(requiredDocumentsFor(claim).contains(documentType))
                .build();

        return documentRepository.save(document);
    }

    @Transactional
    public void deleteDocument(Long claimId, String cardMemberId, Long documentId) {
        requireOwnedClaim(claimId, cardMemberId);
        ClaimDocument document = documentRepository.findByIdAndClaimId(documentId, claimId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found: " + documentId));

        documentRepository.delete(document);
        try {
            Files.deleteIfExists(Path.of(document.getStorageKey()));
        } catch (IOException ignored) {
            // best-effort cleanup; the DB record is the source of truth for the document metadata
        }
    }

    @Transactional(readOnly = true)
    public ClaimDocument getDocumentForClaim(Long claimId, Long documentId) {
        return documentRepository.findByIdAndClaimId(documentId, claimId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found: " + documentId));
    }

    @Transactional(readOnly = true)
    public byte[] readDocumentBytes(Long claimId, Long documentId) {
        ClaimDocument document = getDocumentForClaim(claimId, documentId);
        try {
            return Files.readAllBytes(Path.of(document.getStorageKey()));
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document file not available: " + documentId, ex);
        }
    }

    public Claim requireOwnedClaim(Long claimId, String cardMemberId) {
        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Claim not found: " + claimId));
        if (claim.getTransaction() == null || !cardMemberId.equals(claim.getTransaction().getCardMemberId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You may only access your own claim documents.");
        }
        return claim;
    }

    public void assertRequiredDocumentsPresent(Claim claim) {
        List<ClaimDocumentType> missing = requiredDocumentsMissing(claim);
        if (!missing.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Claim submission blocked: required documents missing: " + missing);
        }
    }

    private Path pathForClaim(Long claimId) {
        String base = StringUtils.hasText(storageDir) ? storageDir : "./claim-documents";
        return Paths.get(base, String.valueOf(claimId));
    }

    private void validateFile(MultipartFile file, ClaimDocumentType documentType, Claim claim) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A document file is required.");
        }
        if (documentType == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "documentType is required.");
        }
        if (file.getSize() > maxFileSizeBytes) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "Document exceeds the 5MB limit.");
        }
        String mimeType = normalizedContentType(file);
        if (!ALLOWED_MIME_TYPES.contains(mimeType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unsupported file type. Use PDF, PNG, JPG, GIF, or WEBP.");
        }
        List<ClaimDocumentType> allowed = allowedDocumentTypesFor(claim);
        if (!allowed.contains(documentType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This document type is not valid for the selected benefit.");
        }
    }

    private List<ClaimDocumentType> allowedDocumentTypesFor(Claim claim) {
        return switch (claim.getBenefit().getType()) {
            case PURCHASE_PROTECTION -> List.of(
                    ClaimDocumentType.PURCHASE_RECEIPT,
                    ClaimDocumentType.PROOF_OF_LOSS,
                    ClaimDocumentType.OTHER_SUPPORTING,
                    ClaimDocumentType.POLICE_REPORT
            );
            case RETURN_PROTECTION -> List.of(
                    ClaimDocumentType.PURCHASE_RECEIPT,
                    ClaimDocumentType.MERCHANT_RETURN_DENIAL,
                    ClaimDocumentType.OTHER_SUPPORTING
            );
            case TRAVEL_DELAY -> List.of(
                    ClaimDocumentType.AIRLINE_BOOKING_PROOF,
                    ClaimDocumentType.AIRLINE_DELAY_CERTIFICATE,
                    ClaimDocumentType.OTHER_SUPPORTING
            );
        };
    }

    private List<ClaimDocumentType> requiredTypesFor(BenefitType type) {
        return switch (type) {
            case PURCHASE_PROTECTION -> List.of(
                    ClaimDocumentType.PURCHASE_RECEIPT,
                    ClaimDocumentType.PROOF_OF_LOSS
            );
            case RETURN_PROTECTION -> List.of(
                    ClaimDocumentType.PURCHASE_RECEIPT,
                    ClaimDocumentType.MERCHANT_RETURN_DENIAL
            );
            case TRAVEL_DELAY -> List.of(
                    ClaimDocumentType.AIRLINE_BOOKING_PROOF,
                    ClaimDocumentType.AIRLINE_DELAY_CERTIFICATE
            );
        };
    }

    private String sanitizeFilename(String originalFilename) {
        String safe = originalFilename == null || originalFilename.isBlank() ? "document" : originalFilename;
        safe = safe.replace("\\", "/");
        int slash = safe.lastIndexOf('/');
        int backSlash = safe.lastIndexOf('\\');
        int lastSeparator = Math.max(slash, backSlash);
        String fileName = lastSeparator >= 0 ? safe.substring(lastSeparator + 1) : safe;
        fileName = fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
        return fileName.isBlank() ? "document" : fileName;
    }

    private String normalizedContentType(MultipartFile file) {
        return file.getContentType() == null || file.getContentType().isBlank()
                ? "application/octet-stream"
                : file.getContentType().toLowerCase();
    }
}
