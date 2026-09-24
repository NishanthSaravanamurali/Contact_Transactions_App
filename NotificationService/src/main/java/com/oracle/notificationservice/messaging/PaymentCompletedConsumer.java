package com.oracle.notificationservice.messaging;

import com.oracle.notificationservice.dto.event.PaymentCompletedEvent;
import com.oracle.notificationservice.service.abstractions.NotificationCommandService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
public class PaymentCompletedConsumer {
    private final JsonMapper mapper;
    private final NotificationCommandService commands;

    public PaymentCompletedConsumer(JsonMapper mapper, NotificationCommandService commands) {
        this.mapper = mapper;
        this.commands = commands;
    }

    @KafkaListener(topics = "${notifications.kafka.payment-topic}",
            groupId = "${spring.kafka.consumer.group-id}")
    public void consume(String payload) {
        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("Payment payload is required.");
        }
        PaymentCompletedEvent event;
        try { event = mapper.readValue(payload, PaymentCompletedEvent.class); }
        catch (JacksonException exception) {
            throw new IllegalArgumentException("Payment payload is not a valid payment event.", exception);
        }
        commands.createFromPayment(event);
    }
}
