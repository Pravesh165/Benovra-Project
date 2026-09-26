package com.amex.benefit_activation_engine.integration.stripe;

import com.amex.benefit_activation_engine.dto.CreateTransactionRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

/**
 * Translates a Stripe Issuing authorization into the engine's internal
 * {@link CreateTransactionRequest}, which is then fed through the normal
 * ingestion pipeline. This adapter is the ONLY Stripe-aware code on the write
 * path — swapping Stripe test mode for live requires no downstream changes.
 */
@Component
@RequiredArgsConstructor
public class StripeTransactionAdapter {

    /** Card metadata key that carries the card product code (e.g. PLATINUM). */
    static final String CARD_PRODUCT_METADATA_KEY = "card_product";

    private final MccCategoryMapper mccCategoryMapper;

    public CreateTransactionRequest toCreateRequest(StripeWebhookEvent.Authorization auth) {
        StripeWebhookEvent.MerchantData merchant = auth.merchantData();
        StripeWebhookEvent.Card card = auth.card();

        return CreateTransactionRequest.builder()
                .cardMemberId(cardholderId(card))
                .cardProduct(cardProduct(card))
                .merchantName(merchant != null ? merchant.name() : null)
                .merchantCategory(mccCategoryMapper.categoryFor(
                        merchant != null ? merchant.categoryCode() : null,
                        merchant != null ? merchant.category() : null))
                .amount(minorUnitsToDecimal(auth.amount()))
                .currency(auth.currency())
                .purchaseDate(toPurchaseDate(auth.created()))
                .description("Stripe issuing authorization " + auth.id())
                .build();
    }

    /** Stripe amounts are in the currency's minor unit (cents); convert to a decimal. */
    private BigDecimal minorUnitsToDecimal(Long minorUnits) {
        return minorUnits == null ? null : BigDecimal.valueOf(minorUnits).movePointLeft(2);
    }

    private LocalDate toPurchaseDate(Long epochSeconds) {
        if (epochSeconds == null) {
            return LocalDate.now();
        }
        return Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private String cardProduct(StripeWebhookEvent.Card card) {
        if (card == null || card.metadata() == null) {
            return null;
        }
        Map<String, String> metadata = card.metadata();
        return metadata.get(CARD_PRODUCT_METADATA_KEY);
    }

    private String cardholderId(StripeWebhookEvent.Card card) {
        if (card == null || card.cardholder() == null) {
            return null;
        }
        return card.cardholder().id();
    }
}
