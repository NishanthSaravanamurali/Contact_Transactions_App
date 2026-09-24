package com.oracle.notificationservice.service.implementations;

import com.oracle.notificationservice.dto.event.PaymentCompletedEvent;
import com.oracle.notificationservice.entity.Notification;
import com.oracle.notificationservice.realtime.NotificationStreamService;
import com.oracle.notificationservice.repository.NotificationRepository;
import jakarta.validation.Validator;
import org.junit.jupiter.api.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.*;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificationCommandServiceImplTest {
    private final NotificationRepository repository = mock(NotificationRepository.class);
    private final NotificationStreamService streams = mock(NotificationStreamService.class);
    private final Validator validator = mock(Validator.class);
    private final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    private NotificationCommandServiceImpl commands;

    private PaymentCompletedEvent event() {
        return new PaymentCompletedEvent(9L, 1L, "User1", 2L,
                new BigDecimal("500.00"), "INR", OffsetDateTime.now());
    }

    @BeforeEach
    void setup() {
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(validator.validate(any(PaymentCompletedEvent.class))).thenReturn(Set.of());
        when(repository.saveAndFlush(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));
        commands = new NotificationCommandServiceImpl(repository, streams, validator, manager);
    }

    @Test
    void failedCommitNeverPushesNotification() {
        doThrow(new TransactionSystemException("commit failed")).when(manager).commit(any());
        assertThatThrownBy(() -> commands.createFromPayment(event())).isInstanceOf(TransactionSystemException.class);
        verifyNoInteractions(streams);
    }

    @Test
    void unrelatedIntegrityFailureIsNotMistakenForDuplicate() {
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("different constraint"));
        assertThatThrownBy(() -> commands.createFromPayment(event())).isInstanceOf(DataIntegrityViolationException.class);
        verify(manager).rollback(any());
        verifyNoInteractions(streams);
    }
}
