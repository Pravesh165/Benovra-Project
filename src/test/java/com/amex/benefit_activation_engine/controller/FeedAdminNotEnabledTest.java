package com.amex.benefit_activation_engine.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * With the default test config ({@code feed.type=none}) the LocalScheduledFeed
 * bean is absent, so the admin endpoints must report NOT_ENABLED and refuse
 * pause/resume with 409 rather than failing.
 */
@SpringBootTest
@AutoConfigureMockMvc
class FeedAdminNotEnabledTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void status_reportsNotEnabled() throws Exception {
        mockMvc.perform(get("/api/admin/feed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("NOT_ENABLED"));
    }

    @Test
    void pause_whenFeedNotEnabled_returns409() throws Exception {
        mockMvc.perform(post("/api/admin/feed/pause"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.state").value("NOT_ENABLED"));
    }
}
