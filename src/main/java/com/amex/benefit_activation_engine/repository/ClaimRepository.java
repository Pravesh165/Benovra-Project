package com.amex.benefit_activation_engine.repository;

import com.amex.benefit_activation_engine.model.Claim;
import com.amex.benefit_activation_engine.model.ClaimStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClaimRepository extends JpaRepository<Claim, Long> {

    List<Claim> findByStatus(ClaimStatus status);

    List<Claim> findByTransactionId(Long transactionId);

    /** All claims belonging to a card member (via the linked transaction). */
    List<Claim> findByTransactionCardMemberIdOrderByCreatedAtDesc(String cardMemberId);

    /** A card member's claims filtered by workflow status. */
    List<Claim> findByTransactionCardMemberIdAndStatusOrderByCreatedAtDesc(
            String cardMemberId, ClaimStatus status);
}
