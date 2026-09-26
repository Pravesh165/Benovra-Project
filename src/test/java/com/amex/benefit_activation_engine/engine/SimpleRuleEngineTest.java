package com.amex.benefit_activation_engine.engine;

import com.amex.benefit_activation_engine.model.Benefit;
import com.amex.benefit_activation_engine.model.BenefitType;
import com.amex.benefit_activation_engine.model.Transaction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the matching rules against the seeded benefits/entitlements:
 * category rules, amount limit, coverage window, entitlement cross-check,
 * ranking, no-match, and the travel-delay window exemption.
 */
@SpringBootTest
class SimpleRuleEngineTest {

    @Autowired
    private RuleEngine ruleEngine;

    private Transaction txn(String cardProduct, String category, String amount, LocalDate date) {
        return Transaction.builder()
                .cardMemberId("CM-1001")
                .cardProduct(cardProduct)
                .merchantName("Test Merchant")
                .merchantCategory(category)
                .amount(new BigDecimal(amount))
                .currency("USD")
                .purchaseDate(date)
                .build();
    }

    private List<BenefitType> types(List<Benefit> benefits) {
        return benefits.stream().map(Benefit::getType).toList();
    }

    @Test
    void electronicsOnPlatinum_matchesPurchaseAndReturn_rankedByValue() {
        List<Benefit> matches = ruleEngine.match(txn("PLATINUM", "ELECTRONICS", "500.00", LocalDate.now()));

        // Electronics qualifies for both purchase (limit 1000) and return (limit 300).
        assertThat(types(matches))
                .containsExactly(BenefitType.PURCHASE_PROTECTION, BenefitType.RETURN_PROTECTION);
        // Ranked by estimated recoverable amount: min(500,1000)=500 > min(500,300)=300.
        assertThat(matches.get(0).getType()).isEqualTo(BenefitType.PURCHASE_PROTECTION);
    }

    @Test
    void airlineOnPlatinum_matchesTravelDelayOnly() {
        List<Benefit> matches = ruleEngine.match(txn("PLATINUM", "AIRLINE", "800.00", LocalDate.now()));

        assertThat(types(matches)).containsExactly(BenefitType.TRAVEL_DELAY);
    }

    @Test
    void electronicsOnGreen_onlyPurchaseProtection_dueToEntitlements() {
        // GREEN is entitled to purchase protection only (per DataSeeder).
        List<Benefit> matches = ruleEngine.match(txn("GREEN", "ELECTRONICS", "400.00", LocalDate.now()));

        assertThat(types(matches)).containsExactly(BenefitType.PURCHASE_PROTECTION);
    }

    @Test
    void unknownCardProduct_hasNoEntitlements_soNoMatch() {
        List<Benefit> matches = ruleEngine.match(txn("BLACK", "ELECTRONICS", "400.00", LocalDate.now()));

        assertThat(matches).isEmpty();
    }

    @Test
    void purchaseAmountOverLimit_stillMatchesPurchaseProtection_cappedAtLimit() {
        // 2000 > purchase limit (1000): the limit is a payout cap, not an eligibility
        // gate, so purchase protection still matches (and outranks return protection,
        // since min(2000,1000)=1000 > min(2000,300)=300).
        List<Benefit> matches = ruleEngine.match(txn("PLATINUM", "ELECTRONICS", "2000.00", LocalDate.now()));

        assertThat(types(matches))
                .containsExactly(BenefitType.PURCHASE_PROTECTION, BenefitType.RETURN_PROTECTION);
    }

    @Test
    void travelDelayIgnoresPurchaseDateWindow() {
        // Purchase 200 days ago: travel-delay must still match (event/hours-based, not days-from-purchase).
        List<Benefit> matches = ruleEngine.match(
                txn("PLATINUM", "AIRLINE", "800.00", LocalDate.now().minusDays(200)));

        assertThat(types(matches)).contains(BenefitType.TRAVEL_DELAY);
    }
}
