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
//    A transaction is visible only when the current user owns
//    the source wallet or destination wallet.
//    It could technically be expressed with a very long derived method name, such as:
//    findByTransactionIdAndSourceWallet_UserIdOrTransactionIdAndDestinationWallet_UserId(...)
//    in that case we might skip the @Query
    @Query("""
            SELECT t
            FROM Transaction t
            WHERE t.transactionId = :transactionId
              AND (
                    t.sourceWallet.userId = :userId
                    OR t.destinationWallet.userId = :userId
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
            WHERE t.sourceWallet.userId = :userId
               OR t.destinationWallet.userId = :userId
            ORDER BY t.createdAt DESC
            """)
    Page<Transaction> findAllVisibleTransactions(
            @Param("userId") Long userId,
            Pageable pageable
    );
}