package com.oracle.notificationservice.dto.event;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

// The dedicated topic contains only committed, successful wallet-to-wallet payments.
public record PaymentCompletedEvent(
        @NotNull @Positive Long transactionId,
        @NotNull @Positive Long senderUserId,
        @NotBlank @Size(max = 100) String senderName,
        @NotNull @Positive Long receiverUserId,
        @NotNull @DecimalMin("0.01") @Digits(integer = 18, fraction = 2) BigDecimal amount,
        @NotNull @Pattern(regexp = "INR") String currency,
        @NotNull OffsetDateTime completedAt
) {}
