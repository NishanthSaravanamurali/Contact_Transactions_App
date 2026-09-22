package com.contacttx.userservice.messaging;

import com.contacttx.userservice.entity.OutboxEvent;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class OutboxEventFactory {

    public static final String USER_REGISTERED = "UserRegistered";
    private static final int EVENT_VERSION = 1;

    private final ObjectMapper objectMapper;

    public OutboxEventFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public OutboxEvent createUserRegistered(Long userId) {
        String eventId = UUID.randomUUID().toString();
        Instant occurredAt = Instant.now();

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId);
        envelope.put("eventType", USER_REGISTERED);
        envelope.put("eventVersion", EVENT_VERSION);
        envelope.put("aggregateId", userId);
        envelope.put("occurredAt", occurredAt.toString());
        envelope.put("payload", Map.of("userId", userId));

        try {
            String payload = objectMapper.writeValueAsString(envelope);
            return new OutboxEvent(
                    eventId,
                    USER_REGISTERED,
                    userId,
                    payload,
                    LocalDateTime.ofInstant(occurredAt, ZoneOffset.UTC));
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "Unable to serialize UserRegistered outbox event",
                    exception);
        }
    }
}
