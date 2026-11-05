package com.abhik.ledgerlens.dto;

import com.abhik.ledgerlens.entity.AccountType;
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
public class AccountResponse
{
    private UUID accountId;
    private String accountName;
    private AccountType accountType;
    private BigDecimal accountBalance;

}
