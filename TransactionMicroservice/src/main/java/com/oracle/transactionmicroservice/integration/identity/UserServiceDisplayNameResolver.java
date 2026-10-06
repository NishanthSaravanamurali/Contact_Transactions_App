package com.oracle.transactionmicroservice.integration.identity;

import com.oracle.transactionmicroservice.service.abstractions.UserDisplayNameResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class UserServiceDisplayNameResolver implements UserDisplayNameResolver {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(UserServiceDisplayNameResolver.class);

    private final RestClient userServiceClient;
    private final String internalServiceToken;

    public UserServiceDisplayNameResolver(
            @Qualifier("loadBalancedRestClientBuilder") RestClient.Builder restClientBuilder,
            @Value("${integration.user-service.service-id}") String userServiceId,
            @Value("${integration.user-service.internal-token}") String internalServiceToken
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
    public Map<Long, String> resolve(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }

        try {
            DisplayNamesResponse response = userServiceClient.post()
                    .uri("/internal/v1/users/display-names")
                    .header("X-Internal-Service-Token", internalServiceToken)
                    .body(new DisplayNamesRequest(List.copyOf(userIds)))
                    .retrieve()
                    .body(DisplayNamesResponse.class);

            if (response == null || response.users() == null) {
                return Map.of();
            }

            Map<Long, String> names = new LinkedHashMap<>();
            for (DisplayNameEntry user : response.users()) {
                if (user != null && user.userId() != null && userIds.contains(user.userId())
                        && user.displayName() != null && !user.displayName().isBlank()) {
                    names.put(user.userId(), user.displayName());
                }
            }
            return Map.copyOf(names);
        } catch (RestClientException exception) {
            LOGGER.warn("Unable to resolve transaction display names; using generic labels: {}",
                    exception.getMessage());
            return Map.of();
        }
    }

    private record DisplayNamesRequest(List<Long> userIds) {
    }

    private record DisplayNameEntry(Long userId, String displayName) {
    }

    private record DisplayNamesResponse(
            List<DisplayNameEntry> users,
            List<Long> unresolvedUserIds
    ) {
    }
}
