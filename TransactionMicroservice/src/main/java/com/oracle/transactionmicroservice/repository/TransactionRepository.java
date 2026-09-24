package com.oracle.transactionmicroservice.repository;

import com.oracle.transactionmicroservice.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    //    We use @Query where the rule is more complex or needs to be very explicit.
//    A completed transaction is visible to either wallet owner. A failed W2W
//    attempt is visible only to its source-wallet owner. A2W transactions are
//    visible to the source-account owner, including failed attempts.
//    It could technically be expressed with a very long derived method name, such as:
//    findByTransactionIdAndSourceWallet_UserIdOrTransactionIdAndDestinationWallet_UserId(...)
//    in that case we might skip the @Query
    @Query("""
            SELECT t
            FROM Transaction t
            LEFT JOIN t.sourceWallet sw
            LEFT JOIN t.sourceAccount sa
            JOIN t.destinationWallet dw
            WHERE t.transactionId = :transactionId
              AND (
                    sw.userId = :userId
                    OR sa.userId = :userId
                    OR (
                        t.status = com.oracle.transactionmicroservice.enums.TransactionStatus.COMPLETED
                        AND dw.userId = :userId
                    )
                  )
            """)
    Optional<Transaction> findVisibleTransactionById(
            @Param("transactionId") Long transactionId,
            @Param("userId") Long userId
    );

//    A Page contains both the transactions and paging information:
//    For example, the controller later can request:
//    GET /transactions?page=0&size=20

    @Query("""
        SELECT t
        FROM Transaction t
        LEFT JOIN t.sourceWallet sw
        LEFT JOIN t.sourceAccount sa
        JOIN t.destinationWallet dw
        WHERE sw.userId = :userId
           OR sa.userId = :userId
           OR (
               t.status = com.oracle.transactionmicroservice.enums.TransactionStatus.COMPLETED
               AND dw.userId = :userId
           )
        ORDER BY t.createdAt DESC, t.transactionId DESC
        """)
    Page<Transaction> findAllVisibleTransactions(
            @Param("userId") Long userId,
            Pageable pageable
    );
}
