package com.amex.benefit_activation_engine.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Lightweight liveness endpoint used to verify the app boots and serves traffic.
 * (Spring Boot Actuator additionally exposes {@code /actuator/health}.)
 */
@RestController
@Tag(name = "Health", description = "Service liveness check")
public class HealthController {

    @GetMapping("/health")
    @Operation(summary = "Liveness probe", description = "Returns UP with a timestamp when the service is running.")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "service", "benefit-activation-engine",
                "timestamp", Instant.now().toString()
        );
    }
}
