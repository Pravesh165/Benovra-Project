package com.amex.benefit_activation_engine.repository;

import com.amex.benefit_activation_engine.model.Benefit;
import com.amex.benefit_activation_engine.model.BenefitType;
import com.amex.benefit_activation_engine.model.Claim;
import com.amex.benefit_activation_engine.model.ClaimStatus;
import com.amex.benefit_activation_engine.model.Entitlement;
import com.amex.benefit_activation_engine.model.Transaction;
import com.amex.benefit_activation_engine.model.TransactionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the phase-1 data model: entities map correctly, the schema builds,
 * relationships persist, and repository finder methods work.
 */
@DataJpaTest
class DataModelPersistenceTest {

    @Autowired
    private BenefitRepository benefitRepository;
    @Autowired
    private EntitlementRepository entitlementRepository;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private ClaimRepository claimRepository;

    private Benefit newPurchaseProtection() {
        return Benefit.builder()
                .type(BenefitType.PURCHASE_PROTECTION)
                .name("Purchase Protection")
                .description("Covers eligible items against damage or theft.")
                .perClaimLimit(new BigDecimal("1000.00"))
                .coverageWindowDays(90)
                .build();
    }

    private Transaction newTransaction() {
        return Transaction.builder()
                .cardMemberId("CM-1001")
                .cardProduct("PLATINUM")
                .merchantName("Best Electronics")
                .merchantCategory("ELECTRONICS")
                .amount(new BigDecimal("499.99"))
                .currency("USD")
                .purchaseDate(LocalDate.of(2026, 7, 20))
                .description("Wireless headphones")
                .build();
    }

    @Test
    void benefitPersistsAndIsFoundByType() {
        Benefit saved = benefitRepository.save(newPurchaseProtection());

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.isActive()).isTrue(); // @Builder.Default
        assertThat(benefitRepository.findByType(BenefitType.PURCHASE_PROTECTION))
                .isPresent()
                .get()
                .extracting(Benefit::getName)
                .isEqualTo("Purchase Protection");
    }

    @Test
    void entitlementLinksCardProductToBenefit() {
        Benefit benefit = benefitRepository.save(newPurchaseProtection());
        entitlementRepository.save(Entitlement.builder()
                .cardProduct("PLATINUM")
                .benefit(benefit)
                .build());

        List<Entitlement> active = entitlementRepository.findByCardProductAndActiveTrue("PLATINUM");

        assertThat(active).hasSize(1);
        assertThat(active.get(0).getBenefit().getType()).isEqualTo(BenefitType.PURCHASE_PROTECTION);
    }

    @Test
    void transactionDefaultsToReceivedAndSetsCreatedAt() {
        Transaction saved = transactionRepository.save(newTransaction());

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(TransactionStatus.RECEIVED); // @Builder.Default
        assertThat(saved.getCreatedAt()).isNotNull();                        // @PrePersist
        assertThat(transactionRepository.findByCardMemberId("CM-1001")).hasSize(1);
    }

    @Test
    void claimLinksTransactionAndBenefitAndTracksStatus() {
        Benefit benefit = benefitRepository.save(newPurchaseProtection());
        Transaction transaction = transactionRepository.save(newTransaction());

        Claim saved = claimRepository.save(Claim.builder()
                .transaction(transaction)
                .benefit(benefit)
                .claimAmount(new BigDecimal("499.99"))
                .build());

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(ClaimStatus.PREFILLED); // @Builder.Default
        assertThat(saved.getCreatedAt()).isNotNull();               // @PrePersist

        List<Claim> drafts = claimRepository.findByStatus(ClaimStatus.PREFILLED);
        assertThat(drafts).hasSize(1);

        Claim reloaded = claimRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getTransaction().getMerchantName()).isEqualTo("Best Electronics");
        assertThat(reloaded.getBenefit().getType()).isEqualTo(BenefitType.PURCHASE_PROTECTION);
        assertThat(claimRepository.findByTransactionId(transaction.getId())).hasSize(1);
    }
}
