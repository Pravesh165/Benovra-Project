package com.amex.benefit_activation_engine.engine;

import com.amex.benefit_activation_engine.model.Benefit;
import com.amex.benefit_activation_engine.model.BenefitType;
import com.amex.benefit_activation_engine.model.Entitlement;
import com.amex.benefit_activation_engine.model.Transaction;
import com.amex.benefit_activation_engine.repository.EntitlementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Default config-driven matching engine.
 *
 * <p>Algorithm:</p>
 * <ol>
 *   <li><b>Entitlement cross-check</b> — start from the benefits the purchase's
 *       card product is actually entitled to (only those can ever match).</li>
 *   <li><b>Rules</b> — apply the per-type rule to each entitled benefit:
 *     <ul>
 *       <li>Purchase Protection: qualifying category (e.g. ELECTRONICS) AND
 *           within the coverage window. Over-limit purchases still qualify — the
 *           per-claim limit is applied as a payout cap during ranking/pre-fill,
 *           not as an eligibility gate.</li>
 *       <li>Return Protection: qualifying retail category AND within the
 *           coverage window.</li>
 *       <li>Travel-Delay: qualifying travel category. The purchase-date coverage
 *           window is intentionally NOT applied — travel-delay is triggered by a
 *           delay event (hours-based), not days-from-purchase.</li>
 *     </ul>
 *   </li>
 *   <li><b>Ranking</b> — order matches by estimated recoverable amount
 *       (min(amount, per-claim limit)) descending, so the most valuable benefit
 *       is offered first.</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SimpleRuleEngine implements RuleEngine {

    private final EntitlementRepository entitlementRepository;
    private final MatchingProperties matchingProperties;

    private record ScoredBenefit(Benefit benefit, BigDecimal estimatedClaim) {
    }

    @Override
    public List<Benefit> match(Transaction transaction) {
        if (transaction == null
                || transaction.getCardProduct() == null
                || transaction.getMerchantCategory() == null
                || transaction.getAmount() == null) {
            return List.of();
        }

        List<Entitlement> entitlements =
                entitlementRepository.findByCardProductAndActiveTrue(transaction.getCardProduct());

        List<ScoredBenefit> matches = new ArrayList<>();
        for (Entitlement entitlement : entitlements) {
            Benefit benefit = entitlement.getBenefit();
            if (benefit == null || !benefit.isActive()) {
                continue;
            }
            if (ruleApplies(benefit, transaction)) {
                matches.add(new ScoredBenefit(benefit, estimatedClaim(benefit, transaction)));
            }
        }

        List<Benefit> ranked = matches.stream()
                .sorted(Comparator.comparing(ScoredBenefit::estimatedClaim).reversed()
                        .thenComparing(sb -> sb.benefit().getType().name()))
                .map(ScoredBenefit::benefit)
                .toList();

        log.debug("Matched {} benefit(s) for transaction {} (cardProduct={}, category={})",
                ranked.size(), transaction.getId(), transaction.getCardProduct(),
                transaction.getMerchantCategory());
        return ranked;
    }

    /** Applies the benefit-type-specific rule to a candidate benefit. */
    private boolean ruleApplies(Benefit benefit, Transaction txn) {
        BenefitType type = benefit.getType();
        if (!matchingProperties.categoryQualifies(type, txn.getMerchantCategory())) {
            return false;
        }
        return switch (type) {
            // 🛡️ Electronics/appliances within coverage window; over-limit purchases
            // still match — the per-claim limit is applied later as a payout cap.
            case PURCHASE_PROTECTION -> withinCoverageWindow(benefit, txn);
            // 📦 Retail purchase within the return coverage window.
            case RETURN_PROTECTION -> withinCoverageWindow(benefit, txn);
            // ✈️ Travel purchase; window is event/hours-based, not days-from-purchase (Phase-1 flag).
            case TRAVEL_DELAY -> true;
        };
    }

    private boolean withinCoverageWindow(Benefit benefit, Transaction txn) {
        if (benefit.getCoverageWindowDays() == null || txn.getPurchaseDate() == null) {
            return true;
        }
        LocalDate earliestCovered = LocalDate.now().minusDays(benefit.getCoverageWindowDays());
        return !txn.getPurchaseDate().isBefore(earliestCovered);
    }

    private BigDecimal estimatedClaim(Benefit benefit, Transaction txn) {
        if (benefit.getPerClaimLimit() == null) {
            return txn.getAmount();
        }
        return txn.getAmount().min(benefit.getPerClaimLimit());
    }
}
