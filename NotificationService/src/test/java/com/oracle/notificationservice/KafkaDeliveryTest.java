package com.oracle.notificationservice;

import com.oracle.notificationservice.dto.event.PaymentCompletedEvent;
import com.oracle.notificationservice.realtime.NotificationStreamService;
import com.oracle.notificationservice.service.abstractions.NotificationCommandService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.kafka.core.*;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
        topics = {"payments.completed.v1"},
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
    void malformedJsonIsSkippedAndNextEventIsProcessed() throws Exception {
        template.send("payments.completed.v1", "2", "{broken").get(10, TimeUnit.SECONDS);
        template.send("payments.completed.v1", "2", payload(702)).get(10, TimeUnit.SECONDS);
        verify(commands, timeout(15000)).createFromPayment(argThat(event -> event.transactionId() == 702L));
    }

    @Test
    void exhaustedFailureIsSkippedAndNextEventIsProcessed() throws Exception {
        doThrow(new TransientDataAccessResourceException("test outage")).when(commands)
                .createFromPayment(argThat(event -> event.transactionId() == 703L));
        template.send("payments.completed.v1", "2", payload(703)).get(10, TimeUnit.SECONDS);
        template.send("payments.completed.v1", "2", payload(704)).get(10, TimeUnit.SECONDS);
        verify(commands, timeout(15000)).createFromPayment(argThat(event -> event.transactionId() == 704L));
        verify(commands, times(3)).createFromPayment(argThat(event -> event.transactionId() == 703L));
    }
}
