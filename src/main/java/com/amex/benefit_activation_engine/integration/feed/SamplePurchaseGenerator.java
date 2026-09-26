package com.amex.benefit_activation_engine.integration.feed;

import com.amex.benefit_activation_engine.dto.CreateTransactionRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Generates realistic, randomized purchases for the demo feed. Card products
 * are drawn from the set that the {@code DataSeeder} grants entitlements to
 * (PLATINUM / GOLD / GREEN) so the downstream matching engine has something to
 * match against.
 */
@Component
public class SamplePurchaseGenerator {

    private record Merchant(String name, String category) {
    }

    private static final List<Merchant> MERCHANTS = List.of(
            new Merchant("Best Electronics", "ELECTRONICS"),
            new Merchant("SkyHigh Airlines", "AIRLINE"),
            new Merchant("Grand Plaza Hotel", "LODGING"),
            new Merchant("Fashion Hub", "APPAREL"),
            new Merchant("HomeStyle Appliances", "APPLIANCES"),
            new Merchant("City Department Store", "DEPARTMENT_STORE")
    );

    private static final List<String> CARD_PRODUCTS = List.of("PLATINUM", "GOLD", "GREEN");

    private static final List<String> MEMBER_IDS = List.of("CM-1001", "CM-1002", "CM-1003", "CM-1004");

    /** Builds one valid, ready-to-ingest purchase. */
    public CreateTransactionRequest next() {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        Merchant merchant = MERCHANTS.get(rnd.nextInt(MERCHANTS.size()));

        BigDecimal amount = BigDecimal.valueOf(rnd.nextDouble(25.0, 1500.0))
                .setScale(2, RoundingMode.HALF_UP);

        return CreateTransactionRequest.builder()
                .cardMemberId(MEMBER_IDS.get(rnd.nextInt(MEMBER_IDS.size())))
                .cardProduct(CARD_PRODUCTS.get(rnd.nextInt(CARD_PRODUCTS.size())))
                .merchantName(merchant.name())
                .merchantCategory(merchant.category())
                .amount(amount)
                .currency("USD")
                // within the last week, always past-or-present
                .purchaseDate(LocalDate.now().minusDays(rnd.nextInt(0, 7)))
                .description("Simulated purchase at " + merchant.name())
                .build();
    }
}
