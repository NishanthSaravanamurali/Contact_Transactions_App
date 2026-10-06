package com.oracle.transactionmicroservice.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateWalletRequest(

        @NotNull(message = "User ID is required.")
        @Positive(message = "User ID must be greater than zero.")
        Long userId
) {
}