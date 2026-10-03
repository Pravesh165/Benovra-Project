package com.amex.benefit_activation_engine.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless JWT security — BOLA-hardened.
 *
 * <p>Access control tiers:</p>
 * <ul>
 *   <li><b>REVIEWER only</b> — {@code /api/admin/claims/**},
 *       {@code GET /api/transactions/**} (full list / by-id),
 *       {@code GET /api/claims} (cross-member list),
 *       {@code GET /api/claims/*&#47;audit},
 *       {@code POST /api/claims/*&#47;decision}</li>
 *   <li><b>CARD_MEMBER only</b> — {@code /api/me/**}</li>
 *   <li><b>Any authenticated user</b> — {@code GET /api/claims/{id}},
 *       {@code POST /api/claims/*&#47;submit} (ownership enforced in controller)</li>
 *   <li><b>Public</b> — auth endpoints, Swagger, H2 console, actuator, static assets,
 *       {@code POST /api/transactions} (webhook / demo feed ingestion),
 *       {@code /api/admin/feed/**} (demo feed toggle),
 *       {@code /api/benefits/**}, {@code /api/entitlements/**},
 *       {@code /stripe/**}, metrics</li>
 * </ul>
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    @Bean
    public JwtAuthFilter jwtAuthFilter(JwtService jwtService) {
        return new JwtAuthFilter(jwtService);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter)
            throws Exception {
        http
                // Stateless token API: no CSRF, no server-side session.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Allow the H2 console to render in a frame (same origin).
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .authorizeHttpRequests(auth -> auth

                        // ── PUBLIC ────────────────────────────────────────────────────────────
                        // Auth endpoints (login / token refresh / register).
                        .requestMatchers("/api/auth/**").permitAll()
                        // Stripe webhook (signature verified inside the controller).
                        .requestMatchers("/api/stripe/**").permitAll()
                        // Demo feed admin toggle (no auth required to keep demos simple).
                        .requestMatchers("/api/admin/feed/**").permitAll()
                        // Benefit catalogue and entitlement lookup (read-only, non-sensitive).
                        .requestMatchers("/api/benefits/**").permitAll()
                        .requestMatchers("/api/entitlements/**").permitAll()
                        // Transaction ingestion webhook — POST only.
                        .requestMatchers(HttpMethod.POST, "/api/transactions").permitAll()
                        // Infrastructure / tooling.
                        .requestMatchers("/h2-console/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        // Static frontend assets.
                        .requestMatchers("/", "/index.html", "/assets/**", "/favicon.ico").permitAll()

                        // ── REVIEWER-ONLY ─────────────────────────────────────────────────────
                        // Admin claims API (cross-member; also handles decisions).
                        .requestMatchers("/api/admin/claims/**").hasRole(Roles.REVIEWER)
                        // Full transaction list and individual transaction lookup.
                        .requestMatchers(HttpMethod.GET, "/api/transactions/**").hasRole(Roles.REVIEWER)
                        // Cross-member claim list (PREFILLED/SUBMITTED/etc.).
                        .requestMatchers(HttpMethod.GET, "/api/claims").hasRole(Roles.REVIEWER)
                        // Immutable audit trail.
                        .requestMatchers(HttpMethod.GET, "/api/claims/*/audit").hasRole(Roles.REVIEWER)
                        // Reviewer decision on a claim.
                        .requestMatchers(HttpMethod.POST, "/api/claims/*/decision").hasRole(Roles.REVIEWER)

                        // ── CARD_MEMBER-ONLY ──────────────────────────────────────────────────
                        // "My claims" scoped API.
                        .requestMatchers("/api/me/**").hasRole(Roles.CARD_MEMBER)

                        // ── ANY AUTHENTICATED USER ────────────────────────────────────────────
                        // Remaining /api/claims/** paths (GET single claim, POST submit).
                        .anyRequest().authenticated())

                // Return 401 (not a login redirect) when a protected call lacks a valid token.
                .exceptionHandling(ex -> ex.authenticationEntryPoint(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
