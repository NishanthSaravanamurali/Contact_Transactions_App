package com.oracle.transactionmicroservice.config;

import com.oracle.transactionmicroservice.messaging.MalformedUserLifecycleEventException;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaConsumerConfigTest {

    @Test
    void temporaryFailureRetriesBeforePermanentFailureGoesToDlt() {
        KafkaTemplate<String, String> kafkaTemplate = template();
        DefaultErrorHandler handler = handler(kafkaTemplate, 2L);
        ConsumerRecord<String, String> record = record();
        Consumer<?, ?> consumer = mock(Consumer.class);
        MessageListenerContainer container = mock(MessageListenerContainer.class);
        RuntimeException failure = new IllegalStateException("database unavailable");

        assertFalse(handler.handleOne(failure, record, consumer, container));
        assertFalse(handler.handleOne(failure, record, consumer, container));
        verify(kafkaTemplate, never()).send(
                isA(ProducerRecord.class));

        assertTrue(handler.handleOne(failure, record, consumer, container));
        verify(kafkaTemplate, times(1)).send(
                argThat((ProducerRecord<String, String> producerRecord) ->
                "user.lifecycle.v1.DLT".equals(producerRecord.topic())
                        && "41".equals(producerRecord.key())));
    }

    @Test
    void malformedRecordSkipsRetriesAndGoesDirectlyToDlt() {
        KafkaTemplate<String, String> kafkaTemplate = template();
        DefaultErrorHandler handler = handler(kafkaTemplate, 3L);

        boolean handled = handler.handleOne(
                new MalformedUserLifecycleEventException("eventId is invalid"),
                record(),
                mock(Consumer.class),
                mock(MessageListenerContainer.class));

        assertTrue(handled);
        verify(kafkaTemplate, times(1)).send(
                argThat((ProducerRecord<String, String> producerRecord) ->
                "user.lifecycle.v1.DLT".equals(producerRecord.topic())));
    }

    private DefaultErrorHandler handler(
            KafkaTemplate<String, String> kafkaTemplate,
            long retries
    ) {
        return (DefaultErrorHandler) new KafkaConsumerConfig()
                .userLifecycleErrorHandler(
                        kafkaTemplate,
                        "user.lifecycle.v1.DLT",
                        0L,
                        retries);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private KafkaTemplate<String, String> template() {
        KafkaTemplate<String, String> template = mock(KafkaTemplate.class);
        when(template.send(isA(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        return template;
    }

    private ConsumerRecord<String, String> record() {
        return new ConsumerRecord<>(
                "user.lifecycle.v1",
                0,
                0L,
                "41",
                "{\"eventType\":\"UserRegistered\"}");
    }
}
