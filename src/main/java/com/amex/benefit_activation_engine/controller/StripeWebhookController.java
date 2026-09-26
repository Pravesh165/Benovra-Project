package com.amex.benefit_activation_engine.controller;

import com.amex.benefit_activation_engine.dto.CreateTransactionRequest;
import com.amex.benefit_activation_engine.dto.TransactionResponse;
import com.amex.benefit_activation_engine.integration.stripe.StripeTransactionAdapter;
import com.amex.benefit_activation_engine.integration.stripe.StripeWebhookEvent;
import com.amex.benefit_activation_engine.service.IngestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;

/**
 * Receives Stripe Issuing webhook events. Authorization events are mapped to the
 * engine's internal transaction shape and fed through the same
 * {@link IngestionService} pipeline as the manual feed — so switching from
 * Stripe test mode to live requires no downstream changes.
 *
 * <p><b>Security note:</b> for the offline demo this endpoint does NOT verify
 * the {@code Stripe-Signature} header. Before any real deployment, add webhook
 * signature verification with the endpoint's signing secret and restrict access.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/stripe")
@RequiredArgsConstructor
@Tag(name = "Stripe", description = "Stripe Issuing webhook ingestion")
public class StripeWebhookController {

    private static final String AUTHORIZATION_EVENT_PREFIX = "issuing_authorization";

    private final StripeTransactionAdapter adapter;
    private final IngestionService ingestionService;
    private final Validator validator;

    @PostMapping("/webhook")
    @Operation(summary = "Receive a Stripe Issuing event",
            description = "Maps issuing_authorization events into a transaction and ingests them; "
                    + "other event types are acknowledged and ignored.")
    public ResponseEntity<?> handle(@RequestBody StripeWebhookEvent event) {
        if (!isAuthorizationEvent(event)) {
            log.info("Ignoring non-authorization Stripe event: type={}", event.type());
            return ResponseEntity.ok(Map.of(
                    "received", true,
                    "processed", false,
                    "type", String.valueOf(event.type())));
        }

        CreateTransactionRequest request = adapter.toCreateRequest(event.data().object());

        // The webhook bypasses controller-level @Valid, so validate the mapped
        // request explicitly and surface failures as HTTP 400.
        Set<ConstraintViolation<CreateTransactionRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }

        TransactionResponse response = TransactionResponse.from(ingestionService.ingest(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    private boolean isAuthorizationEvent(StripeWebhookEvent event) {
        return event != null
                && event.type() != null
                && event.type().startsWith(AUTHORIZATION_EVENT_PREFIX)
                && event.data() != null
                && event.data().object() != null;
    }
}
