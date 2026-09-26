package com.amex.benefit_activation_engine.integration.feed;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the feed flag selects the local-scheduled mechanism and excludes the
 * Pub/Sub one. The initial delay is set very high so no drip fires during the test.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "feed.type=local-scheduled",
        "feed.local.initial-delay-ms=3600000"
})
class FeedSelectionLocalTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void onlyLocalScheduledFeedIsActive() {
        assertThat(context.getBeansOfType(LocalScheduledFeed.class)).isNotEmpty();
        assertThat(context.getBeansOfType(PubSubFeed.class)).isEmpty();
    }
}
