package com.oracle.transactionmicroservice.entity;

import com.oracle.transactionmicroservice.enums.TransactionStatus;
import com.oracle.transactionmicroservice.enums.TransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transaction_id", nullable = false)
    private Long transactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 3)
    private TransactionType transactionType;

//    sourceWallet is used only for W2W; sourceAccount is used only for A2W. The database check constraint enforces that rule.
//    hence these 2 dont have (optional = false) while destination_wallet_id has it
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_wallet_id")
    private Wallet sourceWallet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_account_id")
    private Account sourceAccount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destination_wallet_id", nullable = false)
    private Wallet destinationWallet;

    @Column(name = "amount", nullable = false, precision = 20, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TransactionStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    protected Transaction() {
        // Required by JPA.
    }

    public Transaction(
            TransactionType transactionType,
            Wallet sourceWallet,
            Account sourceAccount,
            Wallet destinationWallet,
            BigDecimal amount
    ) {
        this.transactionType = transactionType;
        this.sourceWallet = sourceWallet;
        this.sourceAccount = sourceAccount;
        this.destinationWallet = destinationWallet;
        this.amount = amount;
        this.status = TransactionStatus.PENDING;
    }

    @PrePersist
    private void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public void markCompleted() {
        this.status = TransactionStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    public void markFailed() {
        this.status = TransactionStatus.FAILED;
        this.completedAt = null;
    }

    public Long getTransactionId() {
        return transactionId;
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public Wallet getSourceWallet() {
        return sourceWallet;
    }

    public Account getSourceAccount() {
        return sourceAccount;
    }

    public Wallet getDestinationWallet() {
        return destinationWallet;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }
}