package com.contacttx.contactservice.dto.response;

public class PaymentEligibilityResponse {

    private final boolean allowed;
    private final PaymentEligibilityReason reason;

    public PaymentEligibilityResponse(
            boolean allowed,
            PaymentEligibilityReason reason) {
        this.allowed = allowed;
        this.reason = reason;
    }

    public boolean isAllowed() {
        return allowed;
    }

    public PaymentEligibilityReason getReason() {
        return reason;
    }

    public static PaymentEligibilityResponse eligible() {
        return new PaymentEligibilityResponse(
                true,
                PaymentEligibilityReason.ELIGIBLE);
    }

    public static PaymentEligibilityResponse denied(
            PaymentEligibilityReason reason) {
        return new PaymentEligibilityResponse(false, reason);
    }
}
