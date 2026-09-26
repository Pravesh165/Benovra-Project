package com.amex.benefit_activation_engine.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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

/**
 * A card protection benefit definition (e.g. Purchase Protection) together with
 * the coverage rules the matching engine evaluates a purchase against.
 */
@Entity
@Table(name = "benefit")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Benefit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The kind of protection this benefit provides. */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private BenefitType type;

    /** Human-readable benefit name shown on claims and docs. */
    @NotBlank
    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 500)
    private String description;

    /** Maximum amount reimbursable for a single claim under this benefit. */
    @NotNull
    @Positive
    @Column(name = "per_claim_limit", nullable = false, precision = 12, scale = 2)
    private BigDecimal perClaimLimit;

    /**
     * Number of days after the purchase date during which the benefit applies
     * (e.g. purchase protection often covers 90-120 days).
     */
    @NotNull
    @Positive
    @Column(name = "coverage_window_days", nullable = false)
    private Integer coverageWindowDays;

    /** Whether this benefit is currently offered. */
    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;
}
