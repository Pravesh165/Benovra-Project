package com.amex.benefit_activation_engine.engine;

import com.amex.benefit_activation_engine.dto.CreateTransactionRequest;
import com.amex.benefit_activation_engine.model.ClaimStatus;
import com.amex.benefit_activation_engine.model.Transaction;
import com.amex.benefit_activation_engine.model.TransactionStatus;
import com.amex.benefit_activation_engine.repository.ClaimRepository;
import com.amex.benefit_activation_engine.repository.TransactionRepository;
import com.amex.benefit_activation_engine.service.IngestionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the WATCH → MATCH auto-trigger: ingesting a transaction fires the
 * {@code TransactionIngestedEvent}, which the detection listener consumes after
 * commit and records the outcome as MATCHED / NO_MATCH.
 *
 * <p>Intentionally NOT {@code @Transactional}: the listener runs on
 * {@code AFTER_COMMIT}, which only fires for a real commit. Each ingested
 * transaction is cleaned up afterwards to keep the shared in-memory DB clean.</p>
 */
@SpringBootTest
class BenefitDetectionListenerTest {

    @Autowired
    private IngestionService ingestionService;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private ClaimRepository claimRepository;

    @AfterEach
    void cleanUp() {
        // Claims reference transactions (FK), so remove claims first.
        claimRepository.deleteAll();
        transactionRepository.deleteAll();
    }

    private CreateTransactionRequest request(String cardProduct, String category, String amount) {
        return CreateTransactionRequest.builder()
                .cardMemberId("CM-1001")
                .cardProduct(cardProduct)
                .merchantName("Best Electronics")
                .merchantCategory(category)
                .amount(new BigDecimal(amount))
                .currency("USD")
                .purchaseDate(LocalDate.now())
                .build();
    }

    @Test
    void ingestingAQualifyingPurchase_autoMarksMatched() {
        Transaction saved = ingestionService.ingest(request("PLATINUM", "ELECTRONICS", "500.00"));

        // The synchronous ingest response reflects VALIDATED; detection runs after commit.
        Transaction reloaded = transactionRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(TransactionStatus.MATCHED);

        // PRE-FILL: a claim is auto-generated for the matched benefit.
        var claims = claimRepository.findByTransactionId(saved.getId());
        assertThat(claims).hasSize(1);
        assertThat(claims.get(0).getStatus()).isEqualTo(ClaimStatus.PREFILLED);
        assertThat(claims.get(0).getPrefilledData()).isNotEmpty();
    }

    @Test
    void ingestingAnUnentitledPurchase_autoMarksNoMatch() {
        Transaction saved = ingestionService.ingest(request("BLACK", "ELECTRONICS", "500.00"));

        Transaction reloaded = transactionRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(TransactionStatus.NO_MATCH);

        // No match → no claim pre-filled.
        List<?> claims = claimRepository.findByTransactionId(saved.getId());
        assertThat(claims).isEmpty();
    }
}
