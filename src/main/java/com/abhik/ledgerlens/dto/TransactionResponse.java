package com.abhik.ledgerlens.dto;

import com.abhik.ledgerlens.entity.TransactionStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TransactionResponse {
    private UUID transactionId;
    private UUID senderAccountId;
    private UUID receiverAccountId;
    private String currency;
    private TransactionStatus status;
    private BigDecimal amount;
    private Instant createdAt;
    private boolean replayed;
}
