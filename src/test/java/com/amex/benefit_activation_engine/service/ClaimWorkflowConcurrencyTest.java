package com.amex.benefit_activation_engine.service;

import com.amex.benefit_activation_engine.integration.bank.BankClient;
import com.amex.benefit_activation_engine.model.Benefit;
import com.amex.benefit_activation_engine.model.BenefitType;
import com.amex.benefit_activation_engine.model.Claim;
import com.amex.benefit_activation_engine.model.ClaimAuditEvent;
import com.amex.benefit_activation_engine.model.ClaimDecision;
import com.amex.benefit_activation_engine.model.ClaimDocument;
import com.amex.benefit_activation_engine.model.ClaimDocumentType;
import com.amex.benefit_activation_engine.model.ClaimStatus;
import com.amex.benefit_activation_engine.model.Transaction;
import com.amex.benefit_activation_engine.model.TransactionStatus;
import com.amex.benefit_activation_engine.repository.BenefitRepository;
import com.amex.benefit_activation_engine.repository.ClaimAuditRepository;
import com.amex.benefit_activation_engine.repository.ClaimDocumentRepository;
import com.amex.benefit_activation_engine.repository.ClaimRepository;
import com.amex.benefit_activation_engine.repository.TransactionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
class ClaimWorkflowConcurrencyTest {

    @Autowired
    private ClaimWorkflowService workflowService;
    @Autowired
    private ClaimService claimService;
    @Autowired
    private BenefitRepository benefitRepository;
    @Autowired
    private ClaimRepository claimRepository;
    @Autowired
    private ClaimAuditRepository auditRepository;
    @Autowired
    private ClaimDocumentRepository documentRepository;
    @Autowired
    private TransactionRepository transactionRepository;
    @MockitoSpyBean
    private BankClient bankClient;

    private Long transactionId;
    private Long claimId;

    @AfterEach
    void cleanUp() {
        if (claimId != null) {
            auditRepository.deleteAll(auditRepository.findByClaimIdOrderByOccurredAtAscIdAsc(claimId));
            documentRepository.deleteAll(documentRepository.findByClaimIdOrderByUploadedAtAscIdAsc(claimId));
            claimRepository.deleteById(claimId);
        }
        if (transactionId != null) {
            transactionRepository.deleteById(transactionId);
        }
    }

    @Test
    void concurrentReviewerApprovalsDisburseOnce() throws Exception {
        Claim claim = createReviewableClaim();
        CountDownLatch payoutStarted = new CountDownLatch(1);
        CountDownLatch releasePayout = new CountDownLatch(1);
        CountDownLatch secondApprovalStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        doAnswer(invocation -> {
            payoutStarted.countDown();
            if (!releasePayout.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to release mock payout");
            }
            return invocation.callRealMethod();
        }).when(bankClient).disburse(any(Claim.class));

        try {
            Future<ClaimStatus> firstApproval = executor.submit(() -> workflowService
                    .decide(claim.getId(), ClaimDecision.APPROVE, "Verified".toString())
                    .getStatus());
            assertThat(payoutStarted.await(5, TimeUnit.SECONDS)).isTrue();

            Future<ClaimStatus> secondApproval = executor.submit(() -> {
                secondApprovalStarted.countDown();
                try {
                    return workflowService.decide(claim.getId(), ClaimDecision.APPROVE, "Duplicate")
                            .getStatus();
                } catch (IllegalClaimTransitionException ex) {
                    return null;
                }
            });
            assertThat(secondApprovalStarted.await(5, TimeUnit.SECONDS)).isTrue();
            releasePayout.countDown();

            assertThat(firstApproval.get(10, TimeUnit.SECONDS)).isEqualTo(ClaimStatus.PAID);
            assertThat(secondApproval.get(10, TimeUnit.SECONDS)).isNull();
            assertThat(claimRepository.findById(claim.getId()).orElseThrow().getStatus())
                    .isEqualTo(ClaimStatus.PAID);
            assertThat(auditRepository.findByClaimIdOrderByOccurredAtAscIdAsc(claim.getId()).stream()
                    .filter(event -> event.getToStatus() == ClaimStatus.PAID))
                    .hasSize(1);
            verify(bankClient, times(1)).disburse(any(Claim.class));
        } finally {
            releasePayout.countDown();
            executor.shutdownNow();
        }
    }

    private Claim createReviewableClaim() {
        Transaction transaction = transactionRepository.save(Transaction.builder()
                .cardMemberId("CM-CONCURRENT")
                .cardProduct("PLATINUM")
                .merchantName("Concurrency Test Store")
                .merchantCategory("ELECTRONICS")
                .amount(new BigDecimal("800.00"))
                .currency("USD")
                .purchaseDate(LocalDate.now())
                .description("Test item")
                .status(TransactionStatus.MATCHED)
                .build());
        transactionId = transaction.getId();
        Benefit benefit = benefitRepository.findByType(BenefitType.PURCHASE_PROTECTION).orElseThrow();
        Claim claim = claimService.generateFor(transaction, benefit);
        claimId = claim.getId();

        Claim managedClaim = claimRepository.findById(claimId).orElseThrow();
        for (ClaimDocumentType type : List.of(ClaimDocumentType.PURCHASE_RECEIPT, ClaimDocumentType.PROOF_OF_LOSS)) {
            documentRepository.save(ClaimDocument.builder()
                    .claim(managedClaim)
                    .documentType(type)
                    .originalFilename(type.name() + ".pdf")
                    .contentType("application/pdf")
                    .sizeBytes(1)
                    .storageKey("test-only")
                    .required(true)
                    .build());
        }
        workflowService.submit(claimId);
        return claimRepository.findById(claimId).orElseThrow();
    }
}
