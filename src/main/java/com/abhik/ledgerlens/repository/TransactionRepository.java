package com.abhik.ledgerlens.repository;

import com.abhik.ledgerlens.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Optional<Transaction> findByReferenceId(String referenceId);

    Optional<Transaction> findBySenderAccountIdAndIdempotencyKey(UUID senderAccountId, String idempotencyKey);
}
