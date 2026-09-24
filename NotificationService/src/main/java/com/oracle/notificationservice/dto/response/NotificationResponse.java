package com.oracle.notificationservice.dto.response;

import com.oracle.notificationservice.entity.Notification;
import java.time.OffsetDateTime;

public record NotificationResponse(
        Long notificationId, Long transactionId, String message,
        OffsetDateTime createdAt, OffsetDateTime readAt
) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(notification.getNotificationId(),
                notification.getTransactionId(), notification.getMessage(),
                notification.getCreatedAt(), notification.getReadAt());
    }
}
