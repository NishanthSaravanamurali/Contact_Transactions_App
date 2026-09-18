package com.oracle.transactionmicroservice.repository;

import com.oracle.transactionmicroservice.entity.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WalletRepository extends JpaRepository<Wallet, Long> {

    Optional<Wallet> findByUserId(Long userId);

//    The pessimistic-lock methods are important:
//    later, when two payments happen at the same time,
//    they prevent both requests from spending the same balance.

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT w
            FROM Wallet w
            WHERE w.userId = :userId
            """)
    Optional<Wallet> findByUserIdForUpdate(@Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT w
            FROM Wallet w
            WHERE w.userId IN :userIds
            ORDER BY w.walletId
            """)
    List<Wallet> findAllByUserIdInForUpdate(
            @Param("userIds") Collection<Long> userIds
    );
}