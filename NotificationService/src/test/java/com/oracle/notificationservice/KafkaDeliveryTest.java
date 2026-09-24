package com.oracle.notificationservice;

import com.oracle.notificationservice.dto.event.PaymentCompletedEvent;
import com.oracle.notificationservice.realtime.NotificationStreamService;
import com.oracle.notificationservice.service.abstractions.NotificationCommandService;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.kafka.core.*;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=true",
        "spring.datasource.url=jdbc:h2:mem:notifications-kafka-test;MODE=Oracle;DB_CLOSE_DELAY=-1"
})
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1,
        topics = {"payments.completed.v1", "payments.completed.v1.notification-dlt"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@DirtiesContext
class KafkaDeliveryTest {
    @Autowired KafkaTemplate<Object, Object> template;
    @Autowired EmbeddedKafkaBroker broker;
    @MockitoBean NotificationCommandService commands;
    @MockitoBean NotificationStreamService streams;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.public-key", TestKeys::publicPem);
    }

    private String payload(long id) {
        return """
                {"transactionId":%d,"senderUserId":1,"senderName":"User1","receiverUserId":2,
                 "amount":"500.00","currency":"INR","completedAt":"2026-09-23T10:00:00Z"}
                """.formatted(id);
    }

    private Consumer<String, String> dltConsumer() {
        var properties = KafkaTestUtils.consumerProps("test-dlt-" + UUID.randomUUID(), "false", broker);
        var consumer = new DefaultKafkaConsumerFactory<>(properties,
                new StringDeserializer(), new StringDeserializer()).createConsumer();
        broker.consumeFromAnEmbeddedTopic(consumer, "payments.completed.v1.notification-dlt");
        return consumer;
    }

    @Test
    void transientFailureIsRetriedAndThenProcessed() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        doAnswer(invocation -> {
            if (attempts.incrementAndGet() <= 2) throw new TransientDataAccessResourceException("test outage");
            return null;
        }).when(commands).createFromPayment(argThat(event -> event.transactionId() == 701L));
        template.send("payments.completed.v1", "2", payload(701)).get(10, TimeUnit.SECONDS);
        verify(commands, timeout(15000).times(3)).createFromPayment(any(PaymentCompletedEvent.class));
        assertThat(attempts).hasValue(3);
    }

    @Test
    void malformedJsonIsRetainedInDeadLetterTopic() throws Exception {
        try (var consumer = dltConsumer()) {
            template.send("payments.completed.v1", "2", "{broken").get(10, TimeUnit.SECONDS);
            var record = KafkaTestUtils.getSingleRecord(consumer,
                    "payments.completed.v1.notification-dlt", Duration.ofSeconds(15));
            assertThat(record.value()).isEqualTo("{broken");
            assertThat(record.key()).isEqualTo("2");
            verifyNoInteractions(commands);
        }
    }
}
