package com.amex.benefit_activation_engine.model;

import jakarta.persistence.Column;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A pre-filled benefit claim generated when a {@link Transaction} matches a
 * {@link Benefit}. Carries the reimbursement amount and moves through the
 * {@link ClaimStatus} approval workflow.
 */
@Entity
@Table(name = "claim")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Claim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The purchase this claim is filed against. */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    /** The benefit under which reimbursement is claimed. */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "benefit_id", nullable = false)
    private Benefit benefit;

    /** Amount requested for reimbursement (capped by the benefit's per-claim limit). */
    @NotNull
    @Positive
    @Column(name = "claim_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal claimAmount;

    /** Current workflow state. */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ClaimStatus status = ClaimStatus.PREFILLED;

    /**
     * Benefit-specific fields auto-populated by the PRE-FILL stage (e.g.
     * item description, incident type, travel provider). The exact keys depend
     * on the {@link Benefit#getType()} — see the claim service's field mapping.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "claim_prefilled_data",
            joinColumns = @JoinColumn(name = "claim_id")
    )
    @MapKeyColumn(name = "field_key", length = 60)
    @Column(name = "field_value", length = 500)
    @Builder.Default
    private Map<String, String> prefilledData = new LinkedHashMap<>();

    /** Explanation for an approval or rejection decision. */
    @Column(name = "decision_reason", length = 500)
    private String decisionReason;

    /** Bank/ledger reference returned when an approved claim is disbursed (null until PAID). */
    @Column(name = "payout_reference", length = 60)
    private String payoutReference;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** When the claim was submitted for processing (null while PREFILLED). */
    @Column(name = "submitted_at")
    private Instant submittedAt;

    /** When a final decision (APPROVED/REJECTED/PAID) was recorded. */
    @Column(name = "decided_at")
    private Instant decidedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
