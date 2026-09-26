package com.amex.benefit_activation_engine.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies GET /api/entitlements returns the seeded card-product entitlements
 * and supports filtering by cardProduct.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EntitlementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listsAllSeededEntitlements() throws Exception {
        // DataSeeder creates 6: PLATINUM x3, GOLD x2, GREEN x1.
        mockMvc.perform(get("/api/entitlements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6));
    }

    @Test
    void filtersByCardProduct_caseInsensitive() throws Exception {
        // GREEN is entitled to purchase protection only.
        mockMvc.perform(get("/api/entitlements").param("cardProduct", "green"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].cardProduct").value("GREEN"))
                .andExpect(jsonPath("$[0].benefitType").value("PURCHASE_PROTECTION"))
                .andExpect(jsonPath("$[0].perClaimLimit").value(1000.00));
    }
}
