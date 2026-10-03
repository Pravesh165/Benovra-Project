package com.amex.benefit_activation_engine.service;

import com.amex.benefit_activation_engine.model.Claim;
import com.amex.benefit_activation_engine.model.ClaimStatus;
import com.amex.benefit_activation_engine.repository.ClaimRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClaimExpirationService {

    private final ClaimRepository claimRepository;
    private final CoverageWindowPolicy coverageWindowPolicy;

    @Value("${workflow.expiration-warning-days:7}")
    private int expirationWarningDays;

    @Transactional(readOnly = true)
    public List<Claim> findExpiringForMember(String cardMemberId, int withinDays) {
        return expiring(claimRepository.findByTransactionCardMemberIdAndStatusOrderByCreatedAtDesc(
                cardMemberId, ClaimStatus.PREFILLED), withinDays);
    }

    @Transactional(readOnly = true)
    public List<Claim> findExpiringForReviewer(int withinDays) {
        return expiring(claimRepository.findByStatus(ClaimStatus.PREFILLED), withinDays);
    }

    @Scheduled(fixedDelayString = "${workflow.expiration-scan-interval-ms:3600000}")
    @Transactional(readOnly = true)
    public void scanExpiringClaims() {
        List<Claim> claims = expiring(claimRepository.findByStatus(ClaimStatus.PREFILLED), expirationWarningDays);
        if (!claims.isEmpty()) {
            log.warn("{} claim(s) expire within {} day(s): {}", claims.size(), expirationWarningDays,
                    claims.stream().map(Claim::getId).toList());
        }
    }

    private List<Claim> expiring(List<Claim> claims, int withinDays) {
        LocalDate today = LocalDate.now();
        LocalDate deadline = today.plusDays(Math.max(0, withinDays));
        return claims.stream()
                .filter(claim -> claim.getStatus() == ClaimStatus.PREFILLED)
                .filter(claim -> claim.getBenefit() != null && claim.getTransaction() != null)
                .filter(claim -> {
                    LocalDate expiresOn = coverageWindowPolicy.expiresOn(
                            claim.getBenefit(), claim.getTransaction().getPurchaseDate());
                    return expiresOn != null && !expiresOn.isBefore(today) && !expiresOn.isAfter(deadline);
                })
                .sorted(Comparator.comparing(claim -> coverageWindowPolicy.expiresOn(
                        claim.getBenefit(), claim.getTransaction().getPurchaseDate())))
                .toList();
    }
}
