package com.oracle.transactionmicroservice.repository;

import com.oracle.transactionmicroservice.entity.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    List<Account> findAllByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT a
            FROM Account a
            WHERE a.accountId = :accountId
              AND a.userId = :userId
            """)
    Optional<Account> findByAccountIdAndUserIdForUpdate(
            @Param("accountId") Long accountId,
            @Param("userId") Long userId
    );
}