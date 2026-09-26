package com.amex.benefit_activation_engine.config;

import com.amex.benefit_activation_engine.engine.MatchingProperties;
import com.amex.benefit_activation_engine.engine.RuleEngine;
import com.amex.benefit_activation_engine.model.Benefit;
import com.amex.benefit_activation_engine.model.Transaction;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.function.Function;

/**
 * Wires up the matching layer: binds {@link MatchingProperties} and exposes the
 * matching logic as a {@code Function<Transaction, List<Benefit>>} bean.
 *
 * <p>Modeling matching as a plain {@link Function} keeps it framework-agnostic
 * so it can later be lifted into a Spring Cloud Function / AWS Lambda handler
 * with no changes to the core logic.</p>
 */
@Configuration
@EnableConfigurationProperties(MatchingProperties.class)
public class MatchingConfig {

    @Bean
    public Function<Transaction, List<Benefit>> matchingFunction(RuleEngine ruleEngine) {
        return ruleEngine::match;
    }
}
