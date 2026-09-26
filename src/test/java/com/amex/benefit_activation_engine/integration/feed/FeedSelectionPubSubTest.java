package com.amex.benefit_activation_engine.integration.feed;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that flipping the single {@code feed.type} flag to {@code pubsub}
 * swaps the active delivery mechanism to the Pub/Sub subscriber.
 */
@SpringBootTest
@TestPropertySource(properties = "feed.type=pubsub")
class FeedSelectionPubSubTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void onlyPubSubFeedIsActive() {
        assertThat(context.getBeansOfType(PubSubFeed.class)).isNotEmpty();
        assertThat(context.getBeansOfType(LocalScheduledFeed.class)).isEmpty();
    }
}
