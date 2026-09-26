package com.amex.benefit_activation_engine.integration.feed;

/**
 * A swappable delivery mechanism that brings purchases into the engine in real
 * time. Implementations turn an external stream of purchases into calls on the
 * ingestion pipeline.
 *
 * <p>Exactly one implementation is active at a time, selected by the
 * {@code feed.type} configuration flag:</p>
 * <ul>
 *   <li>{@code local-scheduled} → {@link LocalScheduledFeed} (demo drip)</li>
 *   <li>{@code pubsub} → {@link PubSubFeed} (production subscriber)</li>
 *   <li>{@code none} → no feed active</li>
 * </ul>
 */
public interface TransactionFeed {

    /** Identifier of this delivery mechanism (e.g. {@code local-scheduled}). */
    String name();

    /** Whether this feed is currently delivering purchases. */
    boolean isActive();
}
