package com.oracle.notificationservice.service.implementations;

import com.oracle.notificationservice.dto.response.NotificationResponse;
import com.oracle.notificationservice.repository.NotificationRepository;
import com.oracle.notificationservice.service.abstractions.NotificationQueryService;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class NotificationQueryServiceImpl implements NotificationQueryService {
    private final NotificationRepository repository;

    public NotificationQueryServiceImpl(NotificationRepository repository) {
        this.repository = repository;
    }

    @Override
    public Page<NotificationResponse> getAll(Long userId, String filter, int page, int size) {
        requireUser(userId);
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("Page must be nonnegative and size must be 1 to 100.");
        }
        if (filter == null) throw new IllegalArgumentException("Filter is required.");
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt", "notificationId"));
        var result = switch (filter) {
            case "all" -> repository.findByReceiverUserId(userId, pageable);
            case "unread" -> repository.findByReceiverUserIdAndReadAtIsNull(userId, pageable);
            case "read" -> repository.findByReceiverUserIdAndReadAtIsNotNull(userId, pageable);
            default -> throw new IllegalArgumentException("Filter must be all, unread, or read.");
        };
        return result.map(NotificationResponse::from);
    }

    @Override
    public long unreadCount(Long userId) {
        requireUser(userId);
        return repository.countByReceiverUserIdAndReadAtIsNull(userId);
    }

    static void requireUser(Long userId) {
        if (userId == null || userId <= 0) throw new IllegalArgumentException("A valid user ID is required.");
    }
}
