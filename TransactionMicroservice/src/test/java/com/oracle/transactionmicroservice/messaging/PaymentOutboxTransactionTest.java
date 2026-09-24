package com.oracle.transactionmicroservice.messaging;

import com.oracle.transactionmicroservice.dto.request.MakePaymentRequest;
import com.oracle.transactionmicroservice.entity.*;
import com.oracle.transactionmicroservice.enums.TransactionStatus;
import com.oracle.transactionmicroservice.policy.MoneyPolicy;
import com.oracle.transactionmicroservice.repository.*;
import com.oracle.transactionmicroservice.service.abstractions.UserOperationGuard;
import com.oracle.transactionmicroservice.service.implementations.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.time.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.datasource.url=jdbc:h2:mem:payment-outbox;MODE=Oracle",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.show-sql=false",
        "payment.outbox.enabled=false", "eureka.client.enabled=false"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({PaymentOutboxEventFactory.class, PaymentOutboxPublicationService.class,
        WalletCommandServiceImpl.class, GuardedLocalTransaction.class, MoneyPolicy.class,
        PaymentOutboxTransactionTest.Support.class})
class PaymentOutboxTransactionTest {
    @Autowired WalletCommandServiceImpl payments;
    @Autowired WalletRepository wallets;
    @Autowired TransactionRepository transactions;
    @MockitoSpyBean PaymentOutboxEventRepository outbox;
    @Autowired PaymentOutboxPublicationService publication;
    @MockitoBean KafkaTemplate<String, String> kafka;

    @TestConfiguration
    static class Support {
        @Bean ObjectMapper mapper() { return JsonMapper.builder().build(); }
        @Bean UserOperationGuard guard() {
            return new UserOperationGuard() {
                public <T> T withActiveUsers(Long sender, Long receiver, Supplier<T> action) { return action.get(); }
                public <T> T withPaymentUsers(Long sender, Long receiver, Function<String, T> action) {
                    return action.apply("Priya Office");
                }
            };
        }
    }

    @BeforeEach void setup() {
        outbox.deleteAll();
        transactions.deleteAll();
        wallets.deleteAll();
        Wallet sender = new Wallet(1L);
        sender.credit(new BigDecimal("100.00"));
        wallets.saveAndFlush(sender);
        wallets.saveAndFlush(new Wallet(2L));
    }

    private Long pay() {
        return payments.makePayment(1L, new MakePaymentRequest(2L, new BigDecimal("50.00"))).transactionId();
    }

    @Test void paymentAndNameSnapshotCommitTogether() {
        Long id = pay();
        var row = outbox.findById(id).orElseThrow();
        assertThat(row.getPayload()).contains("\"senderName\":\"Priya Office\"", "\"transactionId\":" + id);
        assertThat(row.getPublishedAt()).isNull();
        assertThat(wallets.findByUserId(1L).orElseThrow().getBalance()).isEqualByComparingTo("50");
        assertThat(wallets.findByUserId(2L).orElseThrow().getBalance()).isEqualByComparingTo("50");
        verify(kafka, never()).send(anyString(), anyString(), anyString());
    }

    @Test void outboxFailureRollsBackBothWalletsAndTransaction() {
        doThrow(new IllegalStateException("outbox write failed")).when(outbox).saveAndFlush(any(PaymentOutboxEvent.class));
        assertThatThrownBy(this::pay).isInstanceOf(IllegalStateException.class);
        assertThat(transactions.count()).isZero();
        assertThat(outbox.count()).isZero();
        assertThat(wallets.findByUserId(1L).orElseThrow().getBalance()).isEqualByComparingTo("100");
        assertThat(wallets.findByUserId(2L).orElseThrow().getBalance()).isEqualByComparingTo("0");
    }

    @Test void failedPaymentDoesNotEnterOutbox() {
        var result = payments.makePayment(1L, new MakePaymentRequest(2L, new BigDecimal("200.00")));
        assertThat(result.status()).isEqualTo(TransactionStatus.FAILED);
        assertThat(outbox.count()).isZero();
    }

    @Test void acknowledgementMarksPublishedAndPreventsResending() {
        Long id = pay();
        when(kafka.send(eq("payments.completed.v1"), eq("2"), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
        publication.publishOne(id);
        publication.publishOne(id);
        assertThat(outbox.findById(id).orElseThrow().getPublishedAt()).isNotNull();
        verify(kafka, times(1)).send(eq("payments.completed.v1"), eq("2"), anyString());
    }

    @Test void failedSendStaysPendingWithBackoffAndDoesNotUndoPayment() {
        Long id = pay();
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("Kafka down")));
        publication.publishOne(id);
        var row = outbox.findById(id).orElseThrow();
        assertThat(row.getPublishedAt()).isNull();
        assertThat(row.getPublishAttempts()).isEqualTo(1);
        assertThat(row.getNextAttemptAt()).isAfter(row.getCreatedAt());
        publication.publishOne(id);
        verify(kafka, times(1)).send(anyString(), anyString(), anyString());
        assertThat(wallets.findByUserId(2L).orElseThrow().getBalance()).isEqualByComparingTo("50");
        org.springframework.test.util.ReflectionTestUtils.setField(row, "nextAttemptAt",
                LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1));
        outbox.saveAndFlush(row);
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
        publication.publishOne(id);
        assertThat(outbox.findById(id).orElseThrow().getPublishedAt()).isNotNull();
        verify(kafka, times(2)).send(anyString(), anyString(), anyString());
    }

    @Test void cleanupDeletesOnlyOldPublishedRowsNeverPendingOrRecentRows() {
        var old = LocalDateTime.now(ZoneOffset.UTC).minusHours(48);
        var published = new PaymentOutboxEvent(901L, 2L, "{}", old);
        published.markPublished(old);
        outbox.saveAndFlush(published);
        outbox.saveAndFlush(new PaymentOutboxEvent(902L, 2L, "{}", old));
        var recent = new PaymentOutboxEvent(903L, 2L, "{}", old);
        recent.markPublished(LocalDateTime.now(ZoneOffset.UTC));
        outbox.saveAndFlush(recent);
        assertThat(publication.cleanupPublished()).isEqualTo(1);
        assertThat(outbox.existsById(901L)).isFalse();
        assertThat(outbox.existsById(902L)).isTrue();
        assertThat(outbox.existsById(903L)).isTrue();
    }
}
