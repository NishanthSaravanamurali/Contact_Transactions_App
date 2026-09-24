package com.oracle.notificationservice.service.implementations;

import com.oracle.notificationservice.dto.event.PaymentCompletedEvent;
import com.oracle.notificationservice.dto.response.NotificationResponse;
import com.oracle.notificationservice.entity.Notification;
import com.oracle.notificationservice.exception.ResourceNotFoundException;
import com.oracle.notificationservice.realtime.NotificationStreamService;
import com.oracle.notificationservice.repository.NotificationRepository;
import com.oracle.notificationservice.service.abstractions.NotificationCommandService;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;

@Service
public class NotificationCommandServiceImpl implements NotificationCommandService {
    private final NotificationRepository repository;
    private final NotificationStreamService streams;
    private final Validator validator;
    private final TransactionTemplate transaction;

    public NotificationCommandServiceImpl(NotificationRepository repository,
            NotificationStreamService streams, Validator validator,
            PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.streams = streams;
        this.validator = validator;
        this.transaction = new TransactionTemplate(transactionManager);
        // Return only after commit; a duplicate insert must roll back before checking it.
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public void createFromPayment(PaymentCompletedEvent event) {
        if (event == null) throw new IllegalArgumentException("Payment event is required.");
        var violations = validator.validate(event);
        if (!violations.isEmpty()) throw new ConstraintViolationException(violations);
        if (event.senderUserId().equals(event.receiverUserId())) {
            throw new IllegalArgumentException("A payment sender and receiver must be different.");
        }

        NotificationResponse created;
        try {
            created = transaction.execute(status -> {
                if (repository.existsByTransactionId(event.transactionId())) return null;
                String message = event.senderName().strip() + " has sent you \u20B9"
                        + event.amount().setScale(2).toPlainString();
                Notification notification = new Notification(event.transactionId(),
                        event.senderUserId(), event.receiverUserId(), message);
                return NotificationResponse.from(repository.saveAndFlush(notification));
            });
        } catch (DataIntegrityViolationException exception) {
            // Only the known deduplication constraint is safe to treat as success.
            if (isTransactionDuplicate(exception)
                    && repository.existsByTransactionId(event.transactionId())) return;
            throw exception;
        }
        if (created != null) streams.notificationCreated(event.receiverUserId(), created);
    }

    @Override
    public void markRead(Long userId, Long notificationId) {
        NotificationQueryServiceImpl.requireUser(userId);
        if (notificationId == null || notificationId <= 0) {
            throw new IllegalArgumentException("A valid notification ID is required.");
        }
        Boolean changed = transaction.execute(status -> {
            if (!repository.existsByNotificationIdAndReceiverUserId(notificationId, userId)) {
                throw new ResourceNotFoundException("Notification not found.");
            }
            return repository.markRead(notificationId, userId, OffsetDateTime.now(ZoneOffset.UTC)) > 0;
        });
        if (Boolean.TRUE.equals(changed)) streams.notificationsRead(userId);
    }

    @Override
    public void markAllRead(Long userId) {
        NotificationQueryServiceImpl.requireUser(userId);
        Integer changed = transaction.execute(status ->
                repository.markAllRead(userId, OffsetDateTime.now(ZoneOffset.UTC)));
        if (changed != null && changed > 0) streams.notificationsRead(userId);
    }

    private boolean isTransactionDuplicate(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation) {
                String constraint = violation.getConstraintName();
                if (constraint != null && constraint.toUpperCase(Locale.ROOT)
                        .contains("UQ_NOTIFICATIONS_TRANSACTION")) return true;
            }
        }
        return false;
    }
}
