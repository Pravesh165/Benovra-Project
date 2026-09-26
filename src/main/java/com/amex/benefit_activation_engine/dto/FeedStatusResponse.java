package com.amex.benefit_activation_engine.dto;

/**
 * Current state of the demo transaction feed, returned by the feed admin API.
 *
 * @param state      RUNNING, PAUSED, or NOT_ENABLED (when feed.type is not
 *                   {@code local-scheduled})
 * @param feedType   the configured {@code feed.type}
 * @param intervalMs the drip interval in milliseconds
 */
public record FeedStatusResponse(
        String state,
        String feedType,
        long intervalMs
) {
    public static final String RUNNING = "RUNNING";
    public static final String PAUSED = "PAUSED";
    public static final String NOT_ENABLED = "NOT_ENABLED";
}
