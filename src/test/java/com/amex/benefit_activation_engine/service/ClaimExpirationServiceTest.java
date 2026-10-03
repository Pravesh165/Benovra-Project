package com.amex.benefit_activation_engine.service;

import com.amex.benefit_activation_engine.model.Benefit;
import com.amex.benefit_activation_engine.model.BenefitType;
import com.amex.benefit_activation_engine.model.Claim;
import com.amex.benefit_activation_engine.model.ClaimStatus;
import com.amex.benefit_activation_engine.model.Transaction;
import com.amex.benefit_activation_engine.repository.ClaimRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClaimExpirationServiceTest {

    @Mock
    private ClaimRepository claimRepository;

    private ClaimExpirationService expirationService;

    @BeforeEach
    void setUp() {
        expirationService = new ClaimExpirationService(claimRepository, new CoverageWindowPolicy());
    }

    @Test
    void memberScanReturnsOnlyDraftClaimsInsideWarningWindow() {
        LocalDate today = LocalDate.now();
        Claim dueToday = claim("CM-EXPIRING", BenefitType.PURCHASE_PROTECTION, today.minusDays(90), 90);
        Claim dueSoon = claim("CM-EXPIRING", BenefitType.RETURN_PROTECTION, today.minusDays(86), 90);
        Claim expired = claim("CM-EXPIRING", BenefitType.PURCHASE_PROTECTION, today.minusDays(91), 90);
        Claim dueLater = claim("CM-EXPIRING", BenefitType.PURCHASE_PROTECTION, today.minusDays(82), 90);
        Claim eventBased = claim("CM-EXPIRING", BenefitType.TRAVEL_DELAY, today.minusYears(2), 1);
        Claim submitted = claim("CM-EXPIRING", BenefitType.PURCHASE_PROTECTION, today.minusDays(86), 90);
        submitted.setStatus(ClaimStatus.SUBMITTED);
        when(claimRepository.findByTransactionCardMemberIdAndStatusOrderByCreatedAtDesc(
                "CM-EXPIRING", ClaimStatus.PREFILLED))
                .thenReturn(List.of(dueSoon, expired, dueLater, dueToday, eventBased));

        List<Claim> expiring = expirationService.findExpiringForMember("CM-EXPIRING", 7);

        assertThat(expiring).containsExactly(dueToday, dueSoon);
        assertThat(expiring).doesNotContain(expired, dueLater, eventBased, submitted);
        verify(claimRepository).findByTransactionCardMemberIdAndStatusOrderByCreatedAtDesc(
                "CM-EXPIRING", ClaimStatus.PREFILLED);
    }

    @Test
    void reviewerScanUsesRequestedWindow() {
        LocalDate today = LocalDate.now();
        Claim dueSoon = claim("CM-EXPIRING", BenefitType.PURCHASE_PROTECTION, today.minusDays(87), 90);
        when(claimRepository.findByStatus(ClaimStatus.PREFILLED)).thenReturn(List.of(dueSoon));

        assertThat(expirationService.findExpiringForReviewer(7)).containsExactly(dueSoon);
    }

    private Claim claim(String memberId, BenefitType type, LocalDate purchaseDate, int windowDays) {
        Benefit benefit = Benefit.builder().type(type).coverageWindowDays(windowDays).build();
        Transaction transaction = Transaction.builder().cardMemberId(memberId).purchaseDate(purchaseDate).build();
        return Claim.builder().benefit(benefit).transaction(transaction).status(ClaimStatus.PREFILLED).build();
    }
}
