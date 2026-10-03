package com.amex.benefit_activation_engine.repository;

import com.amex.benefit_activation_engine.model.ClaimDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClaimDocumentRepository extends JpaRepository<ClaimDocument, Long> {
    List<ClaimDocument> findByClaimIdOrderByUploadedAtAscIdAsc(Long claimId);

    Optional<ClaimDocument> findByIdAndClaimId(Long id, Long claimId);
}
