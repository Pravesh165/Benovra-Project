package com.amex.benefit_activation_engine.engine;

import com.amex.benefit_activation_engine.model.Benefit;
import com.amex.benefit_activation_engine.model.Transaction;

import java.util.List;

/**
 * Swappable matching strategy: given a purchase, return the protection benefits
 * it qualifies for.
 *
 * <p>Results are already cross-checked against the card product's entitlements
 * (only benefits the card actually has are returned) and ranked best-first.
 * The default implementation is {@link SimpleRuleEngine}; alternative engines
 * (ML-based, external service, etc.) can be dropped in without touching callers.</p>
 */
public interface RuleEngine {

    /**
     * @param transaction the purchase to evaluate
     * @return entitled, ranked matching benefits; empty when nothing qualifies
     */
    List<Benefit> match(Transaction transaction);
}
