package com.amex.benefit_activation_engine.repository;

import com.amex.benefit_activation_engine.model.Entitlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EntitlementRepository extends JpaRepository<Entitlement, Long> {

    /**
     * Active benefit entitlements for a card product (used during matching).
     * Join-fetches the benefit so callers can read it without an open session.
     */
    @Query("select e from Entitlement e join fetch e.benefit "
            + "where e.cardProduct = :cardProduct and e.active = true")
    List<Entitlement> findByCardProductAndActiveTrue(@Param("cardProduct") String cardProduct);

    List<Entitlement> findByCardProduct(String cardProduct);
}
