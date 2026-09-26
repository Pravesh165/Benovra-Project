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
 * End-to-end test of the Phase 2a ingestion endpoints through the web layer:
 * validation, normalization, persistence, and error responses.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TransactionIngestionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String VALID_BODY = """
            {
              "cardMemberId": "CM-2001",
              "cardProduct": "platinum",
              "merchantName": "Best Electronics",
              "merchantCategory": "electronics",
              "amount": 499.99,
              "currency": "usd",
              "purchaseDate": "2026-07-20",
              "description": "Wireless headphones"
            }
            """;

    @Test
    void ingest_validRequest_returns201_normalizesCasing_andMarksValidated() throws Exception {
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.status").value("VALIDATED"))
                .andExpect(jsonPath("$.cardProduct").value("PLATINUM"))   // normalized
                .andExpect(jsonPath("$.merchantCategory").value("ELECTRONICS")) // normalized
                .andExpect(jsonPath("$.currency").value("USD"))           // normalized
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void ingest_invalidRequest_returns400_withFieldErrors() throws Exception {
        String invalid = """
                {
                  "cardMemberId": "",
                  "cardProduct": "PLATINUM",
                  "merchantName": "Store",
                  "merchantCategory": "ELECTRONICS",
                  "amount": -5,
                  "currency": "US",
                  "purchaseDate": "2999-01-01"
                }
                """;

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.cardMemberId").exists())
                .andExpect(jsonPath("$.errors.amount").exists())
                .andExpect(jsonPath("$.errors.currency").exists())
                .andExpect(jsonPath("$.errors.purchaseDate").exists());
    }

    @Test
    void list_returnsIngestedTransactions() throws Exception {
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cardMemberId").value("CM-2001"))
                .andExpect(jsonPath("$[0].cardProduct").value("PLATINUM"));
    }

    @Test
    void getById_unknownId_returns404() throws Exception {
        mockMvc.perform(get("/api/transactions/{id}", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Not found"));
    }
}
