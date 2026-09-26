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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * An ingested card purchase. This is the input the engine WATCHes and then
 * attempts to MATCH against the card member's benefit entitlements.
 */
@Entity
@Table(name = "card_transaction")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Identifier of the card member who made the purchase. */
    @NotBlank
    @Column(name = "card_member_id", nullable = false, length = 60)
    private String cardMemberId;

    /**
     * Card product code used for the purchase (e.g. PLATINUM). Drives which
     * benefit entitlements the transaction is evaluated against.
     */
    @NotBlank
    @Column(name = "card_product", nullable = false, length = 60)
    private String cardProduct;

    @NotBlank
    @Column(name = "merchant_name", nullable = false, length = 160)
    private String merchantName;

    /** Merchant category (e.g. ELECTRONICS, AIRLINE) used for matching. */
    @Column(name = "merchant_category", length = 80)
    private String merchantCategory;

    @NotNull
    @Positive
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @NotBlank
    @Column(nullable = false, length = 3)
    private String currency;

    /** Date the purchase was made; the coverage window is measured from here. */
    @NotNull
    @Column(name = "purchase_date", nullable = false)
    private LocalDate purchaseDate;

    /** Free-text detail (e.g. item description or travel itinerary note). */
    @Column(length = 500)
    private String description;

    /** Processing state in the WATCH -> MATCH pipeline. */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TransactionStatus status = TransactionStatus.RECEIVED;

    /** When the transaction was ingested. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
