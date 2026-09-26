package com.amex.benefit_activation_engine.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Login payload for the customer UI. The card member "signs in" with only their
 * card member id (demo identity token — no password in the prototype).
 */
public record LoginRequest(
        @NotBlank(message = "cardMemberId is required")
        String cardMemberId
) {
}
