package com.amex.benefit_activation_engine.integration.stripe;

import com.amex.benefit_activation_engine.dto.CreateTransactionRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for the Stripe -> internal mapping: cents conversion, MCC lookup,
 * cardholder/card-product extraction. No Spring context needed.
 */
class StripeTransactionAdapterTest {

    private final StripeTransactionAdapter adapter = new StripeTransactionAdapter(new MccCategoryMapper());

    private StripeWebhookEvent.Authorization authorization() {
        return new StripeWebhookEvent.Authorization(
                "iauth_123",
                49999L,               // cents
                "usd",
                1690000000L,          // epoch seconds
                new StripeWebhookEvent.MerchantData("BEST ELECTRONICS", "electronics_stores", "5732"),
                new StripeWebhookEvent.Card(
                        new StripeWebhookEvent.Cardholder("ich_789", "Jordan Smith"),
                        Map.of("card_product", "platinum"))
        );
    }

    @Test
    void mapsAllFields() {
        CreateTransactionRequest req = adapter.toCreateRequest(authorization());

        assertThat(req.getCardMemberId()).isEqualTo("ich_789");
        assertThat(req.getCardProduct()).isEqualTo("platinum"); // normalization happens later, in ingestion
        assertThat(req.getMerchantName()).isEqualTo("BEST ELECTRONICS");
        assertThat(req.getMerchantCategory()).isEqualTo("ELECTRONICS"); // MCC 5732
        assertThat(req.getAmount()).isEqualByComparingTo(new BigDecimal("499.99")); // 49999 cents
        assertThat(req.getCurrency()).isEqualTo("usd");
        assertThat(req.getDescription()).contains("iauth_123");
    }

    @Test
    void unknownMccFallsBackToStripeCategory() {
        StripeWebhookEvent.Authorization auth = new StripeWebhookEvent.Authorization(
                "iauth_x", 1000L, "usd", 1690000000L,
                new StripeWebhookEvent.MerchantData("Some Shop", "novelty_shop", "9999"),
                new StripeWebhookEvent.Card(new StripeWebhookEvent.Cardholder("ich_1", "A"), Map.of()));

        CreateTransactionRequest req = adapter.toCreateRequest(auth);

        assertThat(req.getMerchantCategory()).isEqualTo("NOVELTY_SHOP"); // fallback, upper-cased
        assertThat(req.getCardProduct()).isNull(); // no card_product metadata
    }
}
