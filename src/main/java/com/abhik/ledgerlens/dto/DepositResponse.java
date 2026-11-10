package com.abhik.ledgerlens.dto;

import com.abhik.ledgerlens.entity.TransactionStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DepositResponse {
    private UUID transactionId;
    private UUID accountId;
    private BigDecimal amount;
    private BigDecimal newBalance;
    private String currency;
    private TransactionStatus status;
}
