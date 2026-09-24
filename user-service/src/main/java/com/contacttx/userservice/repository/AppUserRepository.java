package com.contacttx.userservice.repository;

import com.contacttx.userservice.entity.AppUser;
import com.contacttx.userservice.entity.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<AppUser> findByUserIdAndStatus(Long userId, UserStatus status);

    Optional<AppUser> findFirstByMobileNoOrderByUserIdAsc(Long mobileNo);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AppUser user
               set user.name = :name,
                   user.mobileNo = :mobileNo
             where user.userId = :userId
               and user.status = :status
               and user.updatedAt = :expectedUpdatedAt
            """)
    int updateProfileIfUnchanged(
            @Param("userId") Long userId,
            @Param("status") UserStatus status,
            @Param("expectedUpdatedAt") LocalDateTime expectedUpdatedAt,
            @Param("name") String name,
            @Param("mobileNo") Long mobileNo);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AppUser user
               set user.passwordHash = :passwordHash
             where user.userId = :userId
               and user.status = :status
               and user.updatedAt = :expectedUpdatedAt
            """)
    int updatePasswordIfUnchanged(
            @Param("userId") Long userId,
            @Param("status") UserStatus status,
            @Param("expectedUpdatedAt") LocalDateTime expectedUpdatedAt,
            @Param("passwordHash") String passwordHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AppUser user
               set user.status = :newStatus
             where user.userId = :userId
               and user.status = :expectedStatus
               and user.updatedAt = :expectedUpdatedAt
            """)
    int updateStatusIfUnchanged(
            @Param("userId") Long userId,
            @Param("expectedStatus") UserStatus expectedStatus,
            @Param("newStatus") UserStatus newStatus,
            @Param("expectedUpdatedAt") LocalDateTime expectedUpdatedAt);
}
