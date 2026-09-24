package com.oracle.notificationservice.repository;

import com.oracle.notificationservice.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.OffsetDateTime;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    boolean existsByTransactionId(Long transactionId);
    boolean existsByNotificationIdAndReceiverUserId(Long notificationId, Long receiverUserId);
    long countByReceiverUserIdAndReadAtIsNull(Long receiverUserId);

    Page<Notification> findByReceiverUserId(Long receiverUserId, Pageable pageable);
    Page<Notification> findByReceiverUserIdAndReadAtIsNull(Long receiverUserId, Pageable pageable);
    Page<Notification> findByReceiverUserIdAndReadAtIsNotNull(Long receiverUserId, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE Notification n SET n.readAt = :readAt
        WHERE n.notificationId = :id AND n.receiverUserId = :userId AND n.readAt IS NULL
        """)
    int markRead(@Param("id") Long id, @Param("userId") Long userId,
                 @Param("readAt") OffsetDateTime readAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE Notification n SET n.readAt = :readAt
        WHERE n.receiverUserId = :userId AND n.readAt IS NULL
        """)
    int markAllRead(@Param("userId") Long userId, @Param("readAt") OffsetDateTime readAt);
}
