package com.abhik.ledgerlens.service;

import com.abhik.ledgerlens.dto.TransactionRequest;
import com.abhik.ledgerlens.dto.TransactionResponse;
import com.abhik.ledgerlens.entity.*;
import com.abhik.ledgerlens.exception.*;
import com.abhik.ledgerlens.repository.AccountRepository;
import com.abhik.ledgerlens.repository.LedgerEntryRepository;
import com.abhik.ledgerlens.repository.TransactionRepository;
import com.abhik.ledgerlens.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private TransferService transferService;

    private User authUser;
    private final String authEmail = "sender@example.com";

    @BeforeEach
    void setUpSecurityContext() {
        authUser = User.builder()
                .id(UUID.randomUUID())
                .name("Sender User")
                .email(authEmail)
                .passwordHash("hashed")
                .role(Role.USER)
                .build();

        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(
                new UsernamePasswordAuthenticationToken(authEmail, "credentials")
        );
        SecurityContextHolder.setContext(securityContext);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsSelfTransferImmediately() {
        UUID accountId = UUID.randomUUID();
        TransactionRequest request = new TransactionRequest(accountId, "INR", BigDecimal.valueOf(100));

        assertThrows(SelfTransferException.class, () ->
                transferService.transfer(accountId, request, "idem-1")
        );

        verifyNoInteractions(accountRepository);
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void deterministicLockOrder_locksLowerIdFirst() {
        UUID id1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID id2 = UUID.fromString("00000000-0000-0000-0000-000000000002");

        // Sender is id2 (larger), receiver is id1 (smaller)
        UUID senderId = id2;
        UUID receiverId = id1;

        Account senderAccount = Account.builder()
                .id(senderId)
                .user(authUser)
                .currency("INR")
                .balance(BigDecimal.valueOf(500))
                .status(AccountStatus.ACTIVE)
                .build();

        Account receiverAccount = Account.builder()
                .id(receiverId)
                .user(User.builder().id(UUID.randomUUID()).email("receiver@example.com").name("Receiver").build())
                .currency("INR")
                .balance(BigDecimal.valueOf(100))
                .status(AccountStatus.ACTIVE)
                .build();

        when(accountRepository.findByIdWithLock(id1)).thenReturn(Optional.of(receiverAccount));
        when(accountRepository.findByIdWithLock(id2)).thenReturn(Optional.of(senderAccount));
        when(userRepository.findByEmail(authEmail)).thenReturn(Optional.of(authUser));
        when(transactionRepository.findBySenderAccountIdAndIdempotencyKey(senderId, "idem-lock"))
                .thenReturn(Optional.empty());
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction t = invocation.getArgument(0);
            t.setId(UUID.randomUUID());
            t.setCreatedAt(Instant.now());
            return t;
        });

        TransactionRequest request = new TransactionRequest(receiverId, "INR", BigDecimal.valueOf(50));
        transferService.transfer(senderId, request, "idem-lock");

        InOrder inOrder = inOrder(accountRepository);
        inOrder.verify(accountRepository).findByIdWithLock(id1);
        inOrder.verify(accountRepository).findByIdWithLock(id2);
    }

    @Test
    void throwsAccountNotFoundException_whenAccountMissing() {
        UUID senderId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();

        when(accountRepository.findByIdWithLock(any())).thenReturn(Optional.empty());

        TransactionRequest request = new TransactionRequest(receiverId, "INR", BigDecimal.valueOf(100));

        assertThrows(AccountNotFoundException.class, () ->
                transferService.transfer(senderId, request, "idem-2")
        );
    }

    @Test
    void throwsUnauthorizedAccountAccessException_whenSenderNotOwnedByUser() {
        UUID senderId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();

        User differentUser = User.builder().id(UUID.randomUUID()).email("other@example.com").name("Other").build();

        Account senderAccount = Account.builder()
                .id(senderId)
                .user(differentUser) // Not authUser
                .currency("INR")
                .balance(BigDecimal.valueOf(1000))
                .status(AccountStatus.ACTIVE)
                .build();

        Account receiverAccount = Account.builder()
                .id(receiverId)
                .user(User.builder().id(UUID.randomUUID()).email("r@example.com").name("R").build())
                .currency("INR")
                .balance(BigDecimal.ZERO)
                .status(AccountStatus.ACTIVE)
                .build();

        when(accountRepository.findByIdWithLock(senderId)).thenReturn(Optional.of(senderAccount));
        when(accountRepository.findByIdWithLock(receiverId)).thenReturn(Optional.of(receiverAccount));
        when(userRepository.findByEmail(authEmail)).thenReturn(Optional.of(authUser));

        TransactionRequest request = new TransactionRequest(receiverId, "INR", BigDecimal.valueOf(50));

        assertThrows(UnauthorizedAccountAccessException.class, () ->
                transferService.transfer(senderId, request, "idem-idor")
        );
    }

    @Test
    void idempotentReplay_returnsExistingTransactionWithoutMutations() {
        UUID senderId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();
        String idempotencyKey = "idem-replay";

        Account sender = Account.builder().id(senderId).user(authUser).currency("INR").balance(BigDecimal.valueOf(500)).status(AccountStatus.ACTIVE).build();
        Account receiver = Account.builder().id(receiverId).user(User.builder().id(UUID.randomUUID()).name("R").email("r@ex.com").build()).currency("INR").balance(BigDecimal.ZERO).status(AccountStatus.ACTIVE).build();

        when(accountRepository.findByIdWithLock(senderId)).thenReturn(Optional.of(sender));
        when(accountRepository.findByIdWithLock(receiverId)).thenReturn(Optional.of(receiver));
        when(userRepository.findByEmail(authEmail)).thenReturn(Optional.of(authUser));

        UUID existingTxId = UUID.randomUUID();
        Transaction existing = Transaction.builder()
                .id(existingTxId)
                .senderAccount(sender)
                .receiverAccount(receiver)
                .idempotencyKey(idempotencyKey)
                .amount(BigDecimal.valueOf(100))
                .currency("INR")
                .status(TransactionStatus.COMPLETED)
                .createdAt(Instant.now())
                .build();

        when(transactionRepository.findBySenderAccountIdAndIdempotencyKey(senderId, idempotencyKey))
                .thenReturn(Optional.of(existing));

        TransactionRequest request = new TransactionRequest(receiverId, "INR", BigDecimal.valueOf(100));
        TransactionResponse response = transferService.transfer(senderId, request, idempotencyKey);

        assertEquals(existingTxId, response.getTransactionId());
        assertEquals(BigDecimal.valueOf(500), sender.getBalance(), "Sender balance must not change on replay");
        assertEquals(BigDecimal.ZERO, receiver.getBalance(), "Receiver balance must not change on replay");
        verify(accountRepository, never()).save(any());
        verifyNoInteractions(ledgerEntryRepository);
    }

    @Test
    void idempotencyConflict_throwsWhenPayloadDiffers() {
        UUID senderId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();
        String idempotencyKey = "idem-conflict";

        Account sender = Account.builder().id(senderId).user(authUser).currency("INR").balance(BigDecimal.valueOf(500)).status(AccountStatus.ACTIVE).build();
        Account receiver = Account.builder().id(receiverId).user(User.builder().id(UUID.randomUUID()).name("R").email("r@ex.com").build()).currency("INR").balance(BigDecimal.ZERO).status(AccountStatus.ACTIVE).build();

        when(accountRepository.findByIdWithLock(senderId)).thenReturn(Optional.of(sender));
        when(accountRepository.findByIdWithLock(receiverId)).thenReturn(Optional.of(receiver));
        when(userRepository.findByEmail(authEmail)).thenReturn(Optional.of(authUser));

        Transaction existing = Transaction.builder()
                .id(UUID.randomUUID())
                .senderAccount(sender)
                .receiverAccount(receiver)
                .idempotencyKey(idempotencyKey)
                .amount(BigDecimal.valueOf(100))
                .currency("INR")
                .build();

        when(transactionRepository.findBySenderAccountIdAndIdempotencyKey(senderId, idempotencyKey))
                .thenReturn(Optional.of(existing));

        // Request with different amount (200 instead of 100)
        TransactionRequest conflictingRequest = new TransactionRequest(receiverId, "INR", BigDecimal.valueOf(200));

        assertThrows(IdempotencyConflictException.class, () ->
                transferService.transfer(senderId, conflictingRequest, idempotencyKey)
        );
    }

    @Test
    void throwsAccountInactiveException_whenSenderNotActive() {
        UUID senderId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();

        Account sender = Account.builder().id(senderId).user(authUser).currency("INR").balance(BigDecimal.valueOf(500)).status(AccountStatus.FROZEN).build();
        Account receiver = Account.builder().id(receiverId).user(User.builder().id(UUID.randomUUID()).name("R").email("r@ex.com").build()).currency("INR").balance(BigDecimal.ZERO).status(AccountStatus.ACTIVE).build();

        when(accountRepository.findByIdWithLock(senderId)).thenReturn(Optional.of(sender));
        when(accountRepository.findByIdWithLock(receiverId)).thenReturn(Optional.of(receiver));
        when(userRepository.findByEmail(authEmail)).thenReturn(Optional.of(authUser));
        when(transactionRepository.findBySenderAccountIdAndIdempotencyKey(senderId, "key")).thenReturn(Optional.empty());

        TransactionRequest request = new TransactionRequest(receiverId, "INR", BigDecimal.valueOf(50));

        assertThrows(AccountInactiveException.class, () ->
                transferService.transfer(senderId, request, "key")
        );
    }

    @Test
    void throwsCurrencyMismatchException_whenCurrenciesDiffer() {
        UUID senderId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();

        Account sender = Account.builder().id(senderId).user(authUser).currency("INR").balance(BigDecimal.valueOf(500)).status(AccountStatus.ACTIVE).build();
        Account receiver = Account.builder().id(receiverId).user(User.builder().id(UUID.randomUUID()).name("R").email("r@ex.com").build()).currency("USD").balance(BigDecimal.ZERO).status(AccountStatus.ACTIVE).build();

        when(accountRepository.findByIdWithLock(senderId)).thenReturn(Optional.of(sender));
        when(accountRepository.findByIdWithLock(receiverId)).thenReturn(Optional.of(receiver));
        when(userRepository.findByEmail(authEmail)).thenReturn(Optional.of(authUser));
        when(transactionRepository.findBySenderAccountIdAndIdempotencyKey(senderId, "key")).thenReturn(Optional.empty());

        TransactionRequest request = new TransactionRequest(receiverId, "INR", BigDecimal.valueOf(50));

        assertThrows(CurrencyMismatchException.class, () ->
                transferService.transfer(senderId, request, "key")
        );
    }

    @Test
    void throwsInsufficientFundsException_whenBalanceTooLow() {
        UUID senderId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();

        Account sender = Account.builder().id(senderId).user(authUser).currency("INR").balance(BigDecimal.valueOf(20)).status(AccountStatus.ACTIVE).build();
        Account receiver = Account.builder().id(receiverId).user(User.builder().id(UUID.randomUUID()).name("R").email("r@ex.com").build()).currency("INR").balance(BigDecimal.ZERO).status(AccountStatus.ACTIVE).build();

        when(accountRepository.findByIdWithLock(senderId)).thenReturn(Optional.of(sender));
        when(accountRepository.findByIdWithLock(receiverId)).thenReturn(Optional.of(receiver));
        when(userRepository.findByEmail(authEmail)).thenReturn(Optional.of(authUser));
        when(transactionRepository.findBySenderAccountIdAndIdempotencyKey(senderId, "key")).thenReturn(Optional.empty());

        TransactionRequest request = new TransactionRequest(receiverId, "INR", BigDecimal.valueOf(50));

        assertThrows(InsufficientFundsException.class, () ->
                transferService.transfer(senderId, request, "key")
        );
    }

    @Test
    void successfulTransfer_updatesBalancesAndCreatesDoubleEntryLedger() {
        UUID senderId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();
        BigDecimal transferAmount = BigDecimal.valueOf(150);

        Account sender = Account.builder().id(senderId).user(authUser).currency("INR").balance(BigDecimal.valueOf(500)).status(AccountStatus.ACTIVE).build();
        Account receiver = Account.builder().id(receiverId).user(User.builder().id(UUID.randomUUID()).name("R").email("r@ex.com").build()).currency("INR").balance(BigDecimal.valueOf(50)).status(AccountStatus.ACTIVE).build();

        when(accountRepository.findByIdWithLock(senderId)).thenReturn(Optional.of(sender));
        when(accountRepository.findByIdWithLock(receiverId)).thenReturn(Optional.of(receiver));
        when(userRepository.findByEmail(authEmail)).thenReturn(Optional.of(authUser));
        when(transactionRepository.findBySenderAccountIdAndIdempotencyKey(senderId, "tx-success")).thenReturn(Optional.empty());

        UUID txId = UUID.randomUUID();
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction t = invocation.getArgument(0);
            t.setId(txId);
            t.setCreatedAt(Instant.now());
            return t;
        });

        TransactionRequest request = new TransactionRequest(receiverId, "INR", transferAmount);
        TransactionResponse response = transferService.transfer(senderId, request, "tx-success");

        // Verify balance mutations
        assertEquals(BigDecimal.valueOf(350), sender.getBalance());
        assertEquals(BigDecimal.valueOf(200), receiver.getBalance());

        // Verify account persists
        verify(accountRepository).save(sender);
        verify(accountRepository).save(receiver);

        // Verify transaction persist
        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(txCaptor.capture());
        Transaction savedTx = txCaptor.getValue();
        assertEquals(TransactionStatus.COMPLETED, savedTx.getStatus());
        assertEquals(transferAmount, savedTx.getAmount());
        assertNotNull(savedTx.getReferenceId());

        // Verify double-entry ledger entries
        ArgumentCaptor<LedgerEntry> ledgerCaptor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository, times(2)).save(ledgerCaptor.capture());

        var entries = ledgerCaptor.getAllValues();
        LedgerEntry debit = entries.get(0);
        LedgerEntry credit = entries.get(1);

        assertEquals(LedgerEntryType.DEBIT, debit.getEntryType());
        assertEquals(senderId, debit.getAccount().getId());
        assertEquals(transferAmount, debit.getAmount());
        assertEquals(BigDecimal.valueOf(350), debit.getBalanceAfter());

        assertEquals(LedgerEntryType.CREDIT, credit.getEntryType());
        assertEquals(receiverId, credit.getAccount().getId());
        assertEquals(transferAmount, credit.getAmount());
        assertEquals(BigDecimal.valueOf(200), credit.getBalanceAfter());

        // Verify response
        assertNotNull(response);
        assertEquals(txId, response.getTransactionId());
        assertEquals(TransactionStatus.COMPLETED, response.getStatus());
    }
}
