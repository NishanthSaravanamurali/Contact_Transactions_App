package com.contacttx.userservice.dto.response;

import java.util.List;

public record UserDisplayNamesResponse(
        List<UserDisplayNameResponse> users,
        List<Long> unresolvedUserIds
) {
}
