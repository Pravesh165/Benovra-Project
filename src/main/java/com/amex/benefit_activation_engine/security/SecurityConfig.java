package com.amex.benefit_activation_engine.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless JWT security.
 *
 * <p>Design goal: guard <b>only</b> the customer-facing {@code /api/me/**}
 * endpoints. Every other surface — the existing REST API ({@code /api/claims},
 * {@code /api/transactions}, ...), the Stripe webhook, metrics, Swagger UI, the
 * H2 console, actuator, and the static UI — stays open, so the engine's
 * existing behaviour and test suite are unaffected.</p>
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
                // Stateless token API: no CSRF tokens, no server-side session.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Allow the H2 console to render in a frame (same origin).
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .authorizeHttpRequests(auth -> auth
                        // Reviewer-only admin claims API.
                        .requestMatchers("/api/admin/claims/**").hasRole(Roles.REVIEWER)
                        // Customer "my claims" API.
                        .requestMatchers("/api/me/**").hasRole(Roles.CARD_MEMBER)
                        // Everything else remains public (existing behaviour, incl. /api/admin/feed).
                        .anyRequest().permitAll())
                // Return 401 (not a login redirect) when a protected call lacks a valid token.
                .exceptionHandling(ex -> ex.authenticationEntryPoint(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
