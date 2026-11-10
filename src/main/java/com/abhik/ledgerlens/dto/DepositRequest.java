package com.abhik.ledgerlens.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DepositRequest {

    @NotNull
    @DecimalMin("0.01")
    @Digits(integer = 15, fraction = 4)
    private BigDecimal amount;
}
