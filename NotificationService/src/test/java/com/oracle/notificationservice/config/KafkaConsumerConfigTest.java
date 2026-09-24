package com.oracle.notificationservice.config;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.MessageListenerContainer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class KafkaConsumerConfigTest {
    @Test
    void transientFailureIsSkippedOnlyAfterThreeAttempts() {
        assertThreeAttempts(new IllegalStateException("database unavailable"));
    }

    @Test
    void malformedFailureAlsoGetsThreeAttempts() {
        assertThreeAttempts(new IllegalArgumentException("invalid event"));
    }

    private void assertThreeAttempts(RuntimeException failure) {
        DefaultErrorHandler handler = new KafkaConsumerConfig().kafkaErrorHandler();
        var record = new ConsumerRecord<>("test-events", 0, 0L, "41", "invalid");
        var consumer = mock(Consumer.class);
        var container = mock(MessageListenerContainer.class);
        assertFalse(handler.handleOne(failure, record, consumer, container));
        assertFalse(handler.handleOne(failure, record, consumer, container));
        assertTrue(handler.handleOne(failure, record, consumer, container));
        assertTrue(handler.isAckAfterHandle());
    }
}
