package com.abhik.ledgerlens.controller;

import com.abhik.ledgerlens.dto.TransactionRequest;
import com.abhik.ledgerlens.dto.TransactionResponse;
import com.abhik.ledgerlens.exception.AccountNotFoundException;
import com.abhik.ledgerlens.repository.AccountRepository;
import com.abhik.ledgerlens.service.TransactionService;
import com.abhik.ledgerlens.service.TransferService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/transactions")
@Validated
public class TransactionController {

    private final TransferService transferService;
    private final TransactionService transactionService;
    private final AccountRepository accountRepository;

    public TransactionController(TransferService transferService,
                                 TransactionService transactionService,
                                 AccountRepository accountRepository) {
        this.transferService = transferService;
        this.transactionService = transactionService;
        this.accountRepository = accountRepository;
    }


    @PostMapping("/transfer")
    public ResponseEntity<TransactionResponse> transfer(
            @RequestHeader("Idempotency-Key") @NotBlank(message = "Idempotency-Key header cannot be blank") String idempotencyKey,
            @Valid @RequestBody TransactionRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        UUID senderAccountId = accountRepository
                .findFirstByUserEmailAndTypeAndStatusOrderByCreatedAtAsc(
                        userDetails.getUsername(),
                        com.abhik.ledgerlens.entity.AccountType.WALLET,
                        com.abhik.ledgerlens.entity.AccountStatus.ACTIVE)
                .orElseThrow(() -> new AccountNotFoundException("Active wallet not found for user"))
                .getId();

        TransactionResponse response = transferService.transfer(senderAccountId, request, idempotencyKey);
        if (response.isReplayed()) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/history")
    public ResponseEntity<Page<TransactionResponse>> history(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) int size,
            @AuthenticationPrincipal UserDetails userDetails) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<TransactionResponse> pageResult = transactionService.getUserTransactionHistory(pageable);
        return ResponseEntity.ok(pageResult);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponse> getById(
            @PathVariable("id") UUID transactionId,
            @AuthenticationPrincipal UserDetails userDetails) {
        TransactionResponse response = transactionService.getTransactionById(transactionId);
        return ResponseEntity.ok(response);
    }
}

