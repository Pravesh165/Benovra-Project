package com.amex.benefit_activation_engine.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the feed admin API toggles the drip at runtime. The feed is enabled
 * but starts paused, and the timer's initial delay is set huge so the real
 * scheduled tick never fires during the test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "feed.type=local-scheduled",
        "feed.local.start-paused=true",
        "feed.local.initial-delay-ms=3600000",
        "feed.local.interval-ms=3600000"
})
class FeedAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void pauseResumeTogglesFeedState() throws Exception {
        // Boots paused (start-paused=true).
        mockMvc.perform(get("/api/admin/feed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PAUSED"))
                .andExpect(jsonPath("$.feedType").value("local-scheduled"));

        // Resume -> RUNNING.
        mockMvc.perform(post("/api/admin/feed/resume"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("RUNNING"));

        mockMvc.perform(get("/api/admin/feed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("RUNNING"));

        // Pause -> PAUSED.
        mockMvc.perform(post("/api/admin/feed/pause"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PAUSED"));
    }
}
