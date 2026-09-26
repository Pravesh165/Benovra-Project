package com.amex.benefit_activation_engine.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;

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
class StripeWebhookIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private String sampleEvent() throws Exception {
        return StreamUtils.copyToString(
                new ClassPathResource("stripe/issuing_authorization_sample.json").getInputStream(),
                StandardCharsets.UTF_8);
    }

    @Test
    void authorizationEvent_isMappedAndIngested() throws Exception {
        mockMvc.perform(post("/api/stripe/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sampleEvent()))
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

        mockMvc.perform(post("/api/stripe/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
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

        mockMvc.perform(post("/api/stripe/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(incomplete))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").exists());
    }
}
