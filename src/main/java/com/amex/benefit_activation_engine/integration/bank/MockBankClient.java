package com.amex.benefit_activation_engine.integration.bank;

import com.amex.benefit_activation_engine.model.Claim;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Offline stand-in for the real payment rails. Always succeeds and returns a
 * deterministic-looking payout reference, so the approval workflow can complete
 * a full APPROVED -> PAID cycle without external infrastructure.
 *
 * <p>Swapping in the real integration is a matter of providing another
 * {@link BankClient} bean; no workflow code changes.</p>
 */
@Slf4j
@Component
public class MockBankClient implements BankClient {

    @Override
    public DisbursementResult disburse(Claim claim) {
        String reference = "MOCK-PAYOUT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.info("[mock-bank] Disbursed {} for claim {} -> reference {}",
                claim.getClaimAmount(), claim.getId(), reference);
        return new DisbursementResult(
                true,
                reference,
                claim.getClaimAmount(),
                "Mock disbursement accepted");
    }
}
