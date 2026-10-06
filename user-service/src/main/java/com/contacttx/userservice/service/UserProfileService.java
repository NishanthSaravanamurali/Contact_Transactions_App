package com.contacttx.userservice.service;

import com.contacttx.userservice.dto.request.UpdateProfileRequest;
import com.contacttx.userservice.dto.response.UserResponse;
import com.contacttx.userservice.entity.AppUser;
import com.contacttx.userservice.entity.UserStatus;
import com.contacttx.userservice.exception.ConcurrentUpdateException;
import com.contacttx.userservice.exception.InactiveUserException;
import com.contacttx.userservice.exception.UserNotFoundException;
import com.contacttx.userservice.mapper.UserMapper;
import com.contacttx.userservice.repository.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserProfileService {

    private final AppUserRepository userRepository;
    private final UserMapper userMapper;

    public UserProfileService(AppUserRepository userRepository, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
    }

    @Transactional(readOnly = true)
    public UserResponse getProfile(Long userId) {
        return userMapper.toResponse(requireActiveUser(userId));
    }

    @Transactional
    public UserResponse updateProfile(Long userId, UpdateProfileRequest request) {
        AppUser currentUser = requireActiveUser(userId);

        String updatedName = request.getName() == null
                ? currentUser.getName()
                : request.getName();
        Long updatedMobileNo = request.getMobileNo() == null
                ? currentUser.getMobileNo()
                : Long.valueOf(request.getMobileNo());

        int updatedRows = userRepository.updateProfileIfUnchanged(
                userId,
                UserStatus.ACTIVE,
                currentUser.getUpdatedAt(),
                updatedName,
                updatedMobileNo);

        if (updatedRows == 0) {
            throw new ConcurrentUpdateException();
        }

        return userMapper.toResponse(requireActiveUser(userId));
    }

    private AppUser requireActiveUser(Long userId) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new InactiveUserException();
        }

        return user;
    }
}
