package com.amex.benefit_activation_engine.controller;

import com.amex.benefit_activation_engine.dto.EntitlementResponse;
import com.amex.benefit_activation_engine.model.Entitlement;
import com.amex.benefit_activation_engine.repository.EntitlementRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

/**
 * Entitlement management API: exposes which benefits each card product is
 * entitled to claim. Backs the workflow's entitlement cross-check and lets the
 * frontend show a card's available protections.
 */
@RestController
@RequestMapping("/api/entitlements")
@RequiredArgsConstructor
@Tag(name = "Entitlements", description = "Card-product benefit entitlements")
public class EntitlementController {

    private final EntitlementRepository entitlementRepository;

    @GetMapping
    @Operation(summary = "List entitlements",
            description = "Returns all card-product entitlements, or only those for the given "
                    + "cardProduct when the query parameter is supplied.")
    public List<EntitlementResponse> list(@RequestParam(required = false) String cardProduct) {
        List<Entitlement> entitlements = (cardProduct == null || cardProduct.isBlank())
                ? entitlementRepository.findAll()
                : entitlementRepository.findByCardProduct(cardProduct.trim().toUpperCase(Locale.ROOT));

        return entitlements.stream()
                .map(EntitlementResponse::from)
                .toList();
    }
}
