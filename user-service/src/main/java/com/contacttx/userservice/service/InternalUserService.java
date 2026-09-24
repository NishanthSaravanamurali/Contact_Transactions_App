package com.contacttx.userservice.service;

import com.contacttx.userservice.dto.request.ResolveUserRequest;
import com.contacttx.userservice.dto.response.InternalUserStatusResponse;
import com.contacttx.userservice.entity.AppUser;
import com.contacttx.userservice.exception.UserNotFoundException;
import com.contacttx.userservice.repository.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InternalUserService {

    private final AppUserRepository userRepository;

    public InternalUserService(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public InternalUserStatusResponse getStatus(Long userId) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);
        return toResponse(user);
    }

    @Transactional(readOnly = true)
    public InternalUserStatusResponse resolveByMobile(ResolveUserRequest request) {
        Long mobileNo = Long.valueOf(request.getMobileNo());
        AppUser user = userRepository.findFirstByMobileNoOrderByUserIdAsc(mobileNo)
                .orElseThrow(UserNotFoundException::new);
        return toResponse(user);
    }

    private InternalUserStatusResponse toResponse(AppUser user) {
        return new InternalUserStatusResponse(user.getUserId(), user.getStatus());
    }
}
