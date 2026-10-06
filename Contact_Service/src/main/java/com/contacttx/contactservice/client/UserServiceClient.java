package com.contacttx.contactservice.client;

import com.contacttx.contactservice.client.dto.ResolveUserRequest;
import com.contacttx.contactservice.client.dto.ResolveUserResponse;
import com.contacttx.contactservice.client.dto.UserStatusResponse;
import com.contacttx.contactservice.exception.UserServiceUnavailableException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;

@Component
public class UserServiceClient {

    private static final String USER_SERVICE_BASE_URL = "http://USER-SERVICE";
    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Service-Token";

    private final RestClient restClient;
    private final String internalToken;

    public UserServiceClient(
            @Qualifier("userServiceRestClientBuilder") RestClient.Builder restClientBuilder,
            @Value("${contact-service.user-service.internal-token}") String internalToken) {

        if (internalToken.isBlank()) {
            throw new IllegalArgumentException("User Service internal token must be configured");
        }

        this.restClient = restClientBuilder
                .baseUrl(USER_SERVICE_BASE_URL)
                .build();
        this.internalToken = internalToken;
    }

    public Optional<UserStatusResponse> getUserStatus(Long userId) {
        try {
            UserStatusResponse response = restClient.get()
                    .uri("/internal/v1/users/{userId}/status", userId)
                    .header(INTERNAL_TOKEN_HEADER, internalToken)
                    .retrieve()
                    .body(UserStatusResponse.class);

            validateStatusResponse(response);
            return Optional.of(response);
        } catch (HttpClientErrorException.NotFound exception) {
            return Optional.empty();
        } catch (RestClientException exception) {
            throw new UserServiceUnavailableException(exception);
        }
    }

    public Optional<ResolveUserResponse> resolveUser(String mobileNo) {
        try {
            ResolveUserResponse response = restClient.post()
                    .uri("/internal/v1/users/resolve")
                    .header(INTERNAL_TOKEN_HEADER, internalToken)
                    .body(new ResolveUserRequest(mobileNo))
                    .retrieve()
                    .body(ResolveUserResponse.class);

            validateResolveResponse(response);
            return Optional.of(response);
        } catch (HttpClientErrorException.NotFound exception) {
            return Optional.empty();
        } catch (RestClientException exception) {
            throw new UserServiceUnavailableException(exception);
        }
    }

    private void validateStatusResponse(UserStatusResponse response) {
        if (response == null
                || response.getUserId() == null
                || response.getUserId() <= 0
                || response.getStatus() == null
                || response.getStatus().isBlank()) {
            throw invalidResponse();
        }
    }

    private void validateResolveResponse(ResolveUserResponse response) {
        if (response == null
                || response.getUserId() == null
                || response.getUserId() <= 0
                || response.getStatus() == null
                || response.getStatus().isBlank()) {
            throw invalidResponse();
        }
    }

    private UserServiceUnavailableException invalidResponse() {
        return new UserServiceUnavailableException(
                new IllegalStateException("User Service returned an invalid response")
        );
    }
}
