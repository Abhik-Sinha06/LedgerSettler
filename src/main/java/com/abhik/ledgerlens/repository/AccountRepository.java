package com.abhik.ledgerlens.repository;

import com.abhik.ledgerlens.entity.Account;
import com.abhik.ledgerlens.entity.AccountStatus;
import com.abhik.ledgerlens.entity.AccountType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

    List<Account> findByUserId(UUID userId);

    List<Account> findByUserIdAndStatus(UUID userId, AccountStatus status);

    Optional<Account> findFirstByUserEmailAndTypeAndStatusOrderByCreatedAtAsc(
            String email,
            AccountType type,
            AccountStatus status
    );
}
