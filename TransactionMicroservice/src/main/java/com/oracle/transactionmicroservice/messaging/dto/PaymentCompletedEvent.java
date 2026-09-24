package com.oracle.transactionmicroservice.messaging.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

// Flat contract consumed by NotificationService; no JWT or private contact data
// is returned in the public payment response.
public record PaymentCompletedEvent(Long transactionId, Long senderUserId,
        String senderName, Long receiverUserId, BigDecimal amount,
        String currency, OffsetDateTime completedAt) {}
