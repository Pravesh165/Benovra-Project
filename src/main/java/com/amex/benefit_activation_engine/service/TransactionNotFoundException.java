package com.amex.benefit_activation_engine.service;

/**
 * Thrown when a transaction lookup fails. Mapped to HTTP 404 by the web layer.
 */
public class TransactionNotFoundException extends RuntimeException {

    public TransactionNotFoundException(Long id) {
        super("Transaction not found: " + id);
    }
}
