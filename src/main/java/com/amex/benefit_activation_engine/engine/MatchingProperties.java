package com.amex.benefit_activation_engine.engine;

import com.amex.benefit_activation_engine.model.BenefitType;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Externalized matching rules: which merchant categories qualify a purchase for
 * each benefit type. Editable in {@code application.yml} under {@code matching.*}
 * with no code changes.
 *
 * <pre>
 * matching:
 *   categories:
 *     PURCHASE_PROTECTION: [ELECTRONICS, APPLIANCES]
 *     RETURN_PROTECTION:   [APPAREL, DEPARTMENT_STORE, ELECTRONICS]
 *     TRAVEL_DELAY:        [AIRLINE, LODGING, TRAVEL_AGENCY]
 * </pre>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "matching")
public class MatchingProperties {

    /** Merchant categories that qualify a purchase for each benefit type. */
    private Map<BenefitType, List<String>> categories = new EnumMap<>(BenefitType.class);

    /** Case-insensitive lookup of the categories configured for a benefit type. */
    public boolean categoryQualifies(BenefitType type, String merchantCategory) {
        if (merchantCategory == null) {
            return false;
        }
        List<String> configured = categories.getOrDefault(type, List.of());
        return configured.stream()
                .anyMatch(c -> c.equalsIgnoreCase(merchantCategory.trim()));
    }

    public List<String> categoriesFor(BenefitType type) {
        return categories.getOrDefault(type, List.of()).stream()
                .map(c -> c.toUpperCase(Locale.ROOT))
                .toList();
    }
}
