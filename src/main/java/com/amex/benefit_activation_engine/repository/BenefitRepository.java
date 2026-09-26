package com.amex.benefit_activation_engine.repository;

import com.amex.benefit_activation_engine.model.Benefit;
import com.amex.benefit_activation_engine.model.BenefitType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BenefitRepository extends JpaRepository<Benefit, Long> {

    Optional<Benefit> findByType(BenefitType type);
}
