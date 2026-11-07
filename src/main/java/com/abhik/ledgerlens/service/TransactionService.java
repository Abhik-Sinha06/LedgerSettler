package com.abhik.ledgerlens.service;

import com.abhik.ledgerlens.dto.TransactionResponse;
import com.abhik.ledgerlens.entity.Account;
import com.abhik.ledgerlens.entity.Transaction;
import com.abhik.ledgerlens.entity.User;
import com.abhik.ledgerlens.exception.TransactionNotFoundException;
import com.abhik.ledgerlens.exception.UnauthorizedAccountAccessException;
import com.abhik.ledgerlens.repository.AccountRepository;
import com.abhik.ledgerlens.repository.TransactionRepository;
import com.abhik.ledgerlens.repository.UserRepository;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TransactionService {

    private static final int MAX_PAGE_SIZE = 50;

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;

    public TransactionService(TransactionRepository transactionRepository,
                              AccountRepository accountRepository,
                              UserRepository userRepository) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> getUserTransactionHistory(Pageable pageable) {
        User authenticatedUser = getAuthenticatedUser();

        int size = Math.min(pageable.getPageSize(), MAX_PAGE_SIZE);
        Pageable constrainedPageable = PageRequest.of(
                        pageable.getPageNumber(),
                        size,
                        pageable.getSort());

        List<UUID> accountIds = accountRepository.findByUserId(authenticatedUser.getId())
                .stream()
                .map(Account::getId)
                .collect(Collectors.toList());

        if (accountIds.isEmpty()) {
            return Page.empty(constrainedPageable);
        }

        Page<Transaction> transactions = transactionRepository
                .findBySenderAccountIdInOrReceiverAccountIdIn(accountIds, constrainedPageable);

        return transactions.map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public TransactionResponse getTransactionById(UUID transactionId) {
        User authenticatedUser = getAuthenticatedUser();

        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(
                        "Transaction not found: " + transactionId));

        Set<UUID> userAccountIds = accountRepository.findByUserId(authenticatedUser.getId())
                .stream()
                .map(Account::getId)
                .collect(Collectors.toSet());

        UUID senderId = transaction.getSenderAccount().getId();
        UUID receiverId = transaction.getReceiverAccount().getId();

        if (!userAccountIds.contains(senderId) && !userAccountIds.contains(receiverId)) {
            throw new UnauthorizedAccountAccessException(
                    "You do not have access to this transaction.");
        }

        return mapToResponse(transaction);
    }

    private User getAuthenticatedUser() {
        String email = SecurityContextHolder.getContext()
                .getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedAccountAccessException(
                        "Authenticated user not found."));
    }

    private TransactionResponse mapToResponse(Transaction transaction) {
        return getTransactionResponse(transaction);
    }

    @NonNull
    static TransactionResponse getTransactionResponse(Transaction transaction) {
        TransactionResponse response = new TransactionResponse();
        response.setTransactionId(transaction.getId());
        response.setSenderAccountId(transaction.getSenderAccount().getId());
        response.setReceiverAccountId(transaction.getReceiverAccount().getId());
        response.setCurrency(transaction.getCurrency());
        response.setAmount(transaction.getAmount());
        response.setStatus(transaction.getStatus());
        response.setCreatedAt(transaction.getCreatedAt());
        return response;
    }
}
