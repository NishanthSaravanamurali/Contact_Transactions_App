package com.contacttx.userservice.service;

import com.contacttx.userservice.dto.request.ChangePasswordRequest;
import com.contacttx.userservice.entity.AppUser;
import com.contacttx.userservice.entity.UserStatus;
import com.contacttx.userservice.exception.ConcurrentUpdateException;
import com.contacttx.userservice.exception.InactiveUserException;
import com.contacttx.userservice.exception.InvalidCurrentPasswordException;
import com.contacttx.userservice.exception.UserNotFoundException;
import com.contacttx.userservice.repository.AppUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserPasswordService {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserPasswordService(
            AppUserRepository userRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new InactiveUserException();
        }

        if (!passwordEncoder.matches(
                request.getCurrentPassword(), user.getPasswordHash())) {
            throw new InvalidCurrentPasswordException();
        }

        String newPasswordHash = passwordEncoder.encode(request.getNewPassword());
        int updatedRows = userRepository.updatePasswordIfUnchanged(
                userId,
                UserStatus.ACTIVE,
                user.getUpdatedAt(),
                newPasswordHash);

        if (updatedRows == 0) {
            throw new ConcurrentUpdateException();
        }
    }
}
