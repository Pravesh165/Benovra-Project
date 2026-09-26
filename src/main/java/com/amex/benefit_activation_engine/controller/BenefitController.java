package com.amex.benefit_activation_engine.controller;

import com.amex.benefit_activation_engine.dto.BenefitMatchResponse;
import com.amex.benefit_activation_engine.engine.RuleEngine;
import com.amex.benefit_activation_engine.model.Transaction;
import com.amex.benefit_activation_engine.service.IngestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * MATCH stage API: returns the protection benefits a transaction qualifies for,
 * cross-checked against the card product's entitlements and ranked best-first.
 */
@RestController
@RequestMapping("/api/benefits")
@RequiredArgsConstructor
@Tag(name = "Benefits", description = "Benefit detection / matching results")
public class BenefitController {

    private final IngestionService ingestionService;
    private final RuleEngine ruleEngine;

    @GetMapping("/{transactionId}")
    @Operation(summary = "Matched benefits for a transaction",
            description = "Runs the matching engine for the given transaction and returns the "
                    + "entitled, ranked benefits it qualifies for (empty if none).")
    public List<BenefitMatchResponse> matchesFor(@PathVariable Long transactionId) {
        Transaction transaction = ingestionService.getById(transactionId); // 404 if unknown
        return ruleEngine.match(transaction).stream()
                .map(benefit -> BenefitMatchResponse.of(benefit, transaction))
                .toList();
    }
}
