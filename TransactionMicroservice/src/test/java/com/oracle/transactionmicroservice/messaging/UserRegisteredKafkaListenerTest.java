package com.oracle.transactionmicroservice.messaging;

import com.oracle.transactionmicroservice.service.abstractions.WalletCommandService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserRegisteredKafkaListenerTest {

    private WalletCommandService walletCommandService;
    private UserRegisteredKafkaListener listener;

    @BeforeEach
    void setUp() {
        walletCommandService = mock(WalletCommandService.class);
        listener = new UserRegisteredKafkaListener(
                JsonMapper.builder().findAndAddModules().build(),
                new UserRegisteredEventValidator(),
                walletCommandService);
    }

    @Test
    void validUserRegisteredEnvelopeCreatesWalletForPayloadUser() {
        listener.consume(record("41", validJson(41L)));

        verify(walletCommandService).createWallet(41L);
    }

    @Test
    void malformedEventDoesNotCreateWallet() {
        String missingEventId = """
                {
                  "eventType":"UserRegistered",
                  "eventVersion":1,
                  "aggregateId":41,
                  "occurredAt":"2026-09-22T16:42:44.033384Z",
                  "payload":{"userId":41}
                }
                """;

        assertThrows(
                MalformedUserLifecycleEventException.class,
                () -> listener.consume(record("41", missingEventId)));
        verify(walletCommandService, never()).createWallet(41L);
    }

    @Test
    void temporaryWalletFailureEscapesListenerForKafkaRetry() {
        when(walletCommandService.createWallet(41L))
                .thenThrow(new QueryTimeoutException("temporary database timeout"));

        assertThrows(
                QueryTimeoutException.class,
                () -> listener.consume(record("41", validJson(41L))));
    }

    @Test
    void mismatchedKafkaKeyIsRejected() {
        assertThrows(
                MalformedUserLifecycleEventException.class,
                () -> listener.consume(record("99", validJson(41L))));
        verify(walletCommandService, never()).createWallet(41L);
    }

    private ConsumerRecord<String, String> record(String key, String value) {
        return new ConsumerRecord<>("user.lifecycle.v1", 0, 0L, key, value);
    }

    private String validJson(Long userId) {
        return """
                {
                  "eventId":"6b4d0ab2-6fb1-4e3a-9c4d-94fc858f2eaa",
                  "eventType":"UserRegistered",
                  "eventVersion":1,
                  "aggregateId":%d,
                  "occurredAt":"2026-09-22T16:42:44.033384Z",
                  "payload":{"userId":%d}
                }
                """.formatted(userId, userId);
    }
}
