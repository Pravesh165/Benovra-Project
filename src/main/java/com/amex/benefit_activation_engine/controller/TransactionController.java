package com.amex.benefit_activation_engine.controller;

import com.amex.benefit_activation_engine.dto.CreateTransactionRequest;
import com.amex.benefit_activation_engine.dto.TransactionResponse;
import com.amex.benefit_activation_engine.service.IngestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Manual transaction feed for the WATCH stage. Ingesting a transaction validates
 * it, saves it, and auto-triggers benefit detection.
 */
@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
@Tag(name = "Transactions", description = "Ingest and query card purchases")
public class TransactionController {

    private final IngestionService ingestionService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ingest a transaction",
            description = "Validates, saves, and auto-triggers benefit detection for a purchase.")
    public TransactionResponse ingest(@Valid @RequestBody CreateTransactionRequest request) {
        return TransactionResponse.from(ingestionService.ingest(request));
    }

    @GetMapping
    @Operation(summary = "List transactions")
    public List<TransactionResponse> list() {
        return ingestionService.findAll().stream()
                .map(TransactionResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a transaction by id")
    public ResponseEntity<TransactionResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(TransactionResponse.from(ingestionService.getById(id)));
    }
}
