package com.amex.benefit_activation_engine.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.HexFormat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end test of the Stripe webhook: a realistic Stripe Issuing
 * authorization event is posted and flows through the ingestion pipeline.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "stripe.webhook.secret=whsec_test_secret")
class StripeWebhookIntegrationTest {

        private static final String TEST_SECRET = "whsec_test_secret";

    @Autowired
    private MockMvc mockMvc;

    private String sampleEvent() throws Exception {
        return StreamUtils.copyToString(
                new ClassPathResource("stripe/issuing_authorization_sample.json").getInputStream(),
                StandardCharsets.UTF_8);
    }

        private String signatureFor(String payload, long timestamp) throws Exception {
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(TEST_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
                byte[] digest = mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8));
                return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(digest);
        }

    @Test
    void authorizationEvent_isMappedAndIngested() throws Exception {
                String payload = sampleEvent();
                long timestamp = Instant.now().getEpochSecond();
        mockMvc.perform(post("/api/stripe/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                                                .header("Stripe-Signature", signatureFor(payload, timestamp))
                                                .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.cardMemberId").value("ich_1PtEST3333333333333333"))
                .andExpect(jsonPath("$.cardProduct").value("PLATINUM"))      // metadata platinum, normalized
                .andExpect(jsonPath("$.merchantName").value("BEST ELECTRONICS"))
                .andExpect(jsonPath("$.merchantCategory").value("ELECTRONICS")) // MCC 5732
                .andExpect(jsonPath("$.amount").value(499.99))               // 49999 cents
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.status").value("VALIDATED"));
    }

    @Test
    void nonAuthorizationEvent_isAcknowledgedAndIgnored() throws Exception {
        String otherEvent = """
                { "id": "evt_x", "type": "issuing_card.created", "data": { "object": {} } }
                """;
        long timestamp = Instant.now().getEpochSecond();

        mockMvc.perform(post("/api/stripe/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", signatureFor(otherEvent, timestamp))
                        .content(otherEvent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.received").value(true))
                .andExpect(jsonPath("$.processed").value(false));
    }

    @Test
    void authorizationMissingRequiredFields_returns400() throws Exception {
        // issuing_authorization event but with an empty authorization object
        // (no amount, merchant, card) -> mapped request fails validation.
        String incomplete = """
                { "id": "evt_y", "type": "issuing_authorization.created",
                  "data": { "object": { "id": "iauth_empty" } } }
                """;
        long timestamp = Instant.now().getEpochSecond();

        mockMvc.perform(post("/api/stripe/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", signatureFor(incomplete, timestamp))
                        .content(incomplete))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").exists());
    }

    @Test
    void missingSignature_isRejected() throws Exception {
        mockMvc.perform(post("/api/stripe/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sampleEvent()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidOrExpiredSignature_isRejected() throws Exception {
        String payload = sampleEvent();
        long staleTimestamp = Instant.now().minusSeconds(301).getEpochSecond();

        mockMvc.perform(post("/api/stripe/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", signatureFor(payload, Instant.now().getEpochSecond()).replaceAll("v1=.*", "v1=" + "0".repeat(64)))
                        .content(payload))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/stripe/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", signatureFor(payload, staleTimestamp))
                        .content(payload))
                .andExpect(status().isBadRequest());
    }
}
