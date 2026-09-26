package com.amex.benefit_activation_engine.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies GET /api/benefits/{transactionId} returns the ranked, entitled
 * benefit matches for an ingested transaction.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BenefitControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private long ingestElectronicsPlatinum() throws Exception {
        String body = """
                {
                  "cardMemberId": "CM-1001",
                  "cardProduct": "platinum",
                  "merchantName": "Best Electronics",
                  "merchantCategory": "electronics",
                  "amount": 499.99,
                  "currency": "usd",
                  "purchaseDate": "2026-07-20"
                }
                """;
        String json = mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.parse(json).read("$.id", Integer.class).longValue();
    }

    @Test
    void returnsRankedMatchesForTransaction() throws Exception {
        long id = ingestElectronicsPlatinum();

        mockMvc.perform(get("/api/benefits/{id}", id))
                .andExpect(status().isOk())
                // electronics on PLATINUM -> purchase + return, purchase ranked first
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].type").value("PURCHASE_PROTECTION"))
                .andExpect(jsonPath("$[0].estimatedClaimAmount").value(499.99))
                .andExpect(jsonPath("$[0].reason").exists())
                .andExpect(jsonPath("$[1].type").value("RETURN_PROTECTION"))
                // return protection per-claim limit is 300 -> capped estimate
                .andExpect(jsonPath("$[1].estimatedClaimAmount").value(300.00));
    }

    @Test
    void unknownTransaction_returns404() throws Exception {
        mockMvc.perform(get("/api/benefits/{id}", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Not found"));
    }
}
