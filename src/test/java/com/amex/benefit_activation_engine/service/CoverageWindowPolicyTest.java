package com.amex.benefit_activation_engine.service;

import com.amex.benefit_activation_engine.model.Benefit;
import com.amex.benefit_activation_engine.model.BenefitType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class CoverageWindowPolicyTest {

    private final CoverageWindowPolicy policy = new CoverageWindowPolicy();

    @Test
    void purchaseCoverageIncludesExpiryDateButNotFollowingDay() {
        Benefit benefit = Benefit.builder()
                .type(BenefitType.PURCHASE_PROTECTION)
                .coverageWindowDays(90)
                .build();
        LocalDate purchaseDate = LocalDate.of(2026, 1, 1);
        LocalDate expiresOn = purchaseDate.plusDays(90);

        assertThat(policy.expiresOn(benefit, purchaseDate)).isEqualTo(expiresOn);
        assertThat(policy.isWithinWindow(benefit, purchaseDate, expiresOn)).isTrue();
        assertThat(policy.isWithinWindow(benefit, purchaseDate, expiresOn.plusDays(1))).isFalse();
    }

    @Test
    void travelDelayDoesNotUsePurchaseDateCoverageWindow() {
        Benefit benefit = Benefit.builder()
                .type(BenefitType.TRAVEL_DELAY)
                .coverageWindowDays(1)
                .build();

        assertThat(policy.expiresOn(benefit, LocalDate.of(2020, 1, 1))).isNull();
        assertThat(policy.isWithinWindow(benefit, LocalDate.of(2020, 1, 1), LocalDate.of(2026, 10, 3))).isTrue();
    }
}
