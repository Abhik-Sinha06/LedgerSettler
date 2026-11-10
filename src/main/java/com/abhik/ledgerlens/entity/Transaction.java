package com.abhik.ledgerlens.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.*;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "transactions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_transactions_sender_idempotency",
                        columnNames = {"sender_account_id", "idempotency_key"}
                ),
                @UniqueConstraint(
                        name = "uk_transactions_deposit_idempotency",
                        columnNames = {"receiver_account_id", "deposit_idempotency_key"}
                )
        }
)
@Check(constraints = "amount > 0")
@Check(constraints = "sender_account_id <> receiver_account_id")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "sender_account_id",
            foreignKey = @ForeignKey(name = "fk_transactions_sender_account_id")
    )
    private Account senderAccount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "receiver_account_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_transactions_receiver_account_id")
    )
    private Account receiverAccount;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "reference_id", nullable = false, unique = true)
    private String referenceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false)
    @Builder.Default
    private TransactionType transactionType = TransactionType.TRANSFER;

    @Column(name = "deposit_idempotency_key")
    private String depositIdempotencyKey;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private TransactionStatus status = TransactionStatus.PENDING;

    @Column(name = "failure_reason")
    private String failureReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
