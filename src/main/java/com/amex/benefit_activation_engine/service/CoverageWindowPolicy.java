package com.amex.benefit_activation_engine.service;

import com.amex.benefit_activation_engine.model.Benefit;
import com.amex.benefit_activation_engine.model.BenefitType;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class CoverageWindowPolicy {

    public boolean isWithinWindow(Benefit benefit, LocalDate purchaseDate, LocalDate asOf) {
        if (benefit == null || benefit.getType() == BenefitType.TRAVEL_DELAY
                || benefit.getCoverageWindowDays() == null || purchaseDate == null) {
            return true;
        }
        return !purchaseDate.isBefore(asOf.minusDays(benefit.getCoverageWindowDays()));
    }

    public LocalDate expiresOn(Benefit benefit, LocalDate purchaseDate) {
        if (benefit == null || benefit.getType() == BenefitType.TRAVEL_DELAY
                || benefit.getCoverageWindowDays() == null || purchaseDate == null) {
            return null;
        }
        return purchaseDate.plusDays(benefit.getCoverageWindowDays());
    }
}
