package com.abhik.ledgerlens.controller;

import com.abhik.ledgerlens.dto.AccountResponse;
import com.abhik.ledgerlens.service.AccountService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/me")
    public AccountResponse getMyAccount(@AuthenticationPrincipal UserDetails user) {
        return accountService.getMyAccount(user.getUsername());
    }

}
