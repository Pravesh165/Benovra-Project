package com.amex.benefit_activation_engine.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Associates a card product (e.g. {@code PLATINUM}) with a {@link Benefit} it
 * grants. The matching engine only pre-fills claims for benefits a purchase's
 * card product is entitled to.
 */
@Entity
@Table(
        name = "entitlement",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_entitlement_card_product_benefit",
                columnNames = {"card_product", "benefit_id"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Entitlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Card product code the entitlement applies to (e.g. PLATINUM, GOLD). */
    @NotBlank
    @Column(name = "card_product", nullable = false, length = 60)
    private String cardProduct;

    /** The benefit granted to the card product. */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "benefit_id", nullable = false)
    private Benefit benefit;

    /** Whether this entitlement is currently active. */
    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;
}
