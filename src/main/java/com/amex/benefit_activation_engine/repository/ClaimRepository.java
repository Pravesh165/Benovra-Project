package com.amex.benefit_activation_engine.repository;

import com.amex.benefit_activation_engine.model.Claim;
import com.amex.benefit_activation_engine.model.ClaimStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClaimRepository extends JpaRepository<Claim, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Claim c where c.id = :claimId")
    Optional<Claim> findByIdForUpdate(@Param("claimId") Long claimId);

    List<Claim> findByStatus(ClaimStatus status);

    List<Claim> findByTransactionId(Long transactionId);

    /** All claims belonging to a card member (via the linked transaction). */
    List<Claim> findByTransactionCardMemberIdOrderByCreatedAtDesc(String cardMemberId);

    /** A card member's claims filtered by workflow status. */
    List<Claim> findByTransactionCardMemberIdAndStatusOrderByCreatedAtDesc(
            String cardMemberId, ClaimStatus status);
}
