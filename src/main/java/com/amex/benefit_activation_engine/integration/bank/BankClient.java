package com.amex.benefit_activation_engine.integration.bank;

import com.amex.benefit_activation_engine.model.Claim;

/**
 * Boundary to the payment rails that actually move reimbursement money to the
 * card member. This is the clean seam where a real American Express
 * disbursement integration would attach; the prototype ships a
 * {@link MockBankClient} so the full workflow runs end to end offline.
 */
public interface BankClient {

    /**
     * Requests disbursement of an approved claim's reimbursement amount.
     *
     * @param claim the approved claim to pay out
     * @return the outcome, including a payout reference for reconciliation
     */
    DisbursementResult disburse(Claim claim);
}
