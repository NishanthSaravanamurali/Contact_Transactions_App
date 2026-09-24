package com.oracle.transactionmicroservice.dto.response;

import com.oracle.transactionmicroservice.enums.TransactionStatus;
import com.oracle.transactionmicroservice.enums.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// This returns account and wallet IDs without exposing JPA entities.
// sourceWalletId is null for A2W,
// sourceAccountId is null for W2W,
// destinationUserId is the owner recorded on destinationWalletId,
// and completedAt is null until completion.
public record TransactionResponse(
        Long transactionId,
        TransactionType transactionType,
        Long sourceWalletId,
        Long sourceAccountId,
        Long destinationWalletId,
        Long destinationUserId,
        BigDecimal amount,
        TransactionStatus status,
        LocalDateTime createdAt,
        LocalDateTime completedAt
) {
}
