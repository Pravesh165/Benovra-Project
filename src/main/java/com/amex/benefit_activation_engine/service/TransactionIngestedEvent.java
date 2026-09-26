package com.amex.benefit_activation_engine.service;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Published the moment a transaction has been validated and saved. This is the
 * seam that auto-triggers benefit detection: the Phase 3 matching engine will
 * subscribe via {@code @EventListener} and run matching asynchronously, keeping
 * ingestion decoupled from detection.
 */
@Getter
public class TransactionIngestedEvent extends ApplicationEvent {

    private final Long transactionId;

    /**
     * @param source        the publisher (typically the IngestionService)
     * @param transactionId id of the freshly ingested transaction
     */
    public TransactionIngestedEvent(Object source, Long transactionId) {
        super(source);
        this.transactionId = transactionId;
    }
}
