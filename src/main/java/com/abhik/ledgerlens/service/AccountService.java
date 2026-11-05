package com.abhik.ledgerlens.service;

import com.abhik.ledgerlens.dto.AccountResponse;
import com.abhik.ledgerlens.entity.Account;
import com.abhik.ledgerlens.entity.AccountStatus;
import com.abhik.ledgerlens.entity.AccountType;
import com.abhik.ledgerlens.entity.User;
import com.abhik.ledgerlens.repository.AccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccountService {
    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
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
}
