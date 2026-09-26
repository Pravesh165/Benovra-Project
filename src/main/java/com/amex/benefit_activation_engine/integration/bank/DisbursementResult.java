package com.amex.benefit_activation_engine.integration.bank;

import java.math.BigDecimal;

/**
 * Outcome of a reimbursement disbursement request.
 *
 * @param success   whether the funds movement was accepted
 * @param reference the bank/ledger reference for the payout (for reconciliation)
 * @param amount    the amount disbursed
 * @param message   human-readable status detail
 */
public record DisbursementResult(
        boolean success,
        String reference,
        BigDecimal amount,
        String message
) {
}
