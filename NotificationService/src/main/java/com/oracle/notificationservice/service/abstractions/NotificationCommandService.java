package com.oracle.notificationservice.service.abstractions;

import com.oracle.notificationservice.dto.event.PaymentCompletedEvent;

public interface NotificationCommandService {
    void createFromPayment(PaymentCompletedEvent event);
    void markRead(Long userId, Long notificationId);
    void markAllRead(Long userId);
}
