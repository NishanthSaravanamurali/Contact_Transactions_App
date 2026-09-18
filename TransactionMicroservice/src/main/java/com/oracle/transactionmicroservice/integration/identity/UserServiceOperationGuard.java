package com.oracle.transactionmicroservice.integration.identity;

import com.oracle.transactionmicroservice.exception.ForbiddenOperationException;
import com.oracle.transactionmicroservice.service.abstractions.UserOperationGuard;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.function.Supplier;

@Component
public class UserServiceOperationGuard implements UserOperationGuard {

    private final RestClient userServiceClient;
    private final String internalServiceToken;

    public UserServiceOperationGuard(
            @Qualifier("loadBalancedRestClientBuilder")
            RestClient.Builder restClientBuilder,

            @Value("${integration.user-service.service-id}")
            String userServiceId,

            @Value("${integration.user-service.internal-token}")
            String internalServiceToken
    ) {
        if (userServiceId == null || userServiceId.isBlank()) {
            throw new IllegalStateException("User Service ID is not configured.");
        }

        if (internalServiceToken == null || internalServiceToken.isBlank()) {
            throw new IllegalStateException("Internal service token is not configured.");
        }

        this.userServiceClient = restClientBuilder
                .baseUrl("http://" + userServiceId)
                .build();

        this.internalServiceToken = internalServiceToken;
    }

    @Override
    public <T> T withActiveUsers(
            Long currentUserId,
            Long recipientUserId,
            Supplier<T> action
    ) {
        requireActiveUser(currentUserId);

        if (recipientUserId != null) {
            requireActiveUser(recipientUserId);
        }

        return action.get();
    }

    private void requireActiveUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw new ForbiddenOperationException("User ID is invalid.");
        }

        try {
            UserStatusResponse response = userServiceClient.get()
                    .uri("/internal/v1/users/{userId}/status", userId)
                    .header("X-Internal-Service-Token", internalServiceToken)
                    .retrieve()
                    .body(UserStatusResponse.class);

            if (response == null || !"ACTIVE".equalsIgnoreCase(response.status())) {
                throw new ForbiddenOperationException("User is not active.");
            }
        } catch (RestClientException exception) {
            throw new ForbiddenOperationException(
                    "Unable to verify the user's active status."
            );
        }
    }

    /*
     * Confirm this matches the actual JSON returned by User Service.
     * Expected example: { "status": "ACTIVE" }
     */
    private record UserStatusResponse(String status) {
    }
}