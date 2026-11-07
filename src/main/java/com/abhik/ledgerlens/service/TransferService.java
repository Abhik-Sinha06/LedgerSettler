package com.abhik.ledgerlens.service;

import com.abhik.ledgerlens.dto.TransactionRequest;
import com.abhik.ledgerlens.dto.TransactionResponse;
import com.abhik.ledgerlens.entity.*;
import com.abhik.ledgerlens.exception.*;
import com.abhik.ledgerlens.repository.AccountRepository;
import com.abhik.ledgerlens.repository.LedgerEntryRepository;
import com.abhik.ledgerlens.repository.TransactionRepository;
import com.abhik.ledgerlens.repository.UserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static com.abhik.ledgerlens.service.TransactionService.getTransactionResponse;

@Service
public class TransferService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final UserRepository userRepository;

    public TransferService(AccountRepository accountRepository,
                           TransactionRepository transactionRepository,
                           LedgerEntryRepository ledgerEntryRepository,
                           UserRepository userRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public TransactionResponse transfer(UUID senderAccountId,
                                        TransactionRequest request,
                                        String idempotencyKey) {

        UUID receiverAccountId = request.getReceiverAccountId();

        //Pre-lock self-transfer check
        if (senderAccountId.equals(receiverAccountId)) {
            throw new SelfTransferException("Sender and receiver accounts must be different.");
        }

        //Deterministic sequential account locking to prevent deadlocks
        UUID firstId;
        UUID secondId;
        if (senderAccountId.compareTo(receiverAccountId) < 0) {
            firstId = senderAccountId;
            secondId = receiverAccountId;
        } else {
            firstId = receiverAccountId;
            secondId = senderAccountId;
        }

        Account firstAccount = accountRepository.findByIdWithLock(firstId)
                .orElseThrow(() -> new AccountNotFoundException(
                        "Account not found: " + firstId));
        Account secondAccount = accountRepository.findByIdWithLock(secondId)
                .orElseThrow(() -> new AccountNotFoundException(
                        "Account not found: " + secondId));

        // Map back to sender / receiver
        Account senderAccount;
        Account receiverAccount;
        if (senderAccountId.equals(firstId)) {
            senderAccount = firstAccount;
            receiverAccount = secondAccount;
        } else {
            senderAccount = secondAccount;
            receiverAccount = firstAccount;
        }

        //  Sender account ownership verification (IDOR defense)
        String authenticatedEmail = SecurityContextHolder.getContext()
                .getAuthentication().getName();
        User authenticatedUser = userRepository.findByEmail(authenticatedEmail)
                .orElseThrow(() -> new UnauthorizedAccountAccessException(
                        "Authenticated user not found."));

        if (!senderAccount.getUser().getId().equals(authenticatedUser.getId())) {
            throw new UnauthorizedAccountAccessException(
                    "Sender account does not belong to the authenticated user.");
        }

        // ── Sender-scoped idempotency check ───────────────────────────────
        Optional<Transaction> existingTxn = transactionRepository
                .findBySenderAccountIdAndIdempotencyKey(senderAccountId, idempotencyKey);

        if (existingTxn.isPresent()) {
            Transaction existing = existingTxn.get();

            boolean amountMatches = existing.getAmount().compareTo(request.getAmount()) == 0;
            boolean currencyMatches = existing.getCurrency().equals(request.getCurrency());
            boolean receiverMatches = existing.getReceiverAccount().getId().equals(receiverAccountId);

            if (!amountMatches || !currencyMatches || !receiverMatches) {
                throw new IdempotencyConflictException(
                        "Idempotency key already used with different transfer parameters.");
            }

            // Idempotent replay — return existing transaction without mutations
            TransactionResponse response = mapToResponse(existing);
            response.setReplayed(true);
            return response;
        }

        // ── Business & financial invariants ────────────────────────────────
        if (senderAccount.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountInactiveException(
                    "Sender account is not active: " + senderAccount.getId());
        }
        if (receiverAccount.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountInactiveException(
                    "Receiver account is not active: " + receiverAccount.getId());
        }

        if (!senderAccount.getCurrency().equals(receiverAccount.getCurrency())) {
            throw new CurrencyMismatchException(
                    "Currency mismatch: sender=" + senderAccount.getCurrency()
                            + ", receiver=" + receiverAccount.getCurrency());
        }

        if (senderAccount.getBalance().compareTo(request.getAmount()) < 0) {
            throw new InsufficientFundsException(
                    "Insufficient funds in account: " + senderAccount.getId());
        }

        // ── State mutation ─────────────────────────────────────────────────
        senderAccount.setBalance(
                senderAccount.getBalance().subtract(request.getAmount()));
        receiverAccount.setBalance(
                receiverAccount.getBalance().add(request.getAmount()));

        accountRepository.save(senderAccount);
        accountRepository.save(receiverAccount);

        // ── Persist transaction ────────────────────────────────────────────
        Transaction transaction = Transaction.builder()
                .senderAccount(senderAccount)
                .receiverAccount(receiverAccount)
                .idempotencyKey(idempotencyKey)
                .referenceId(UUID.randomUUID().toString())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .status(TransactionStatus.COMPLETED)
                .build();

        transaction = transactionRepository.save(transaction);

        // ── Double-entry ledger inserts ─────────────────────────────────────
        LedgerEntry debitEntry = LedgerEntry.builder()
                .transaction(transaction)
                .account(senderAccount)
                .entryType(LedgerEntryType.DEBIT)
                .amount(request.getAmount())
                .balanceAfter(senderAccount.getBalance())
                .build();

        LedgerEntry creditEntry = LedgerEntry.builder()
                .transaction(transaction)
                .account(receiverAccount)
                .entryType(LedgerEntryType.CREDIT)
                .amount(request.getAmount())
                .balanceAfter(receiverAccount.getBalance())
                .build();

        ledgerEntryRepository.save(debitEntry);
        ledgerEntryRepository.save(creditEntry);

        TransactionResponse response = mapToResponse(transaction);
        response.setReplayed(false);
        return response;
    }

    private TransactionResponse mapToResponse(Transaction transaction) {
        return getTransactionResponse(transaction);
    }
}
