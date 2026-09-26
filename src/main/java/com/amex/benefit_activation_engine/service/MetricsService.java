package com.amex.benefit_activation_engine.service;

import com.amex.benefit_activation_engine.dto.MetricsResponse;
import com.amex.benefit_activation_engine.model.Claim;
import com.amex.benefit_activation_engine.model.ClaimStatus;
import com.amex.benefit_activation_engine.model.Transaction;
import com.amex.benefit_activation_engine.model.TransactionStatus;
import com.amex.benefit_activation_engine.repository.ClaimRepository;
import com.amex.benefit_activation_engine.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Task 5 metrics: quantifies detection accuracy and the reduction in unclaimed
 * benefit value the engine has driven.
 *
 * <p>Model: every matched purchase yields a PREFILLED claim, so the sum of all
 * claim amounts is the total <em>detectable</em> benefit value. Value on claims
 * that have moved past PREFILLED counts as <em>claimed</em> (no longer sitting
 * unused); the headline figure is that claimed value as a percentage of the
 * detectable total.</p>
 */
@Service
@RequiredArgsConstructor
public class MetricsService {

    private final ClaimRepository claimRepository;
    private final TransactionRepository transactionRepository;

    @Transactional(readOnly = true)
    public MetricsResponse compute() {
        List<Transaction> transactions = transactionRepository.findAll();
        long totalTransactions = transactions.size();
        long matchedTransactions = transactions.stream()
                .filter(t -> t.getStatus() == TransactionStatus.MATCHED)
                .count();

        List<Claim> claims = claimRepository.findAll();

        Map<ClaimStatus, Long> claimsByStatus = new EnumMap<>(ClaimStatus.class);
        for (ClaimStatus status : ClaimStatus.values()) {
            claimsByStatus.put(status, 0L);
        }
        for (Claim claim : claims) {
            claimsByStatus.merge(claim.getStatus(), 1L, Long::sum);
        }

        BigDecimal detectableValue = sum(claims, c -> true);
        BigDecimal claimedValue = sum(claims, c -> c.getStatus() != ClaimStatus.PREFILLED);
        BigDecimal paidValue = sum(claims, c -> c.getStatus() == ClaimStatus.PAID);
        BigDecimal unclaimedValue = detectableValue.subtract(claimedValue);

        return new MetricsResponse(
                totalTransactions,
                matchedTransactions,
                percentage(matchedTransactions, totalTransactions),
                claims.size(),
                claimsByStatus,
                detectableValue,
                claimedValue,
                paidValue,
                unclaimedValue,
                percentage(claimedValue, detectableValue),
                currencyOf(claims));
    }

    // ------------------------------------------------------------------

    private BigDecimal sum(List<Claim> claims, java.util.function.Predicate<Claim> filter) {
        return claims.stream()
                .filter(filter)
                .map(Claim::getClaimAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** part / whole as a percentage rounded to 2 decimals (0 when whole is 0). */
    private double percentage(BigDecimal part, BigDecimal whole) {
        if (whole.signum() == 0) {
            return 0.0;
        }
        return part.divide(whole, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private double percentage(long part, long whole) {
        if (whole == 0) {
            return 0.0;
        }
        return percentage(BigDecimal.valueOf(part), BigDecimal.valueOf(whole));
    }

    /** The shared currency of the claims, or "MIXED" if they differ, null if none. */
    private String currencyOf(List<Claim> claims) {
        Set<String> currencies = new TreeSet<>();
        for (Claim claim : claims) {
            if (claim.getTransaction() != null && claim.getTransaction().getCurrency() != null) {
                currencies.add(claim.getTransaction().getCurrency());
            }
        }
        if (currencies.isEmpty()) {
            return null;
        }
        return currencies.size() == 1 ? currencies.iterator().next() : "MIXED";
    }
}
