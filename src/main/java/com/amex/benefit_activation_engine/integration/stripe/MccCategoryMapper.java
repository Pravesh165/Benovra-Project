package com.amex.benefit_activation_engine.integration.stripe;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;

/**
 * Maps a card-network Merchant Category Code (MCC) to the engine's internal
 * merchant category used by the matching rules (e.g. {@code 5732 -> ELECTRONICS}).
 *
 * <p>Kept deliberately small and in-code for the demo; in production this would
 * be externalized to config or a lookup table.</p>
 */
@Component
public class MccCategoryMapper {

    private static final Map<String, String> MCC_TO_CATEGORY = Map.ofEntries(
            Map.entry("5732", "ELECTRONICS"),        // Electronics stores
            Map.entry("5722", "APPLIANCES"),         // Household appliance stores
            Map.entry("5651", "APPAREL"),            // Family clothing stores
            Map.entry("5311", "DEPARTMENT_STORE"),   // Department stores
            Map.entry("5812", "RESTAURANT"),         // Eating places / restaurants
            Map.entry("7011", "LODGING"),            // Hotels / lodging
            Map.entry("4511", "AIRLINE"),            // Airlines
            Map.entry("3000", "AIRLINE"),            // Airline (carrier-specific range)
            Map.entry("4722", "TRAVEL_AGENCY")       // Travel agencies
    );

    /**
     * Resolves the internal category for an MCC. Falls back to the supplied
     * Stripe category string (upper-cased) when the MCC is unknown, and finally
     * to {@code OTHER}.
     *
     * @param mcc              the merchant category code (may be null)
     * @param fallbackCategory Stripe's textual category (may be null)
     */
    public String categoryFor(String mcc, String fallbackCategory) {
        if (mcc != null) {
            String mapped = MCC_TO_CATEGORY.get(mcc.trim());
            if (mapped != null) {
                return mapped;
            }
        }
        if (fallbackCategory != null && !fallbackCategory.isBlank()) {
            return fallbackCategory.trim().toUpperCase(Locale.ROOT);
        }
        return "OTHER";
    }
}
