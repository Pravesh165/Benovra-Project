package com.amex.benefit_activation_engine.config;

import com.amex.benefit_activation_engine.model.Benefit;
import com.amex.benefit_activation_engine.model.BenefitType;
import com.amex.benefit_activation_engine.model.Entitlement;
import com.amex.benefit_activation_engine.repository.BenefitRepository;
import com.amex.benefit_activation_engine.repository.EntitlementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Seeds the benefits catalog and card-product entitlements on startup so the
 * matching engine has data to work against in the demo.
 *
 * <p>Idempotent: it only runs when the benefit table is empty, so restarts (and
 * H2's in-memory reset) stay clean. Uses the repositories directly so entity
 * lifecycle callbacks ({@code @PrePersist}) and {@code @Builder.Default} values
 * are applied automatically.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final BenefitRepository benefitRepository;
    private final EntitlementRepository entitlementRepository;

    @Override
    public void run(String... args) {
        if (benefitRepository.count() > 0) {
            log.info("Benefits already present ({}). Skipping seed.", benefitRepository.count());
            return;
        }

        // ---- Benefits catalog ----
        Benefit purchaseProtection = benefitRepository.save(Benefit.builder()
                .type(BenefitType.PURCHASE_PROTECTION)
                .name("Purchase Protection")
                .description("Covers eligible new purchases against accidental damage or theft.")
                .perClaimLimit(new BigDecimal("1000.00"))
                .coverageWindowDays(90)
                .build());

        Benefit returnProtection = benefitRepository.save(Benefit.builder()
                .type(BenefitType.RETURN_PROTECTION)
                .name("Return Protection")
                .description("Reimburses an eligible item when the merchant refuses a valid return.")
                .perClaimLimit(new BigDecimal("300.00"))
                .coverageWindowDays(90)
                .build());

        Benefit travelDelay = benefitRepository.save(Benefit.builder()
                .type(BenefitType.TRAVEL_DELAY)
                .name("Travel-Delay Insurance")
                .description("Reimburses expenses when covered travel is delayed beyond a set threshold.")
                .perClaimLimit(new BigDecimal("500.00"))
                .coverageWindowDays(1) // TODO: travel-delay is event/hours-based (see checklist Phase 3)
                .build());

        // ---- Entitlements: which card products get which benefits ----
        // PLATINUM: all three
        entitlementRepository.saveAll(List.of(
                Entitlement.builder().cardProduct("PLATINUM").benefit(purchaseProtection).build(),
                Entitlement.builder().cardProduct("PLATINUM").benefit(returnProtection).build(),
                Entitlement.builder().cardProduct("PLATINUM").benefit(travelDelay).build()
        ));

        // GOLD: purchase + return (no travel delay)
        entitlementRepository.saveAll(List.of(
                Entitlement.builder().cardProduct("GOLD").benefit(purchaseProtection).build(),
                Entitlement.builder().cardProduct("GOLD").benefit(returnProtection).build()
        ));

        // GREEN: purchase protection only
        entitlementRepository.save(
                Entitlement.builder().cardProduct("GREEN").benefit(purchaseProtection).build()
        );

        log.info("Seeded {} benefits and {} entitlements.",
                benefitRepository.count(), entitlementRepository.count());
    }
}
