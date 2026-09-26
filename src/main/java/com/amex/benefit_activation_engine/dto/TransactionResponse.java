package com.amex.benefit_activation_engine.dto;

import com.amex.benefit_activation_engine.model.Transaction;
import com.amex.benefit_activation_engine.model.TransactionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * API view of a {@link Transaction}. Keeps the JPA entity out of the web layer.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionResponse {

    private Long id;
    private String cardMemberId;
    private String cardProduct;
    private String merchantName;
    private String merchantCategory;
    private BigDecimal amount;
    private String currency;
    private LocalDate purchaseDate;
    private String description;
    private TransactionStatus status;
    private Instant createdAt;

    public static TransactionResponse from(Transaction t) {
        return TransactionResponse.builder()
                .id(t.getId())
                .cardMemberId(t.getCardMemberId())
                .cardProduct(t.getCardProduct())
                .merchantName(t.getMerchantName())
                .merchantCategory(t.getMerchantCategory())
                .amount(t.getAmount())
                .currency(t.getCurrency())
                .purchaseDate(t.getPurchaseDate())
                .description(t.getDescription())
                .status(t.getStatus())
                .createdAt(t.getCreatedAt())
                .build();
    }
}
