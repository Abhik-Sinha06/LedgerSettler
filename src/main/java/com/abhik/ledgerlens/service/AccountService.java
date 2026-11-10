package com.abhik.ledgerlens.service;

import com.abhik.ledgerlens.dto.AccountResponse;
import com.abhik.ledgerlens.dto.DepositRequest;
import com.abhik.ledgerlens.dto.DepositResponse;
import com.abhik.ledgerlens.entity.*;
import com.abhik.ledgerlens.exception.AccountNotFoundException;
import com.abhik.ledgerlens.exception.IdempotencyConflictException;
import com.abhik.ledgerlens.repository.AccountRepository;
import com.abhik.ledgerlens.repository.LedgerEntryRepository;
import com.abhik.ledgerlens.repository.TransactionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class AccountService {
    private static final BigDecimal MINIMUM_DEPOSIT = new BigDecimal("0.01");

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    public AccountService(AccountRepository accountRepository,
                          TransactionRepository transactionRepository,
                          LedgerEntryRepository ledgerEntryRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    public Account createAccount(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null.");
        }

        Account account = Account.builder()
                .user(user)
                .type(AccountType.WALLET)
                .build();
        return accountRepository.save(account);
    }

    @Transactional(readOnly = true)
    public AccountResponse getMyAccount(String email) {
        Account account = accountRepository
                .findFirstByUserEmailAndTypeAndStatusOrderByCreatedAtAsc(
                        email, AccountType.WALLET, AccountStatus.ACTIVE
                )
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Active wallet account not found."
                ));

        return new AccountResponse(
                account.getId(),
                account.getUser().getName(),
                account.getType(),
                account.getBalance()
        );
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DepositResponse deposit(String email, DepositRequest request, String idempotencyKey) {
        Account wallet = accountRepository
                .findFirstByUserEmailAndTypeAndStatusOrderByCreatedAtAsc(email, AccountType.WALLET, AccountStatus.ACTIVE)
                .orElseThrow(() -> new AccountNotFoundException("Active wallet account not found for user."));

        Account account = accountRepository.findByIdWithLock(wallet.getId())
                .orElseThrow(() -> new AccountNotFoundException("Wallet account not found: " + wallet.getId()));


        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Deposit request is required.");
        }

        BigDecimal amount = request.getAmount();
        if (amount == null || amount.compareTo(MINIMUM_DEPOSIT) < 0
                || amount.scale() > 4 || amount.precision() - amount.scale() > 15) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Deposit amount must be between 0.01 and 15 integer digits with at most 4 decimals.");
        }

        var existingTransaction = transactionRepository
                .findByReceiverAccountIdAndTransactionTypeAndIdempotencyKey(
                        account.getId(), TransactionType.DEPOSIT, idempotencyKey);
        if (existingTransaction.isPresent()) {
            Transaction existing = existingTransaction.get();
            if (existing.getAmount().compareTo(amount) != 0) {
                throw new IdempotencyConflictException(
                        "Idempotency key already used with a different deposit amount.");
            }

            LedgerEntry ledgerEntry = ledgerEntryRepository.findByTransactionId(existing.getId())
                    .stream()
                    .filter(entry -> entry.getEntryType() == LedgerEntryType.CREDIT)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "Deposit transaction is missing its credit ledger entry."));
            return toDepositResponse(existing, ledgerEntry.getBalanceAfter());
        }

        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);

        Transaction transaction = transactionRepository.save(Transaction.builder()
                .senderAccount(null)
                .receiverAccount(account)
                .idempotencyKey(idempotencyKey)
                .depositIdempotencyKey(idempotencyKey)
                .referenceId(UUID.randomUUID().toString())
                .transactionType(TransactionType.DEPOSIT)
                .amount(amount)
                .currency(account.getCurrency())
                .status(TransactionStatus.COMPLETED)
                .build());

        ledgerEntryRepository.save(LedgerEntry.builder()
                .transaction(transaction)
                .account(account)
                .entryType(LedgerEntryType.CREDIT)
                .amount(amount)
                .balanceAfter(account.getBalance())
                .build());

        return toDepositResponse(transaction, account.getBalance());
    }

    private DepositResponse toDepositResponse(Transaction transaction, BigDecimal newBalance) {
        return new DepositResponse(
                transaction.getId(),
                transaction.getReceiverAccount().getId(),
                transaction.getAmount(),
                newBalance,
                transaction.getCurrency(),
                transaction.getStatus()
        );
    }
}
