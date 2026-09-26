package com.amex.benefit_activation_engine.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * An append-only record of a single claim state change, forming an immutable
 * audit trail of the approval workflow. Rows are only ever inserted — never
 * updated or deleted — so the history of a claim is tamper-evident.
 *
 * <p>Deliberately has no setters: once persisted, an event is fixed.</p>
 */
@Entity
@Table(name = "claim_audit_event")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClaimAuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The claim this event belongs to (id kept flat to keep the log independent). */
    @Column(name = "claim_id", nullable = false, updatable = false)
    private Long claimId;

    /** Status before the change (null for the very first event). */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20, updatable = false)
    private ClaimStatus fromStatus;

    /** Status after the change. */
    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20, updatable = false)
    private ClaimStatus toStatus;

    /** Who/what caused the change (e.g. SYSTEM, REVIEWER, BANK). */
    @Column(nullable = false, length = 40, updatable = false)
    private String actor;

    /** Human-readable explanation of why the change happened. */
    @Column(length = 500, updatable = false)
    private String detail;

    /** When the change was recorded. */
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @PrePersist
    void onCreate() {
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
    }
}
