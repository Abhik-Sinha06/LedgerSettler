package com.abhik.ledgerlens.repository;

import com.abhik.ledgerlens.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Optional<Transaction> findByReferenceId(String referenceId);

    Optional<Transaction> findBySenderAccountIdAndIdempotencyKey(UUID senderAccountId, String idempotencyKey);

    @Query("SELECT t FROM Transaction t WHERE t.senderAccount.id IN :accountIds OR t.receiverAccount.id IN :accountIds")
    Page<Transaction> findBySenderAccountIdInOrReceiverAccountIdIn(@Param("accountIds") List<UUID> accountIds, Pageable pageable);
}
