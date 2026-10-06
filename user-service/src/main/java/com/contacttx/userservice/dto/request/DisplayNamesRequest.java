package com.contacttx.userservice.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public class DisplayNamesRequest {

    @NotEmpty(message = "At least one user ID is required")
    @Size(max = 200, message = "At most 200 user IDs can be resolved at once")
    private List<@NotNull @Positive Long> userIds;

    public DisplayNamesRequest() {
    }

    public DisplayNamesRequest(List<Long> userIds) {
        this.userIds = userIds;
    }

    public List<Long> getUserIds() {
        return userIds;
    }
}
