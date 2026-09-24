package com.contacttx.userservice.mapper;

import com.contacttx.userservice.dto.response.RegistrationResponse;
import com.contacttx.userservice.dto.response.UserResponse;
import com.contacttx.userservice.entity.AppUser;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public RegistrationResponse toRegistrationResponse(AppUser user) {
        return new RegistrationResponse(
                user.getUserId(),
                user.getEmail(),
                user.getStatus(),
                user.getCreatedAt());
    }

    public UserResponse toResponse(AppUser user) {
        return new UserResponse(
                user.getUserId(),
                user.getName(),
                user.getEmail(),
                String.valueOf(user.getMobileNo()),
                user.getDateOfBirth(),
                user.getStatus(),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }
}
