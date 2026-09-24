package com.oracle.transactionmicroservice.dto.request;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

//There is intentionally no senderUserId in MakePaymentRequest.
// Later, it will come from the authenticated JWT,
// so a client cannot submit a payment pretending to be another sender.

public record MakePaymentRequest(

        @NotNull(message = "Receiver user ID is required.")
        @Positive(message = "Receiver user ID must be greater than zero.")
        Long receiverUserId,

        @NotNull(message = "Amount is required.")
        @Positive(message = "Amount must be greater than zero.")
        @Digits(
                integer = 18,
                fraction = 2,
                message = "Amount must contain up to 18 whole digits and 2 decimal places."
        )
        BigDecimal amount
) {
}