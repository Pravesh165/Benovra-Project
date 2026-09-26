package com.amex.benefit_activation_engine.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Incoming payload for the manual transaction feed (POST /api/transactions).
 * Bean Validation enforces the core ingestion rules before anything is saved.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateTransactionRequest {

    @NotBlank(message = "cardMemberId is required")
    private String cardMemberId;

    @NotBlank(message = "cardProduct is required")
    private String cardProduct;

    @NotBlank(message = "merchantName is required")
    private String merchantName;

    @NotBlank(message = "merchantCategory is required")
    private String merchantCategory;

    @NotNull(message = "amount is required")
    @Positive(message = "amount must be greater than zero")
    private BigDecimal amount;

    @NotBlank(message = "currency is required")
    @Size(min = 3, max = 3, message = "currency must be a 3-letter ISO code")
    private String currency;

    @NotNull(message = "purchaseDate is required")
    @PastOrPresent(message = "purchaseDate cannot be in the future")
    private LocalDate purchaseDate;

    /** Optional free-text detail (e.g. item description). */
    private String description;
}
