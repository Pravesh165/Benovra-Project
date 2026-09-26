package com.amex.benefit_activation_engine.engine;

import com.amex.benefit_activation_engine.model.Benefit;
import com.amex.benefit_activation_engine.model.Transaction;
import com.amex.benefit_activation_engine.model.TransactionStatus;
import com.amex.benefit_activation_engine.repository.TransactionRepository;
import com.amex.benefit_activation_engine.service.ClaimService;
import com.amex.benefit_activation_engine.service.TransactionIngestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * Consumes the {@link TransactionIngestedEvent} published by ingestion and
 * auto-triggers benefit detection — closing the WATCH → MATCH loop.
 *
 * <p>Runs AFTER the ingesting transaction commits (so the row is durably saved)
 * and in its own transaction, then records the outcome on the transaction as
 * {@link TransactionStatus#MATCHED} or {@link TransactionStatus#NO_MATCH}. On a
 * match it also PRE-FILLs a claim for the top-ranked benefit — closing the
 * WATCH → MATCH → PRE-FILL loop.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BenefitDetectionListener {

    private final TransactionRepository transactionRepository;
    private final RuleEngine ruleEngine;
    private final ClaimService claimService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTransactionIngested(TransactionIngestedEvent event) {
        Transaction transaction = transactionRepository.findById(event.getTransactionId()).orElse(null);
        if (transaction == null) {
            log.warn("Detection skipped: transaction {} not found", event.getTransactionId());
            return;
        }

        List<Benefit> matches = ruleEngine.match(transaction);
        transaction.setStatus(matches.isEmpty() ? TransactionStatus.NO_MATCH : TransactionStatus.MATCHED);
        transactionRepository.save(transaction);

        // PRE-FILL: auto-generate a claim for the best (top-ranked) matched benefit.
        if (!matches.isEmpty()) {
            claimService.generateFor(transaction, matches.get(0));
        }

        log.info("Auto-detection for transaction {}: {} -> {} matching benefit(s) {}",
                transaction.getId(), transaction.getStatus(), matches.size(),
                matches.stream().map(b -> b.getType().name()).toList());
    }
}
