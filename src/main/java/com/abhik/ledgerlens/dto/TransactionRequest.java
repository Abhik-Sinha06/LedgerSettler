package com.abhik.ledgerlens.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class TransactionRequest
{
    @NotNull
    private UUID receiverAccountId;
    @NotBlank
    private String currency;
    @NotNull
    @DecimalMin(value = "0.01", message = "Amount cannot be 0")
    private BigDecimal amount;
}
