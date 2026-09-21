package com.contacttx.contactservice.client;

import com.contacttx.contactservice.client.dto.ResolveUserResponse;
import com.contacttx.contactservice.client.dto.UserStatusResponse;
import com.contacttx.contactservice.exception.UserServiceUnavailableException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class UserServiceClientTest {

    private static final String TEST_INTERNAL_TOKEN = "test-internal-token";

    private MockRestServiceServer mockServer;
    private UserServiceClient userServiceClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        userServiceClient = new UserServiceClient(restClientBuilder, TEST_INTERNAL_TOKEN);
    }

    @AfterEach
    void verifyRequests() {
        mockServer.verify();
    }

    @Test
    void getsUserStatusWithTheInternalToken() {
        mockServer.expect(requestTo(
                        "http://USER-SERVICE/internal/v1/users/101/status"
                ))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Service-Token", TEST_INTERNAL_TOKEN))
                .andRespond(withSuccess(
                        """
                        {
                          "userId": 101,
                          "status": "ACTIVE"
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        Optional<UserStatusResponse> response = userServiceClient.getUserStatus(101L);

        assertThat(response).isPresent();
        assertThat(response.orElseThrow().getUserId()).isEqualTo(101L);
        assertThat(response.orElseThrow().getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void resolvesMobileNumberWithTheExpectedJsonAndToken() {
        mockServer.expect(requestTo(
                        "http://USER-SERVICE/internal/v1/users/resolve"
                ))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Service-Token", TEST_INTERNAL_TOKEN))
                .andExpect(content().json(
                        """
                        {
                          "mobileNo": "9876543210"
                        }
                        """
                ))
                .andRespond(withSuccess(
                        """
                        {
                          "userId": 101,
                          "status": "ACTIVE"
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        Optional<ResolveUserResponse> response =
                userServiceClient.resolveUser("9876543210");

        assertThat(response).isPresent();
        assertThat(response.orElseThrow().getUserId()).isEqualTo(101L);
        assertThat(response.orElseThrow().getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void returnsEmptyWhenUserServiceReturnsNotFound() {
        mockServer.expect(requestTo(
                        "http://USER-SERVICE/internal/v1/users/999/status"
                ))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(userServiceClient.getUserStatus(999L)).isEmpty();
    }

    @Test
    void translatesServerFailureToSafeUnavailableException() {
        mockServer.expect(requestTo(
                        "http://USER-SERVICE/internal/v1/users/resolve"
                ))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> userServiceClient.resolveUser("9876543210"))
                .isInstanceOf(UserServiceUnavailableException.class)
                .hasMessage("User Service is currently unavailable");
    }

    @Test
    void rejectsInvalidSuccessfulResponse() {
        mockServer.expect(requestTo(
                        "http://USER-SERVICE/internal/v1/users/101/status"
                ))
                .andRespond(withSuccess(
                        """
                        {
                          "status": "ACTIVE"
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        assertThatThrownBy(() -> userServiceClient.getUserStatus(101L))
                .isInstanceOf(UserServiceUnavailableException.class)
                .hasMessage("User Service is currently unavailable");
    }
}
