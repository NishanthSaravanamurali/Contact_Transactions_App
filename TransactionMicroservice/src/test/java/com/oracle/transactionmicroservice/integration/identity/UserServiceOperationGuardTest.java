package com.oracle.transactionmicroservice.integration.identity;

import com.oracle.transactionmicroservice.exception.ForbiddenOperationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class UserServiceOperationGuardTest {

    private static final String INTERNAL_TOKEN = "test-internal-token";

    private MockRestServiceServer mockServer;
    private UserServiceOperationGuard guard;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        guard = new UserServiceOperationGuard(
                builder,
                "USER-SERVICE",
                "CONTACT-SERVICE",
                INTERNAL_TOKEN);
    }

    @AfterEach
    void verifyRequests() {
        mockServer.verify();
    }

    @Test
    void exposesTheStalePhoneReasonAndDoesNotRunTheMoneyAction() {
        expectActiveUser(42L);
        expectActiveUser(99L);
        mockServer.expect(requestTo(
                        "http://CONTACT-SERVICE/internal/v1/contacts/payment-eligibility"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Service-Token", INTERNAL_TOKEN))
                .andExpect(content().json("""
                        {
                          "senderUserId": 42,
                          "receiverUserId": 99
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "allowed": false,
                          "reason": "CONTACT_PHONE_MISMATCH"
                        }
                        """, MediaType.APPLICATION_JSON));

        AtomicBoolean actionCalled = new AtomicBoolean(false);
        ForbiddenOperationException exception = assertThrows(
                ForbiddenOperationException.class,
                () -> guard.withActiveUsers(42L, 99L, () -> {
                    actionCalled.set(true);
                    return "should not run";
                }));

        assertEquals("CONTACT_PHONE_MISMATCH", exception.getErrorCode());
        assertEquals(
                "This contact’s phone number no longer matches the registered user. "
                        + "Update the contact before transferring money.",
                exception.getMessage());
        assertFalse(actionCalled.get());
    }


    @Test
    void paymentPathPreservesStalePhoneErrorAndNeverRunsAction() {
        expectActiveUser(42L);
        expectActiveUser(99L);
        expectEligibility("{\"allowed\":false,\"reason\":\"CONTACT_PHONE_MISMATCH\",\"senderNameForReceiver\":\"Friend\"}");
        AtomicBoolean called = new AtomicBoolean();
        var error = assertThrows(ForbiddenOperationException.class, () ->
                guard.withPaymentUsers(42L, 99L, name -> { called.set(true); return name; }));
        assertEquals("CONTACT_PHONE_MISMATCH", error.getErrorCode());
        assertFalse(called.get());
    }

    @Test
    void paymentUsesReceiversSavedName() {
        expectActiveUser(42L);
        expectActiveUser(99L);
        expectEligibility("{\"allowed\":true,\"reason\":\"ELIGIBLE\",\"senderNameForReceiver\":\"College Friend\"}");
        assertEquals("College Friend", guard.withPaymentUsers(42L, 99L, name -> name));
    }

    @Test
    void missingReverseContactFallsBackToRegisteredName() {
        expectActiveUser(42L);
        expectActiveUser(99L);
        expectEligibility("{\"allowed\":true,\"reason\":\"ELIGIBLE\",\"senderNameForReceiver\":null}");
        assertEquals("Registered Sender", guard.withPaymentUsers(42L, 99L, name -> name));
    }

    @Test
    void contactServiceFailureDoesNotRunPayment() {
        expectActiveUser(42L);
        expectActiveUser(99L);
        mockServer.expect(requestTo("http://CONTACT-SERVICE/internal/v1/contacts/payment-eligibility"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withServerError());
        AtomicBoolean called = new AtomicBoolean();
        assertThrows(ForbiddenOperationException.class, () ->
                guard.withPaymentUsers(42L, 99L, name -> { called.set(true); return name; }));
        assertFalse(called.get());
    }

    private void expectEligibility(String json) {
        mockServer.expect(requestTo("http://CONTACT-SERVICE/internal/v1/contacts/payment-eligibility"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Service-Token", INTERNAL_TOKEN))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    private void expectActiveUser(Long userId) {
        mockServer.expect(requestTo(
                        "http://USER-SERVICE/internal/v1/users/" + userId + "/status"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Service-Token", INTERNAL_TOKEN))
                .andRespond(withSuccess("""
                        {
                          "userId": %d,
                          "name": "Registered Sender",
                          "status": "ACTIVE"
                        }
                        """.formatted(userId), MediaType.APPLICATION_JSON));
    }
}
