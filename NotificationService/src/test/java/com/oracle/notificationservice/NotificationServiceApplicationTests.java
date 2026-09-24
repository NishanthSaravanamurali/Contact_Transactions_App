package com.oracle.notificationservice;

import com.oracle.notificationservice.dto.event.PaymentCompletedEvent;
import com.oracle.notificationservice.entity.Notification;
import com.oracle.notificationservice.realtime.NotificationStreamService;
import com.oracle.notificationservice.repository.NotificationRepository;
import com.oracle.notificationservice.service.abstractions.*;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.math.BigDecimal;
import java.time.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationServiceApplicationTests {
    @Autowired MockMvc mvc;
    @Autowired NotificationRepository repository;
    @Autowired NotificationCommandService commands;
    @Autowired NotificationQueryService queries;
    @MockitoBean NotificationStreamService streams;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.public-key", TestKeys::publicPem);
    }

    @BeforeEach
    void resetData() { repository.deleteAll(); }

    static PaymentCompletedEvent event(long id, long receiver) {
        return new PaymentCompletedEvent(id, 1L, "User1", receiver,
                new BigDecimal("500.00"), "INR", OffsetDateTime.now(ZoneOffset.UTC));
    }

    String bearer(long user) throws Exception {
        return "Bearer " + TestKeys.token(Long.toString(user), Instant.now().plusSeconds(600));
    }

    @Test
    void consumesOnceAndPushesOnlyAfterCommit() {
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(repository.existsByTransactionId(100L)).isTrue();
            return null;
        }).when(streams).notificationCreated(eq(2L), any());
        commands.createFromPayment(event(100, 2));
        commands.createFromPayment(event(100, 2));
        assertThat(repository.count()).isEqualTo(1);
        var notification = repository.findAll().get(0);
        assertThat(notification.getMessage()).isEqualTo("User1 has sent you \u20B9500.00");
        assertThat(notification.getCreatedAt()).isNotNull();
        assertThat(notification.getReadAt()).isNull();
        verify(streams, times(1)).notificationCreated(eq(2L), any());
    }

    @Test
    void databaseAlsoRejectsDuplicateTransaction() {
        repository.saveAndFlush(new Notification(100L, 1L, 2L, "first"));
        assertThatThrownBy(() -> repository.saveAndFlush(new Notification(100L, 1L, 2L, "second")))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void concurrentDuplicateDeliveryCreatesOneNotification() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Void> delivery = () -> { start.await(); commands.createFromPayment(event(101, 2)); return null; };
            Future<Void> first = executor.submit(delivery);
            Future<Void> second = executor.submit(delivery);
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
            assertThat(repository.count()).isEqualTo(1);
            verify(streams, times(1)).notificationCreated(eq(2L), any());
        } finally { executor.shutdownNow(); }
    }

    @Test
    void listAndCountOnlyExposeAuthenticatedReceiversNotifications() throws Exception {
        commands.createFromPayment(event(101, 2));
        commands.createFromPayment(event(102, 3));
        mvc.perform(get("/api/v1/notifications").header(HttpHeaders.AUTHORIZATION, bearer(2))
                        .param("receiverUserId", "3"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].transactionId").value(101));
        mvc.perform(get("/api/v1/notifications/unread-count").header(HttpHeaders.AUTHORIZATION, bearer(2)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.unreadCount").value(1));
        assertThat(queries.getAll(1L, "all", 0, 20)).isEmpty();
    }

    @Test
    void readingIsIdempotentAndKeepsNotificationInAllAndReadTabs() throws Exception {
        commands.createFromPayment(event(101, 2));
        Long id = repository.findAll().get(0).getNotificationId();
        mvc.perform(patch("/api/v1/notifications/{id}/read", id).header(HttpHeaders.AUTHORIZATION, bearer(2)))
                .andExpect(status().isNoContent());
        OffsetDateTime firstRead = repository.findById(id).orElseThrow().getReadAt();
        commands.markRead(2L, id);
        assertThat(repository.findById(id).orElseThrow().getReadAt()).isEqualTo(firstRead);
        assertThat(queries.getAll(2L, "unread", 0, 20)).isEmpty();
        assertThat(queries.getAll(2L, "read", 0, 20).getTotalElements()).isEqualTo(1);
        assertThat(queries.getAll(2L, "all", 0, 20).getTotalElements()).isEqualTo(1);
        assertThat(queries.unreadCount(2L)).isZero();
        verify(streams, times(1)).notificationsRead(2L);
    }

    @Test
    void anotherUserCannotMarkNotificationRead() throws Exception {
        commands.createFromPayment(event(101, 2));
        Long id = repository.findAll().get(0).getNotificationId();
        mvc.perform(patch("/api/v1/notifications/{id}/read", id).header(HttpHeaders.AUTHORIZATION, bearer(3)))
                .andExpect(status().isNotFound());
        assertThat(repository.findById(id).orElseThrow().getReadAt()).isNull();
        verify(streams, never()).notificationsRead(any());
    }

    @Test
    void markAllOnlyChangesCurrentUserAndPreservesRows() throws Exception {
        commands.createFromPayment(event(101, 2));
        commands.createFromPayment(event(102, 2));
        commands.createFromPayment(event(103, 3));
        mvc.perform(patch("/api/v1/notifications/read-all").header(HttpHeaders.AUTHORIZATION, bearer(2)))
                .andExpect(status().isNoContent());
        assertThat(queries.unreadCount(2L)).isZero();
        assertThat(queries.unreadCount(3L)).isEqualTo(1);
        assertThat(repository.count()).isEqualTo(3);
    }

    @Test
    void badParametersReturn400() throws Exception {
        for (String query : new String[]{"?size=101", "?size=0", "?page=-1", "?filter=anything"}) {
            mvc.perform(get("/api/v1/notifications" + query).header(HttpHeaders.AUTHORIZATION, bearer(2)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void protectedEndpointsRequireBearerJwt() throws Exception {
        for (String endpoint : new String[]{"", "/unread-count", "/stream"}) {
            mvc.perform(get("/api/v1/notifications" + endpoint)).andExpect(status().isUnauthorized());
        }
        mvc.perform(patch("/api/v1/notifications/1/read")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/v1/notifications/read-all")).andExpect(status().isUnauthorized());
    }

    @Test
    void invalidExpiredMissingExpiryAndInvalidSubjectTokensAreRejected() throws Exception {
        for (String token : new String[]{"invalid",
                TestKeys.token("2", Instant.now().minusSeconds(120)),
                TestKeys.token("2", null),
                TestKeys.token("abc", Instant.now().plusSeconds(600)),
                TestKeys.token("-1", Instant.now().plusSeconds(600))}) {
            mvc.perform(get("/api/v1/notifications").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void invalidPaymentsNeverPersistOrPush() {
        var invalid = new PaymentCompletedEvent(101L, 1L, "User1", 2L,
                new BigDecimal("-1"), "INR", OffsetDateTime.now());
        assertThatThrownBy(() -> commands.createFromPayment(invalid)).isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> commands.createFromPayment(event(102, 1))).isInstanceOf(IllegalArgumentException.class);
        assertThat(repository.count()).isZero();
        verifyNoInteractions(streams);
    }

    @Test
    void historyHasStableNewestFirstOrder() {
        commands.createFromPayment(event(101, 2));
        commands.createFromPayment(event(102, 2));
        assertThat(queries.getAll(2L, "all", 0, 1).getContent().get(0).transactionId()).isEqualTo(102);
        assertThat(queries.getAll(2L, "all", 1, 1).getContent().get(0).transactionId()).isEqualTo(101);
    }
}
