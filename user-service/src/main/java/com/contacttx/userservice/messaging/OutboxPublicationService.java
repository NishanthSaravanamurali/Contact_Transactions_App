package com.contacttx.userservice.messaging;

import com.contacttx.userservice.entity.OutboxEvent;
import com.contacttx.userservice.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class OutboxPublicationService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(OutboxPublicationService.class);
    private static final int MAX_ERROR_LENGTH = 1000;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;
    private final int batchSize;
    private final Duration sendTimeout;

    public OutboxPublicationService(
            OutboxEventRepository outboxEventRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${messaging.kafka.user-lifecycle-topic}") String topic,
            @Value("${outbox.publisher.batch-size}") int batchSize,
            @Value("${outbox.publisher.send-timeout}") Duration sendTimeout) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.batchSize = batchSize;
        this.sendTimeout = sendTimeout;
    }

    @Transactional
    public int publishPendingBatch() {
        List<OutboxEvent> events = outboxEventRepository.findPendingForPublishing(
                PageRequest.of(0, batchSize));

        for (OutboxEvent event : events) {
            try {
                kafkaTemplate.send(
                                topic,
                                event.getAggregateId().toString(),
                                event.getPayload())
                        .get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
                event.markPublished(LocalDateTime.now(ZoneOffset.UTC));
                LOGGER.info(
                        "Published outbox event; eventId={}, eventType={}, aggregateId={}",
                        event.getEventId(),
                        event.getEventType(),
                        event.getAggregateId());
            } catch (Exception exception) {
                if (exception instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                event.markPublicationFailed(boundedErrorMessage(exception));
                LOGGER.warn(
                        "Kafka publication failed; eventId={}, eventType={}, aggregateId={}, attempts={}",
                        event.getEventId(),
                        event.getEventType(),
                        event.getAggregateId(),
                        event.getPublishAttempts());

                if (Thread.currentThread().isInterrupted()) {
                    break;
                }
            }
        }

        return events.size();
    }

    private String boundedErrorMessage(Throwable throwable) {
        Throwable rootCause = throwable;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }

        String message = rootCause.getMessage();
        if (message == null || message.isBlank()) {
            message = rootCause.getClass().getSimpleName();
        }
        return message.length() <= MAX_ERROR_LENGTH
                ? message
                : message.substring(0, MAX_ERROR_LENGTH);
    }
}
