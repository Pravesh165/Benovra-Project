package com.amex.benefit_activation_engine.service;

import com.amex.benefit_activation_engine.model.Benefit;
import com.amex.benefit_activation_engine.model.BenefitType;
import com.amex.benefit_activation_engine.model.Claim;
import com.amex.benefit_activation_engine.model.ClaimStatus;
import com.amex.benefit_activation_engine.model.Transaction;
import com.amex.benefit_activation_engine.model.TransactionStatus;
import com.amex.benefit_activation_engine.repository.BenefitRepository;
import com.amex.benefit_activation_engine.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the PRE-FILL stage: benefit-specific field mapping, per-claim-limit
 * capping, PREFILLED persistence, idempotency, the pre-fill quality check, and
 * claim lookups. Runs against the seeded benefits catalog.
 */
@SpringBootTest
@Transactional
class ClaimServiceTest {

    @Autowired
    private ClaimService claimService;
    @Autowired
    private BenefitRepository benefitRepository;
    @Autowired
    private TransactionRepository transactionRepository;

    private Benefit benefit(BenefitType type) {
        return benefitRepository.findByType(type).orElseThrow();
    }

    private Transaction saveTxn(String category, String amount, String description) {
        return transactionRepository.save(Transaction.builder()
                .cardMemberId("CM-1001")
                .cardProduct("PLATINUM")
                .merchantName("Best Electronics")
                .merchantCategory(category)
                .amount(new BigDecimal(amount))
                .currency("USD")
                .purchaseDate(LocalDate.of(2026, 7, 20))
                .description(description)
                .status(TransactionStatus.MATCHED)
                .build());
    }

    @Test
    void purchaseProtection_prefillsCommonAndTypeSpecificFields() {
        Transaction txn = saveTxn("ELECTRONICS", "500.00", "MacBook Pro 14");

        Claim claim = claimService.generateFor(txn, benefit(BenefitType.PURCHASE_PROTECTION));

        assertThat(claim.getId()).isNotNull();
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.PREFILLED);
        assertThat(claim.getClaimAmount()).isEqualByComparingTo("500.00");

        Map<String, String> data = claim.getPrefilledData();
        assertThat(data)
                .containsEntry("cardMemberId", "CM-1001")
                .containsEntry("benefitType", "PURCHASE_PROTECTION")
                .containsEntry("merchantName", "Best Electronics")
                .containsEntry("purchaseAmount", "500.00")
                .containsEntry("currency", "USD")
                .containsEntry("purchaseDate", "2026-07-20")
                .containsEntry("claimAmount", "500.00")
                .containsEntry("itemDescription", "MacBook Pro 14")
                .containsEntry("incidentType", "DAMAGE_OR_THEFT");
    }

    @Test
    void returnProtection_fillsReturnSpecificFields() {
        Transaction txn = saveTxn("RETAIL", "150.00", "Wireless headphones");

        Claim claim = claimService.generateFor(txn, benefit(BenefitType.RETURN_PROTECTION));

        assertThat(claim.getPrefilledData())
                .containsEntry("returnReason", "MERCHANT_REFUSED_RETURN")
                .containsEntry("itemDescription", "Wireless headphones")
                .containsKey("returnWindowDays");
    }

    @Test
    void travelDelay_fillsTravelSpecificFields() {
        Transaction txn = saveTxn("AIRLINE", "800.00", "Flight NYC-LON");

        Claim claim = claimService.generateFor(txn, benefit(BenefitType.TRAVEL_DELAY));

        assertThat(claim.getPrefilledData())
                .containsEntry("travelProvider", "Best Electronics")
                .containsEntry("travelDate", "2026-07-20")
                .containsEntry("minimumDelayHours", "6")
                .containsEntry("expenseType", "MEALS_AND_LODGING");
    }

    @Test
    void claimAmount_isCappedAtPerClaimLimit() {
        // Purchase protection limit is 1000; a 2000 purchase caps the claim at 1000.
        Transaction txn = saveTxn("ELECTRONICS", "2000.00", "Home theatre");

        Claim claim = claimService.generateFor(txn, benefit(BenefitType.PURCHASE_PROTECTION));

        assertThat(claim.getClaimAmount()).isEqualByComparingTo("1000.00");
        assertThat(claim.getPrefilledData()).containsEntry("claimAmount", "1000.00");
    }

    @Test
    void itemDescription_fallsBackToMerchantWhenDescriptionBlank() {
        Transaction txn = saveTxn("ELECTRONICS", "500.00", null);

        Claim claim = claimService.generateFor(txn, benefit(BenefitType.PURCHASE_PROTECTION));

        assertThat(claim.getPrefilledData().get("itemDescription"))
                .isEqualTo("electronics purchase at Best Electronics");
    }

    @Test
    void generateFor_isIdempotentPerTransactionAndBenefit() {
        Transaction txn = saveTxn("ELECTRONICS", "500.00", "MacBook Pro 14");
        Benefit benefit = benefit(BenefitType.PURCHASE_PROTECTION);

        Claim first = claimService.generateFor(txn, benefit);
        Claim second = claimService.generateFor(txn, benefit);

        assertThat(second.getId()).isEqualTo(first.getId());
    }

    @Test
    void getById_unknownClaim_throwsNotFound() {
        assertThatThrownBy(() -> claimService.getById(999999L))
                .isInstanceOf(ClaimNotFoundException.class);
    }

    @Test
    void prefillQualityCheck_rejectsMissingRequiredField() {
        // A map missing every required key must be flagged as incomplete.
        Map<String, String> incomplete = new LinkedHashMap<>();
        incomplete.put("merchantName", "Best Electronics"); // only one field present

        assertThatThrownBy(() ->
                claimService.assertPrefillComplete(BenefitType.PURCHASE_PROTECTION, incomplete))
                .isInstanceOf(PrefillIncompleteException.class)
                .hasMessageContaining("cardMemberId")
                .hasMessageContaining("incidentType");
    }
}
