package com.oracle.transactionmicroservice.integration.identity;

import com.oracle.transactionmicroservice.exception.ForbiddenOperationException;
import com.oracle.transactionmicroservice.service.abstractions.UserOperationGuard;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.function.Supplier;
import java.util.function.Function;

@Component
public class UserServiceOperationGuard implements UserOperationGuard {

    private final RestClient userServiceClient;
    private final RestClient contactServiceClient;
    private final String internalServiceToken;

    public UserServiceOperationGuard(
            @Qualifier("loadBalancedRestClientBuilder")
            RestClient.Builder restClientBuilder,

            @Value("${integration.user-service.service-id}")
            String userServiceId,

            @Value("${integration.contact-service.service-id}")
            String contactServiceId,

            @Value("${integration.user-service.internal-token}")
            String internalServiceToken
    ) {
        if (userServiceId == null || userServiceId.isBlank()) {
            throw new IllegalStateException("User Service ID is not configured.");
        }

        if (contactServiceId == null || contactServiceId.isBlank()) {
            throw new IllegalStateException("Contact Service ID is not configured.");
        }

        if (internalServiceToken == null || internalServiceToken.isBlank()) {
            throw new IllegalStateException("Internal service token is not configured.");
        }

        this.userServiceClient = restClientBuilder
                .baseUrl("http://" + userServiceId)
                .build();

        this.contactServiceClient = restClientBuilder
                .baseUrl("http://" + contactServiceId)
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
            requireReceiverIsContact(currentUserId, recipientUserId);
        }

        return action.get();
    }

    @Override
    public <T> T withPaymentUsers(Long senderUserId, Long receiverUserId, Function<String, T> action) {
        UserStatusResponse sender = requireActiveUser(senderUserId);
        requireActiveUser(receiverUserId);
        PaymentEligibilityResponse eligibility = requireReceiverIsContact(senderUserId, receiverUserId);
        String contactName = eligibility.senderNameForReceiver();
        String selectedName = contactName != null && !contactName.isBlank() ? contactName : sender.name();
        if (selectedName == null || selectedName.isBlank() || selectedName.strip().length() > 100) {
            throw new ForbiddenOperationException("Unable to resolve the sender's display name.");
        }
        return action.apply(selectedName.strip());
    }

    private UserStatusResponse requireActiveUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw new ForbiddenOperationException("User ID is invalid.");
        }

        try {
            UserStatusResponse response = userServiceClient.get()
                    .uri("/internal/v1/users/{userId}/status", userId)
                    .header("X-Internal-Service-Token", internalServiceToken)
                    .retrieve()
                    .body(UserStatusResponse.class);

            if (response == null || !userId.equals(response.userId()) || !"ACTIVE".equalsIgnoreCase(response.status())) {
                throw new ForbiddenOperationException("User is not active.");
            }
            return response;
        } catch (RestClientException exception) {
            throw new ForbiddenOperationException(
                    "Unable to verify the user's active status."
            );
        }
    }

    private PaymentEligibilityResponse requireReceiverIsContact(
            Long senderUserId,
            Long receiverUserId
    ) {
        try {
            PaymentEligibilityResponse response = contactServiceClient.post()
                    .uri("/internal/v1/contacts/payment-eligibility")
                    .header("X-Internal-Service-Token", internalServiceToken)
                    .body(new PaymentEligibilityRequest(senderUserId, receiverUserId))
                    .retrieve()
                    .body(PaymentEligibilityResponse.class);

            if (response == null) {
                throw new ForbiddenOperationException(
                        "Unable to verify the receiver's contact eligibility."
                );
            }
            if (!response.allowed()) {
                if ("CONTACT_PHONE_MISMATCH".equals(response.reason())) {
                    throw new ForbiddenOperationException(
                            "CONTACT_PHONE_MISMATCH",
                            "This contact’s phone number no longer matches the registered user. "
                                    + "Update the contact before transferring money."
                    );
                }
                throw new ForbiddenOperationException(
                        "Receiver is not an eligible contact."
                );
            }
            return response;
        } catch (RestClientException exception) {
            throw new ForbiddenOperationException(
                    "Unable to verify the receiver's contact eligibility."
            );
        }
    }

    private record UserStatusResponse(Long userId, String status, String name) {
    }

    private record PaymentEligibilityRequest(
            Long senderUserId,
            Long receiverUserId
    ) {
    }

    private record PaymentEligibilityResponse(boolean allowed, String reason, String senderNameForReceiver) {
    }
}
