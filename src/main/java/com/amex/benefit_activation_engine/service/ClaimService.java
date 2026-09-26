package com.amex.benefit_activation_engine.service;

import com.amex.benefit_activation_engine.model.Benefit;
import com.amex.benefit_activation_engine.model.BenefitType;
import com.amex.benefit_activation_engine.model.Claim;
import com.amex.benefit_activation_engine.model.ClaimAuditEvent;
import com.amex.benefit_activation_engine.model.ClaimStatus;
import com.amex.benefit_activation_engine.model.Transaction;
import com.amex.benefit_activation_engine.repository.ClaimRepository;
import com.amex.benefit_activation_engine.repository.ClaimAuditRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PRE-FILL stage (Task 3): turns a matched (transaction, benefit) pair into a
 * ready-to-submit {@link Claim}.
 *
 * <p>Responsibilities:</p>
 * <ol>
 *   <li>Auto-populate the reimbursement amount (capped at the benefit's
 *       per-claim limit) and a benefit-type-specific set of claim fields.</li>
 *   <li>Run a pre-fill quality check so no required field is left blank.</li>
 *   <li>Persist the claim in {@link ClaimStatus#PREFILLED} for the card member
 *       to review and submit.</li>
 * </ol>
 *
 * <p>Generation is idempotent per (transaction, benefit): if a claim already
 * exists it is returned as-is, so re-running detection never duplicates claims.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClaimService {

    private final ClaimRepository claimRepository;
    private final ClaimAuditRepository auditRepository;

    // ---- Pre-filled field keys (stable contract shared with the UI) ----
    static final String FIELD_CARD_MEMBER_ID = "cardMemberId";
    static final String FIELD_BENEFIT_NAME = "benefitName";
    static final String FIELD_BENEFIT_TYPE = "benefitType";
    static final String FIELD_MERCHANT_NAME = "merchantName";
    static final String FIELD_PURCHASE_AMOUNT = "purchaseAmount";
    static final String FIELD_CURRENCY = "currency";
    static final String FIELD_PURCHASE_DATE = "purchaseDate";
    static final String FIELD_CLAIM_AMOUNT = "claimAmount";
    static final String FIELD_ITEM_DESCRIPTION = "itemDescription";
    static final String FIELD_INCIDENT_TYPE = "incidentType";
    static final String FIELD_RETURN_REASON = "returnReason";
    static final String FIELD_RETURN_WINDOW_DAYS = "returnWindowDays";
    static final String FIELD_TRAVEL_PROVIDER = "travelProvider";
    static final String FIELD_TRAVEL_DATE = "travelDate";
    static final String FIELD_MINIMUM_DELAY_HOURS = "minimumDelayHours";
    static final String FIELD_EXPENSE_TYPE = "expenseType";

    /** Fields every claim must carry regardless of benefit type. */
    private static final List<String> COMMON_REQUIRED = List.of(
            FIELD_CARD_MEMBER_ID, FIELD_BENEFIT_NAME, FIELD_MERCHANT_NAME,
            FIELD_PURCHASE_AMOUNT, FIELD_CURRENCY, FIELD_PURCHASE_DATE, FIELD_CLAIM_AMOUNT);

    /** Default minimum delay (hours) that triggers travel-delay coverage. */
    private static final String DEFAULT_MIN_DELAY_HOURS = "6";

    /**
     * Builds and persists a PREFILLED claim for the given matched pair, or
     * returns the existing claim if one was already generated.
     */
    @Transactional
    public Claim generateFor(Transaction transaction, Benefit benefit) {
        if (transaction == null || benefit == null) {
            throw new IllegalArgumentException("transaction and benefit are required to pre-fill a claim");
        }

        Claim existing = findExisting(transaction.getId(), benefit.getId());
        if (existing != null) {
            log.debug("Claim {} already exists for transaction {} / benefit {}; skipping pre-fill",
                    existing.getId(), transaction.getId(), benefit.getId());
            return existing;
        }

        BigDecimal claimAmount = cappedClaimAmount(benefit, transaction);
        Map<String, String> prefilled = buildPrefilledData(transaction, benefit, claimAmount);

        // Pre-fill quality check: refuse to persist an incomplete claim.
        assertPrefillComplete(benefit.getType(), prefilled);

        Claim claim = Claim.builder()
                .transaction(transaction)
                .benefit(benefit)
                .claimAmount(claimAmount)
                .status(ClaimStatus.PREFILLED)
                .prefilledData(prefilled)
                .build();

        Claim saved = claimRepository.save(claim);
        log.info("Pre-filled {} claim {} for transaction {} (amount={} {})",
                benefit.getType(), saved.getId(), transaction.getId(),
                claimAmount, transaction.getCurrency());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Claim> findAll() {
        return claimRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Claim getById(Long id) {
        return claimRepository.findById(id)
                .orElseThrow(() -> new ClaimNotFoundException(id));
    }

    /**
     * Returns a card member's claims (newest first), optionally filtered by
     * status. Used by the customer {@code /api/me/claims} endpoint so a member
     * only ever sees their own claims.
     */
    @Transactional(readOnly = true)
    public List<Claim> findForCardMember(String cardMemberId, ClaimStatus status) {
        if (status == null) {
            return claimRepository.findByTransactionCardMemberIdOrderByCreatedAtDesc(cardMemberId);
        }
        return claimRepository.findByTransactionCardMemberIdAndStatusOrderByCreatedAtDesc(cardMemberId, status);
    }

    /**
     * Loads a claim and verifies it belongs to {@code cardMemberId}.
     *
     * @throws ClaimNotFoundException     if no claim has the given id
     * @throws ClaimAccessDeniedException if the claim belongs to another member
     */
    @Transactional(readOnly = true)
    public Claim getOwnedBy(Long id, String cardMemberId) {
        Claim claim = getById(id);
        Transaction txn = claim.getTransaction();
        if (txn == null || !cardMemberId.equals(txn.getCardMemberId())) {
            throw new ClaimAccessDeniedException(id);
        }
        return claim;
    }

    /**
     * Reviewer listing across all customers with optional filters. Any argument
     * left null/blank is ignored. {@code cardMemberId} matches partially
     * (case-insensitive contains); the other text filters match exactly
     * (case-insensitive). Results are newest first.
     */
    @Transactional(readOnly = true)
    public List<Claim> findForReviewer(ClaimStatus status,
                                       String cardMemberId,
                                       String cardProduct,
                                       String merchantCategory,
                                       BenefitType benefitType) {
        // A reviewer filtering by "Submitted" wants every claim the owner has
        // actually submitted, regardless of where it landed afterwards
        // (under review, approved, rejected, or processed). Everything except
        // a still-PREFILLED claim counts as submitted.
        //
        // "Approved" means every claim a reviewer approved. Because an approval
        // immediately disburses (APPROVED → PAID), those claims no longer sit in
        // APPROVED, so we resolve them from the audit trail (actor REVIEWER →
        // APPROVED). This makes a reviewer-approved claim appear under both
        // "Approved" and "Processed".
        List<Claim> base;
        if (status == null) {
            base = claimRepository.findAll();
        } else if (status == ClaimStatus.SUBMITTED) {
            base = claimRepository.findAll().stream()
                    .filter(c -> c.getStatus() != ClaimStatus.PREFILLED)
                    .toList();
        } else if (status == ClaimStatus.APPROVED) {
            List<Long> reviewerApprovedIds = auditRepository
                    .findByActorAndToStatus("REVIEWER", ClaimStatus.APPROVED).stream()
                    .map(ClaimAuditEvent::getClaimId)
                    .distinct()
                    .toList();
            base = claimRepository.findAllById(reviewerApprovedIds);
        } else {
            base = claimRepository.findByStatus(status);
        }

        String memberQuery = StringUtils.hasText(cardMemberId)
                ? cardMemberId.trim().toLowerCase() : null;

        return base.stream()
                .filter(c -> c.getTransaction() != null)
                .filter(c -> memberQuery == null
                        || c.getTransaction().getCardMemberId() != null
                        && c.getTransaction().getCardMemberId().toLowerCase().contains(memberQuery))
                .filter(c -> !StringUtils.hasText(cardProduct)
                        || cardProduct.equalsIgnoreCase(c.getTransaction().getCardProduct()))
                .filter(c -> !StringUtils.hasText(merchantCategory)
                        || merchantCategory.equalsIgnoreCase(c.getTransaction().getMerchantCategory()))
                .filter(c -> benefitType == null
                        || (c.getBenefit() != null && benefitType == c.getBenefit().getType()))
                .sorted(java.util.Comparator.comparing(
                        Claim::getCreatedAt,
                        java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
                .toList();
    }

    // ------------------------------------------------------------------
    // Pre-fill helpers
    // ------------------------------------------------------------------

    private Claim findExisting(Long transactionId, Long benefitId) {
        if (transactionId == null || benefitId == null) {
            return null;
        }
        return claimRepository.findByTransactionId(transactionId).stream()
                .filter(c -> c.getBenefit() != null && benefitId.equals(c.getBenefit().getId()))
                .findFirst()
                .orElse(null);
    }

    /** Reimbursement amount, capped at the benefit's per-claim limit. */
    private BigDecimal cappedClaimAmount(Benefit benefit, Transaction txn) {
        if (benefit.getPerClaimLimit() == null) {
            return txn.getAmount();
        }
        return txn.getAmount().min(benefit.getPerClaimLimit());
    }

    /**
     * Auto-populates the claim fields. Common fields are always filled; each
     * benefit type contributes its own extra fields.
     */
    private Map<String, String> buildPrefilledData(Transaction txn, Benefit benefit, BigDecimal claimAmount) {
        Map<String, String> data = new LinkedHashMap<>();

        // ---- Common fields ----
        data.put(FIELD_CARD_MEMBER_ID, txn.getCardMemberId());
        data.put(FIELD_BENEFIT_NAME, benefit.getName());
        data.put(FIELD_BENEFIT_TYPE, benefit.getType().name());
        data.put(FIELD_MERCHANT_NAME, txn.getMerchantName());
        data.put(FIELD_PURCHASE_AMOUNT, plain(txn.getAmount()));
        data.put(FIELD_CURRENCY, txn.getCurrency());
        data.put(FIELD_PURCHASE_DATE, txn.getPurchaseDate() == null ? null : txn.getPurchaseDate().toString());
        data.put(FIELD_CLAIM_AMOUNT, plain(claimAmount));

        String itemDescription = describeItem(txn);
        String coverageDays = benefit.getCoverageWindowDays() == null
                ? null : String.valueOf(benefit.getCoverageWindowDays());

        // ---- Benefit-type-specific fields ----
        switch (benefit.getType()) {
            case PURCHASE_PROTECTION -> {
                data.put(FIELD_ITEM_DESCRIPTION, itemDescription);
                data.put(FIELD_INCIDENT_TYPE, "DAMAGE_OR_THEFT");
            }
            case RETURN_PROTECTION -> {
                data.put(FIELD_ITEM_DESCRIPTION, itemDescription);
                data.put(FIELD_RETURN_REASON, "MERCHANT_REFUSED_RETURN");
                data.put(FIELD_RETURN_WINDOW_DAYS, coverageDays);
            }
            case TRAVEL_DELAY -> {
                data.put(FIELD_TRAVEL_PROVIDER, txn.getMerchantName());
                data.put(FIELD_TRAVEL_DATE, txn.getPurchaseDate() == null ? null : txn.getPurchaseDate().toString());
                data.put(FIELD_MINIMUM_DELAY_HOURS, DEFAULT_MIN_DELAY_HOURS);
                data.put(FIELD_EXPENSE_TYPE, "MEALS_AND_LODGING");
            }
        }
        return data;
    }

    /** A never-blank item description, derived from the merchant when free text is absent. */
    private String describeItem(Transaction txn) {
        if (StringUtils.hasText(txn.getDescription())) {
            return txn.getDescription().trim();
        }
        String category = StringUtils.hasText(txn.getMerchantCategory())
                ? txn.getMerchantCategory().toLowerCase() + " purchase"
                : "purchase";
        return category + " at " + txn.getMerchantName();
    }

    /**
     * Pre-fill quality check: verifies every field required for the benefit type
     * is present and non-blank. Throws {@link PrefillIncompleteException} otherwise.
     */
    void assertPrefillComplete(BenefitType type, Map<String, String> data) {
        List<String> required = new ArrayList<>(COMMON_REQUIRED);
        required.addAll(typeSpecificRequired(type));

        List<String> missing = required.stream()
                .filter(key -> !StringUtils.hasText(data.get(key)))
                .toList();

        if (!missing.isEmpty()) {
            throw new PrefillIncompleteException(type.name(), missing);
        }
    }

    private List<String> typeSpecificRequired(BenefitType type) {
        return switch (type) {
            case PURCHASE_PROTECTION -> List.of(FIELD_ITEM_DESCRIPTION, FIELD_INCIDENT_TYPE);
            case RETURN_PROTECTION -> List.of(FIELD_ITEM_DESCRIPTION, FIELD_RETURN_REASON);
            case TRAVEL_DELAY -> List.of(FIELD_TRAVEL_PROVIDER, FIELD_TRAVEL_DATE, FIELD_MINIMUM_DELAY_HOURS);
        };
    }

    private String plain(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }
}
