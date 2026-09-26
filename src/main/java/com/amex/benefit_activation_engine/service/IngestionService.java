package com.amex.benefit_activation_engine.service;

import com.amex.benefit_activation_engine.dto.CreateTransactionRequest;
import com.amex.benefit_activation_engine.model.Transaction;
import com.amex.benefit_activation_engine.model.TransactionStatus;
import com.amex.benefit_activation_engine.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * Receives, validates, normalizes and persists incoming purchases, then
 * auto-triggers detection by publishing a {@link TransactionIngestedEvent}.
 *
 * <p>Field-level validation (non-empty amount, valid category, currency length,
 * etc.) is enforced by Bean Validation on {@link CreateTransactionRequest} at
 * the controller boundary; this service handles normalization and persistence.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionService {

    private final TransactionRepository transactionRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Transaction ingest(CreateTransactionRequest request) {
        Transaction transaction = Transaction.builder()
                .cardMemberId(request.getCardMemberId().trim())
                .cardProduct(normalizeCode(request.getCardProduct()))
                .merchantName(request.getMerchantName().trim())
                .merchantCategory(normalizeCode(request.getMerchantCategory()))
                .amount(request.getAmount())
                .currency(normalizeCode(request.getCurrency()))
                .purchaseDate(request.getPurchaseDate())
                .description(request.getDescription())
                // validated at the boundary, so it enters the pipeline as VALIDATED
                .status(TransactionStatus.VALIDATED)
                .build();

        Transaction saved = transactionRepository.save(transaction);
        log.info("Ingested transaction {} (cardProduct={}, amount={} {})",
                saved.getId(), saved.getCardProduct(), saved.getAmount(), saved.getCurrency());

        // Auto-trigger detection (Phase 3 matching engine listens for this).
        eventPublisher.publishEvent(new TransactionIngestedEvent(this, saved.getId()));

        return saved;
    }

    @Transactional(readOnly = true)
    public List<Transaction> findAll() {
        return transactionRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Transaction getById(Long id) {
        return transactionRepository.findById(id)
                .orElseThrow(() -> new TransactionNotFoundException(id));
    }

    /**
     * Normalizes a code-like field to a trimmed, upper-cased value so downstream
     * lookups (e.g. cardProduct -> entitlements) don't silently miss on casing.
     */
    private String normalizeCode(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }
}
