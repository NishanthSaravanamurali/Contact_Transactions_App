package com.oracle.transactionmicroservice.messaging.dto;

import java.time.Instant;

/**
 * Exact JSON envelope published by User Service for a committed registration.
 * The user identifier is nested under payload in the producer contract.
 */
public record UserRegisteredEvent(
        String eventId,
        String eventType,
        Integer eventVersion,
        Long aggregateId,
        Instant occurredAt,
        UserRegisteredPayload payload
) {
    public Long userId() {
        return payload == null ? null : payload.userId();
    }

    public record UserRegisteredPayload(Long userId) {
    }
}
