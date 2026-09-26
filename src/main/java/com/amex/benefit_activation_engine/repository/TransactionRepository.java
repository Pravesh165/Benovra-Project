package com.amex.benefit_activation_engine.repository;

import com.amex.benefit_activation_engine.model.Transaction;
import com.amex.benefit_activation_engine.model.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByStatus(TransactionStatus status);

    List<Transaction> findByCardMemberId(String cardMemberId);
}
