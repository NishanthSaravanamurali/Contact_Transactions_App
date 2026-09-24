package com.oracle.transactionmicroservice.repository;

import com.oracle.transactionmicroservice.entity.PaymentOutboxEvent;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentOutboxEventRepository extends JpaRepository<PaymentOutboxEvent, Long> {
    @Query("""
        SELECT e.transactionId FROM PaymentOutboxEvent e
        WHERE e.publishedAt IS NULL AND e.nextAttemptAt <= :now
        ORDER BY e.nextAttemptAt, e.transactionId
        """)
    List<Long> findPendingIds(@Param("now") LocalDateTime now, Pageable pageable);

    // Lock only an outbox row, never wallets, while waiting for Kafka acknowledgement.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("SELECT e FROM PaymentOutboxEvent e WHERE e.transactionId = :id")
    Optional<PaymentOutboxEvent> findForPublishing(@Param("id") Long id);

    @Query("""
        SELECT e.transactionId FROM PaymentOutboxEvent e
        WHERE e.publishedAt IS NOT NULL AND e.publishedAt < :cutoff
        ORDER BY e.publishedAt, e.transactionId
        """)
    List<Long> findExpiredPublishedIds(@Param("cutoff") LocalDateTime cutoff, Pageable pageable);
}
