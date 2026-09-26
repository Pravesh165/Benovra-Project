package com.amex.benefit_activation_engine.controller;

import com.amex.benefit_activation_engine.dto.LoginRequest;
import com.amex.benefit_activation_engine.dto.LoginResponse;
import com.amex.benefit_activation_engine.security.JwtService;
import com.amex.benefit_activation_engine.security.Roles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login. Issues a signed identity token for the UI.
 *
 * <p>Typing {@code admin} logs in as a claims <b>reviewer</b> (role
 * {@link Roles#REVIEWER}, access to {@code /api/admin/**}); any other id logs in
 * as a <b>card member</b> (role {@link Roles#CARD_MEMBER}, access to
 * {@code /api/me/**}).</p>
 *
 * <p>Prototype scope: these are demo identity tokens, not password-based
 * authentication.</p>
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Login — issues a JWT scoped to a card member or reviewer")
public class AuthController {

    /** Typing this id (case-insensitive) logs in as a reviewer. */
    private static final String ADMIN_ID = "admin";

    private final JwtService jwtService;

    @PostMapping("/login")
    @Operation(summary = "Log in",
            description = "Returns a signed JWT. Use 'admin' to log in as a reviewer; any other "
                    + "value logs in as that card member.")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        String input = request.cardMemberId().trim();
        boolean isAdmin = ADMIN_ID.equalsIgnoreCase(input);

        String subject = isAdmin ? ADMIN_ID : input;
        String role = isAdmin ? Roles.REVIEWER : Roles.CARD_MEMBER;

        String token = jwtService.issueToken(subject, role);
        return LoginResponse.bearer(token, subject, role, jwtService.getExpiryMinutes());
    }
}
