package com.contacttx.userservice.service;

import com.contacttx.userservice.entity.AppUser;
import com.contacttx.userservice.entity.UserStatus;
import com.contacttx.userservice.exception.ConcurrentUpdateException;
import com.contacttx.userservice.exception.InactiveUserException;
import com.contacttx.userservice.exception.UserNotFoundException;
import com.contacttx.userservice.repository.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserDeactivationService {

    private final AppUserRepository userRepository;

    public UserDeactivationService(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public void deactivate(Long userId) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new InactiveUserException();
        }

        int updatedRows = userRepository.updateStatusIfUnchanged(
                userId,
                UserStatus.ACTIVE,
                UserStatus.INACTIVE,
                user.getUpdatedAt());

        if (updatedRows == 0) {
            throw new ConcurrentUpdateException();
        }
    }
}
