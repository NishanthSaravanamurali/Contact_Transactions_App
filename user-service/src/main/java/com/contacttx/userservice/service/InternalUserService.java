package com.contacttx.userservice.service;

import com.contacttx.userservice.dto.request.ResolveUserRequest;
import com.contacttx.userservice.dto.request.DisplayNamesRequest;
import com.contacttx.userservice.dto.response.InternalUserStatusResponse;
import com.contacttx.userservice.dto.response.UserDisplayNameResponse;
import com.contacttx.userservice.dto.response.UserDisplayNamesResponse;
import com.contacttx.userservice.entity.AppUser;
import com.contacttx.userservice.entity.UserStatus;
import com.contacttx.userservice.exception.UserNotFoundException;
import com.contacttx.userservice.repository.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    @Transactional(readOnly = true)
    public UserDisplayNamesResponse resolveDisplayNames(DisplayNamesRequest request) {
        var requestedUserIds = new ArrayList<>(new LinkedHashSet<>(request.getUserIds()));
        Map<Long, AppUserRepository.UserDisplayNameProjection> foundByUserId = userRepository
                .findDisplayNamesByUserIds(requestedUserIds, UserStatus.DELETED)
                .stream()
                .collect(Collectors.toMap(
                        AppUserRepository.UserDisplayNameProjection::getUserId,
                        Function.identity()));

        var users = requestedUserIds.stream()
                .filter(foundByUserId::containsKey)
                .map(userId -> {
                    var user = foundByUserId.get(userId);
                    return new UserDisplayNameResponse(user.getUserId(), user.getName());
                })
                .toList();
        var unresolvedUserIds = requestedUserIds.stream()
                .filter(userId -> !foundByUserId.containsKey(userId))
                .toList();

        return new UserDisplayNamesResponse(users, unresolvedUserIds);
    }

    private InternalUserStatusResponse toResponse(AppUser user) {
        return new InternalUserStatusResponse(user.getUserId(), user.getStatus(), user.getName());
    }
}
