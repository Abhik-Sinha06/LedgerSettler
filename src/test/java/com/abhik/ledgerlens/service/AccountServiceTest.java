package com.abhik.ledgerlens.service;

import com.abhik.ledgerlens.dto.DepositRequest;
import com.abhik.ledgerlens.dto.DepositResponse;
import com.abhik.ledgerlens.entity.Account;
import com.abhik.ledgerlens.entity.AccountStatus;
import com.abhik.ledgerlens.entity.AccountType;
import com.abhik.ledgerlens.entity.LedgerEntry;
import com.abhik.ledgerlens.entity.LedgerEntryType;
import com.abhik.ledgerlens.entity.Transaction;
import com.abhik.ledgerlens.entity.TransactionStatus;
import com.abhik.ledgerlens.entity.TransactionType;
import com.abhik.ledgerlens.entity.User;
import com.abhik.ledgerlens.exception.IdempotencyConflictException;
import com.abhik.ledgerlens.repository.AccountRepository;
import com.abhik.ledgerlens.repository.LedgerEntryRepository;
import com.abhik.ledgerlens.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @InjectMocks
    private AccountService accountService;

    private Account account;
    private UUID accountId;

    @BeforeEach
    void setUp() {
        accountId = UUID.randomUUID();
        account = Account.builder()
                .id(accountId)
                .user(User.builder().id(UUID.randomUUID()).email("user@example.com").build())
                .currency("INR")
                .type(AccountType.WALLET)
                .balance(new BigDecimal("100.00"))
                .status(AccountStatus.ACTIVE)
                .build();

        when(accountRepository.findFirstByUserEmailAndTypeAndStatusOrderByCreatedAtAsc(
                "user@example.com", AccountType.WALLET, AccountStatus.ACTIVE)).thenReturn(Optional.of(account));
        when(accountRepository.findByIdWithLock(accountId)).thenReturn(Optional.of(account));
    }

    @Test
    void depositCreatesCompletedTransactionAndOneCreditEntry() {
        when(transactionRepository.findByReceiverAccountIdAndTransactionTypeAndIdempotencyKey(
                accountId, TransactionType.DEPOSIT, "deposit-key")).thenReturn(Optional.empty());
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction transaction = invocation.getArgument(0);
            transaction.setId(UUID.randomUUID());
            return transaction;
        });

        DepositResponse response = accountService.deposit(
                "user@example.com", new DepositRequest(new BigDecimal("25.50")), "deposit-key");

        assertEquals(new BigDecimal("125.50"), account.getBalance());
        assertEquals(accountId, response.getAccountId());
        assertEquals(new BigDecimal("125.50"), response.getNewBalance());
        assertEquals(TransactionStatus.COMPLETED, response.getStatus());

        ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(transactionCaptor.capture());
        Transaction transaction = transactionCaptor.getValue();
        assertNull(transaction.getSenderAccount());
        assertEquals(account, transaction.getReceiverAccount());
        assertEquals(TransactionType.DEPOSIT, transaction.getTransactionType());
        assertEquals("deposit-key", transaction.getDepositIdempotencyKey());
        assertEquals(new BigDecimal("25.50"), transaction.getAmount());
        assertEquals("INR", transaction.getCurrency());

        ArgumentCaptor<LedgerEntry> ledgerCaptor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository).save(ledgerCaptor.capture());
        LedgerEntry entry = ledgerCaptor.getValue();
        assertEquals(LedgerEntryType.CREDIT, entry.getEntryType());
        assertEquals(account, entry.getAccount());
        assertEquals(new BigDecimal("25.50"), entry.getAmount());
        assertEquals(new BigDecimal("125.50"), entry.getBalanceAfter());
    }

    @Test
    void identicalIdempotentRequestReturnsOriginalDepositBalance() {
        UUID transactionId = UUID.randomUUID();
        Transaction existing = Transaction.builder()
                .id(transactionId)
                .receiverAccount(account)
                .idempotencyKey("deposit-key")
                .amount(new BigDecimal("25.00"))
                .currency("INR")
                .status(TransactionStatus.COMPLETED)
                .transactionType(TransactionType.DEPOSIT)
                .build();
        LedgerEntry originalCredit = LedgerEntry.builder()
                .entryType(LedgerEntryType.CREDIT)
                .balanceAfter(new BigDecimal("125.00"))
                .build();
        when(transactionRepository.findByReceiverAccountIdAndTransactionTypeAndIdempotencyKey(
                accountId, TransactionType.DEPOSIT, "deposit-key")).thenReturn(Optional.of(existing));
        when(ledgerEntryRepository.findByTransactionId(transactionId)).thenReturn(List.of(originalCredit));

        DepositResponse response = accountService.deposit(
                "user@example.com", new DepositRequest(new BigDecimal("25.00")), "deposit-key");

        assertEquals(transactionId, response.getTransactionId());
        assertEquals(new BigDecimal("125.00"), response.getNewBalance());
        assertEquals(new BigDecimal("100.00"), account.getBalance());
        verify(accountRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
        verify(ledgerEntryRepository, never()).save(any());
    }

    @Test
    void reusedIdempotencyKeyWithDifferentAmountConflicts() {
        Transaction existing = Transaction.builder()
                .id(UUID.randomUUID())
                .receiverAccount(account)
                .amount(new BigDecimal("25.00"))
                .build();
        when(transactionRepository.findByReceiverAccountIdAndTransactionTypeAndIdempotencyKey(
                accountId, TransactionType.DEPOSIT, "deposit-key")).thenReturn(Optional.of(existing));

        assertThrows(IdempotencyConflictException.class, () -> accountService.deposit(
                "user@example.com", new DepositRequest(new BigDecimal("26.00")), "deposit-key"));
        assertEquals(new BigDecimal("100.00"), account.getBalance());
        verify(accountRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
        verify(ledgerEntryRepository, never()).save(any());
    }
}
