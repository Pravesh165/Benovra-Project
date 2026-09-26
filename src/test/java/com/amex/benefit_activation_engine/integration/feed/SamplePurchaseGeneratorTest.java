package com.amex.benefit_activation_engine.integration.feed;

import com.amex.benefit_activation_engine.dto.CreateTransactionRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.RepeatedTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the generator always produces a valid, ingestible purchase whose
 * card product matches the seeded entitlement set.
 */
class SamplePurchaseGeneratorTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    private final SamplePurchaseGenerator generator = new SamplePurchaseGenerator();

    @BeforeAll
    static void setup() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @RepeatedTest(50)
    void generatesValidPurchase() {
        CreateTransactionRequest req = generator.next();

        assertThat(validator.validate(req)).isEmpty();
        assertThat(req.getAmount()).isGreaterThan(BigDecimal.ZERO);
        assertThat(req.getCurrency()).isEqualTo("USD");
        assertThat(req.getCardProduct()).isIn(List.of("PLATINUM", "GOLD", "GREEN"));
        assertThat(req.getPurchaseDate()).isBeforeOrEqualTo(LocalDate.now());
    }
}
