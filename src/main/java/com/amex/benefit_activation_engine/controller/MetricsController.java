package com.amex.benefit_activation_engine.controller;

import com.amex.benefit_activation_engine.dto.MetricsResponse;
import com.amex.benefit_activation_engine.service.MetricsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Task 5 metrics API: detection accuracy and the reduction in unclaimed benefit
 * value the engine has driven.
 */
@RestController
@RequestMapping("/api/metrics")
@RequiredArgsConstructor
@Tag(name = "Metrics", description = "Detection & unclaimed-benefit-reduction metrics")
public class MetricsController {

    private final MetricsService metricsService;

    @GetMapping
    @Operation(summary = "Engine metrics",
            description = "Returns detection rate, total detectable benefit value, value claimed "
                    + "and paid, and the headline % reduction in unclaimed benefits.")
    public MetricsResponse metrics() {
        return metricsService.compute();
    }
}
