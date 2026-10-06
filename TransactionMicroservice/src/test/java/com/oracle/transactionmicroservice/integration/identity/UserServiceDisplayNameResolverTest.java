package com.oracle.transactionmicroservice.integration.identity;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class UserServiceDisplayNameResolverTest {

    private MockRestServiceServer mockServer;
    private UserServiceDisplayNameResolver resolver;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        resolver = new UserServiceDisplayNameResolver(
                builder, "USER-SERVICE", "test-internal-token");
    }

    @AfterEach
    void verifyRequests() {
        mockServer.verify();
    }

    @Test
    void resolvesNamesInOneAuthenticatedBulkRequest() {
        mockServer.expect(requestTo(
                        "http://USER-SERVICE/internal/v1/users/display-names"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Service-Token", "test-internal-token"))
                .andExpect(content().json("""
                        {
                          "userIds": [7, 12]
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "users": [
                            {"userId": 7, "displayName": "Alice Sharma"},
                            {"userId": 12, "displayName": "Bob Tester"}
                          ],
                          "unresolvedUserIds": []
                        }
                        """, MediaType.APPLICATION_JSON));

        assertEquals(Map.of(7L, "Alice Sharma", 12L, "Bob Tester"),
                resolver.resolve(Set.of(7L, 12L)));
    }

    @Test
    void fallsBackToNoNamesWhenUserServiceIsUnavailable() {
        mockServer.expect(requestTo(
                        "http://USER-SERVICE/internal/v1/users/display-names"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertEquals(Map.of(), resolver.resolve(Set.of(7L)));
    }
}
