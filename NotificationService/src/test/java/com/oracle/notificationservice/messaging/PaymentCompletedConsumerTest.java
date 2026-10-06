package com.oracle.notificationservice.messaging;

import com.oracle.notificationservice.dto.event.PaymentCompletedEvent;
import com.oracle.notificationservice.service.abstractions.NotificationCommandService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentCompletedConsumerTest {
    private final NotificationCommandService commands = mock(NotificationCommandService.class);
    private final PaymentCompletedConsumer consumer = new PaymentCompletedConsumer(JsonMapper.builder().build(), commands);

    @Test
    void parsesExplicitEventContract() {
        consumer.consume("""
                {"transactionId":10,"senderUserId":1,"senderName":"User1","receiverUserId":2,
                 "amount":"500.00","currency":"INR","completedAt":"2026-09-23T10:00:00Z"}
                """);
        var event = ArgumentCaptor.forClass(PaymentCompletedEvent.class);
        verify(commands).createFromPayment(event.capture());
        assertThat(event.getValue().transactionId()).isEqualTo(10);
        assertThat(event.getValue().amount()).isEqualByComparingTo("500.00");
    }

    @Test
    void malformedPayloadsAreRejectedWithoutCallingService() {
        for (String payload : new String[]{"", "{bad", "[]"}) {
            assertThatThrownBy(() -> consumer.consume(payload)).isInstanceOf(IllegalArgumentException.class);
        }
        verifyNoInteractions(commands);
    }

    @Test
    void databaseFailurePropagatesForKafkaRetry() {
        doThrow(new IllegalStateException("database unavailable")).when(commands).createFromPayment(any());
        assertThatThrownBy(() -> consumer.consume("""
                {"transactionId":10,"senderUserId":1,"senderName":"User1","receiverUserId":2,
                 "amount":"500.00","currency":"INR","completedAt":"2026-09-23T10:00:00Z"}
                """)).isInstanceOf(IllegalStateException.class);
    }
}
