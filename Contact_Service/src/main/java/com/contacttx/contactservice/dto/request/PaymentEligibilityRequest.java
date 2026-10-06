package com.contacttx.contactservice.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class PaymentEligibilityRequest {

    @NotNull(message = "senderUserId is required")
    @Positive(message = "senderUserId must be positive")
    private Long senderUserId;

    @NotNull(message = "receiverUserId is required")
    @Positive(message = "receiverUserId must be positive")
    private Long receiverUserId;

    public PaymentEligibilityRequest() {
    }

    public PaymentEligibilityRequest(Long senderUserId, Long receiverUserId) {
        this.senderUserId = senderUserId;
        this.receiverUserId = receiverUserId;
    }

    public Long getSenderUserId() {
        return senderUserId;
    }

    public void setSenderUserId(Long senderUserId) {
        this.senderUserId = senderUserId;
    }

    public Long getReceiverUserId() {
        return receiverUserId;
    }

    public void setReceiverUserId(Long receiverUserId) {
        this.receiverUserId = receiverUserId;
    }
}
