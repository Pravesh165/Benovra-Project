package com.amex.benefit_activation_engine.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring's scheduling support so the {@code LocalScheduledFeed} can
 * drip demo purchases via {@code @Scheduled}. Harmless when no scheduled beans
 * are active (e.g. {@code feed.type=none}).
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
