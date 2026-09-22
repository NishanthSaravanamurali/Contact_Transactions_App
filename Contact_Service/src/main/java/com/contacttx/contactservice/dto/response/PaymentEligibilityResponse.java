package com.contacttx.contactservice.dto.response;

public class PaymentEligibilityResponse {

    private final boolean allowed;

    public PaymentEligibilityResponse(boolean allowed) {
        this.allowed = allowed;
    }

    public boolean isAllowed() {
        return allowed;
    }
}
