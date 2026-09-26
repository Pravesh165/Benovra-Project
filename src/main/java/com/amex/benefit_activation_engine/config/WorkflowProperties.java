package com.amex.benefit_activation_engine.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Externalized approval-workflow settings, editable in {@code application.yml}
 * under {@code workflow.*} with no code changes.
 *
 * <pre>
 * workflow:
 *   auto-approve-threshold: 700.00
 * </pre>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "workflow")
public class WorkflowProperties {

    /**
     * Claims whose amount is at or below this threshold are auto-approved on
     * submission; anything above it is routed to manual review.
     */
    private BigDecimal autoApproveThreshold = new BigDecimal("700.00");
}
