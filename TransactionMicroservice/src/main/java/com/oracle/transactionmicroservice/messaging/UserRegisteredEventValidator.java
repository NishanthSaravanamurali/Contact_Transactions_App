package com.oracle.transactionmicroservice.messaging;

import com.oracle.transactionmicroservice.messaging.dto.UserRegisteredEvent;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class UserRegisteredEventValidator {

    static final String USER_REGISTERED = "UserRegistered";
    static final int SUPPORTED_EVENT_VERSION = 1;

    public void validate(String recordKey, UserRegisteredEvent event) {
        if (event == null) {
            throw invalid("Event value is required.");
        }
        requireUuid(event.eventId());
        if (!USER_REGISTERED.equals(event.eventType())) {
            throw invalid("eventType must be UserRegistered.");
        }
        if (event.eventVersion() == null
                || event.eventVersion() != SUPPORTED_EVENT_VERSION) {
            throw invalid("eventVersion must be 1.");
        }
        requirePositive(event.aggregateId(), "aggregateId");
        requirePositive(event.userId(), "payload.userId");
        if (!event.aggregateId().equals(event.userId())) {
            throw invalid("payload.userId must equal aggregateId.");
        }
        if (event.occurredAt() == null) {
            throw invalid("occurredAt is required.");
        }
        if (!event.aggregateId().toString().equals(recordKey)) {
            throw invalid("Kafka record key must equal aggregateId.");
        }
    }

    private void requireUuid(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            throw invalid("eventId is required.");
        }
        try {
            UUID.fromString(eventId);
        } catch (IllegalArgumentException exception) {
            throw new MalformedUserLifecycleEventException(
                    "eventId must be a valid UUID.", exception);
        }
    }

    private void requirePositive(Long value, String field) {
        if (value == null || value <= 0) {
            throw invalid(field + " must be a positive number.");
        }
    }

    private MalformedUserLifecycleEventException invalid(String message) {
        return new MalformedUserLifecycleEventException(message);
    }
}
