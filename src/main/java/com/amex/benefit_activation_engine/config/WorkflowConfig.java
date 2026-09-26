package com.amex.benefit_activation_engine.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Binds {@link WorkflowProperties} so the approval workflow's auto-approve
 * threshold is configurable via {@code application.yml}.
 */
@Configuration
@EnableConfigurationProperties(WorkflowProperties.class)
public class WorkflowConfig {
}
