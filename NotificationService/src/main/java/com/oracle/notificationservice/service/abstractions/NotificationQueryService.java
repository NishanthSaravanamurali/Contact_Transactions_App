package com.oracle.notificationservice.service.abstractions;

import com.oracle.notificationservice.dto.response.NotificationResponse;
import org.springframework.data.domain.Page;

public interface NotificationQueryService {
    Page<NotificationResponse> getAll(Long userId, String filter, int page, int size);
    long unreadCount(Long userId);
}
