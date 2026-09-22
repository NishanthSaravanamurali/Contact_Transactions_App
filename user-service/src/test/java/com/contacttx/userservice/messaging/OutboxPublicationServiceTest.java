package com.contacttx.userservice.messaging;

import com.contacttx.userservice.entity.OutboxEvent;
import com.contacttx.userservice.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxPublicationServiceTest {

    private static final String TOPIC = "user.lifecycle.v1";

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private OutboxPublicationService publicationService;

    @BeforeEach
    void setUp() {
        publicationService = new OutboxPublicationService(
                outboxEventRepository,
                kafkaTemplate,
                TOPIC,
                25,
                Duration.ofSeconds(1));
    }

    @Test
    void marksTheEventPublishedOnlyAfterKafkaAcknowledgesIt() {
        OutboxEvent event = pendingEvent();
        when(outboxEventRepository.findPendingForPublishing(any(Pageable.class)))
                .thenReturn(List.of(event));
        when(kafkaTemplate.send(TOPIC, "42", event.getPayload()))
                .thenReturn(CompletableFuture.completedFuture(null));

        int processed = publicationService.publishPendingBatch();

        assertEquals(1, processed);
        assertNotNull(event.getPublishedAt());
        assertEquals(0, event.getPublishAttempts());
        assertNull(event.getLastError());
        verify(kafkaTemplate).send(TOPIC, "42", event.getPayload());
    }

    @Test
    void leavesAFailedEventPendingAndPublishesItOnALaterRetry() {
        OutboxEvent event = pendingEvent();
        CompletableFuture<SendResult<String, String>> failedSend =
                new CompletableFuture<>();
        failedSend.completeExceptionally(
                new IllegalStateException("Kafka broker unavailable"));

        when(outboxEventRepository.findPendingForPublishing(any(Pageable.class)))
                .thenReturn(List.of(event));
        when(kafkaTemplate.send(TOPIC, "42", event.getPayload()))
                .thenReturn(failedSend)
                .thenReturn(CompletableFuture.completedFuture(null));

        publicationService.publishPendingBatch();

        assertNull(event.getPublishedAt());
        assertEquals(1, event.getPublishAttempts());
        assertEquals("Kafka broker unavailable", event.getLastError());

        publicationService.publishPendingBatch();

        assertNotNull(event.getPublishedAt());
        assertEquals(1, event.getPublishAttempts());
        assertNull(event.getLastError());
    }

    private OutboxEvent pendingEvent() {
        return new OutboxEvent(
                "b67a6574-42cc-4a7f-8c6a-b7f98b639311",
                OutboxEventFactory.USER_REGISTERED,
                42L,
                "{\"eventType\":\"UserRegistered\",\"payload\":{\"userId\":42}}",
                LocalDateTime.of(2026, 9, 22, 10, 0));
    }
}
