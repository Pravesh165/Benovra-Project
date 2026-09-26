package com.amex.benefit_activation_engine.integration.stripe;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Minimal, lenient representation of a Stripe Issuing webhook event
 * (e.g. {@code issuing_authorization.created}). Only the fields the engine
 * needs are mapped; {@code @JsonIgnoreProperties(ignoreUnknown = true)} lets the
 * rest of Stripe's rich payload pass through harmlessly, so real Stripe JSON
 * can be posted as-is.
 *
 * <p>Monetary {@code amount} is in the currency's minor unit (cents), and
 * {@code created} is epoch seconds — both Stripe conventions.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StripeWebhookEvent(
        String id,
        String type,
        Data data
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(Authorization object) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Authorization(
            String id,
            Long amount,
            String currency,
            Long created,
            @JsonProperty("merchant_data") MerchantData merchantData,
            Card card
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MerchantData(
            String name,
            String category,
            @JsonProperty("category_code") String categoryCode
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Card(
            Cardholder cardholder,
            Map<String, String> metadata
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Cardholder(String id, String name) {
    }
}
