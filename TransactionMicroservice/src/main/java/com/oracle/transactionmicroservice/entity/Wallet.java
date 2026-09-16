package com.oracle.transactionmicroservice.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table
public class Wallet {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "wallet_id")
    private Integer walletId;

    @Column(name = "user_id",nullable = false)
    private Long userId;

    @Column(name = "created_at",nullable = false)
    private LocalDateTime created_at ;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updated_at;

    public Wallet() {
    }

    public Wallet(Long userId) {
        this.userId = userId;
        
    }

    public Integer getWalletId() {
        return walletId;
    }

    public void setWalletId(Integer walletId) {
        this.walletId = walletId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public LocalDateTime getCreated_at() {
        return created_at;
    }

    public void setCreated_at(LocalDateTime created_at) {
        this.created_at = created_at;
    }

    public LocalDateTime getUpdated_at() {
        return updated_at;
    }

    public void setUpdated_at(LocalDateTime updated_at) {
        this.updated_at = updated_at;
    }
}
