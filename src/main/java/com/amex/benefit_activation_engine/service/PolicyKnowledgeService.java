package com.amex.benefit_activation_engine.service;

import com.amex.benefit_activation_engine.dto.PolicyKnowledgeResponse;
import com.amex.benefit_activation_engine.engine.MatchingProperties;
import com.amex.benefit_activation_engine.model.Benefit;
import com.amex.benefit_activation_engine.model.BenefitType;
import com.amex.benefit_activation_engine.model.Entitlement;
import com.amex.benefit_activation_engine.repository.BenefitRepository;
import com.amex.benefit_activation_engine.repository.EntitlementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PolicyKnowledgeService {

    private final BenefitRepository benefitRepository;
    private final EntitlementRepository entitlementRepository;
    private final MatchingProperties matchingProperties;

    private static final Map<BenefitType, List<String>> EXCLUSIONS = Map.of(
            BenefitType.PURCHASE_PROTECTION, List.of(
                    "Loss or theft not reported to law enforcement where applicable",
                    "Damage from normal wear and tear or gradual deterioration",
                    "Items lost or damaged due to intentional misconduct",
                    "Motor vehicles, aircraft, boats, or their motorized parts"
            ),
            BenefitType.RETURN_PROTECTION, List.of(
                    "Returns where merchant already agreed to refund or exchange",
                    "Items damaged, altered, or not in original working condition",
                    "Customized, personalized, or perishable merchandise",
                    "Purchases made outside the eligible return policy window"
            ),
            BenefitType.TRAVEL_DELAY, List.of(
                    "Delays under the required threshold (under 6 hours)",
                    "Expenses covered or refunded directly by common carrier",
                    "Pre-arranged travel changes or unconfirmed bookings",
                    "Delays resulting from illegal acts or regulatory grounding known prior to departure"
            )
    );

    private static final Map<BenefitType, List<String>> REQUIRED_DOCS = Map.of(
            BenefitType.PURCHASE_PROTECTION, List.of("Purchase Receipt", "Proof of Loss / Damage"),
            BenefitType.RETURN_PROTECTION, List.of("Purchase Receipt", "Merchant Return Denial"),
            BenefitType.TRAVEL_DELAY, List.of("Airline Booking Proof", "Airline Delay Certificate")
    );

    @Transactional(readOnly = true)
    public List<PolicyKnowledgeResponse> search(String query, String cardProduct) {
        List<Benefit> allBenefits = benefitRepository.findAll().stream()
                .filter(Benefit::isActive)
                .sorted(Comparator.comparing(Benefit::getId))
                .toList();

        List<Entitlement> entitlements = (StringUtils.hasText(cardProduct))
                ? entitlementRepository.findByCardProductAndActiveTrue(cardProduct.trim().toUpperCase(Locale.ROOT))
                : entitlementRepository.findAll();

        String normalizedQuery = StringUtils.hasText(query) ? query.trim().toLowerCase(Locale.ROOT) : null;

        return allBenefits.stream()
                .map(benefit -> toPolicyResponse(benefit, entitlements))
                .filter(resp -> matchesCardProduct(resp, cardProduct))
                .filter(resp -> matchesQuery(resp, normalizedQuery))
                .toList();
    }

    private PolicyKnowledgeResponse toPolicyResponse(Benefit benefit, List<Entitlement> entitlements) {
        BenefitType type = benefit.getType();
        List<String> categories = matchingProperties.categoriesFor(type);
        List<String> exclusions = EXCLUSIONS.getOrDefault(type, List.of());
        List<String> requiredDocs = REQUIRED_DOCS.getOrDefault(type, List.of());

        List<String> eligibleCards = entitlements.stream()
                .filter(e -> e.getBenefit() != null && e.getBenefit().getId().equals(benefit.getId()))
                .filter(Entitlement::isActive)
                .map(Entitlement::getCardProduct)
                .distinct()
                .sorted()
                .toList();

        String windowExp = (benefit.getCoverageWindowDays() != null)
                ? benefit.getCoverageWindowDays() + " days from purchase date"
                : "Incident event trigger (hours-based delay criteria)";

        String userEligibilityExplanation = switch (type) {
            case PURCHASE_PROTECTION -> "Protects eligible items against accidental damage or theft within "
                    + (benefit.getCoverageWindowDays() != null ? benefit.getCoverageWindowDays() : 90)
                    + " days of purchase up to $" + benefit.getPerClaimLimit() + " per claim.";
            case RETURN_PROTECTION -> "Reimburses items when a merchant refuses a return within "
                    + (benefit.getCoverageWindowDays() != null ? benefit.getCoverageWindowDays() : 90)
                    + " days of purchase up to $" + benefit.getPerClaimLimit() + " per claim.";
            case TRAVEL_DELAY -> "Covers essential meals, lodging, and personal necessities when common carrier travel is delayed by 6 or more hours up to $"
                    + benefit.getPerClaimLimit() + " per claim.";
        };

        return new PolicyKnowledgeResponse(
                benefit.getId(),
                benefit.getType(),
                benefit.getName(),
                benefit.getDescription(),
                benefit.getPerClaimLimit(),
                benefit.getCoverageWindowDays(),
                windowExp,
                categories,
                exclusions,
                requiredDocs,
                eligibleCards,
                userEligibilityExplanation
        );
    }

    private boolean matchesCardProduct(PolicyKnowledgeResponse resp, String cardProduct) {
        if (!StringUtils.hasText(cardProduct)) {
            return true;
        }
        String target = cardProduct.trim().toUpperCase(Locale.ROOT);
        return resp.eligibleCards().stream().anyMatch(c -> c.equalsIgnoreCase(target));
    }

    private boolean matchesQuery(PolicyKnowledgeResponse resp, String q) {
        if (q == null) {
            return true;
        }
        if (resp.name().toLowerCase(Locale.ROOT).contains(q)) return true;
        if (resp.type().name().toLowerCase(Locale.ROOT).contains(q)) return true;
        if (resp.description() != null && resp.description().toLowerCase(Locale.ROOT).contains(q)) return true;
        if (resp.eligibleCategories().stream().anyMatch(c -> c.toLowerCase(Locale.ROOT).contains(q))) return true;
        if (resp.exclusions().stream().anyMatch(e -> e.toLowerCase(Locale.ROOT).contains(q))) return true;
        if (resp.userEligibilityExplanation().toLowerCase(Locale.ROOT).contains(q)) return true;
        return false;
    }
}

