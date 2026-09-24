package com.contacttx.contactservice.dto.response;

public class PaymentEligibilityResponse {
    private final boolean allowed;
    private final PaymentEligibilityReason reason;
    private final String senderNameForReceiver;

    public PaymentEligibilityResponse(boolean allowed, PaymentEligibilityReason reason) {
        this(allowed, reason, null);
    }

    public PaymentEligibilityResponse(boolean allowed, PaymentEligibilityReason reason,
                                      String senderNameForReceiver) {
        this.allowed = allowed;
        this.reason = reason;
        this.senderNameForReceiver = allowed ? senderNameForReceiver : null;
    }

    public boolean isAllowed() { return allowed; }
    public PaymentEligibilityReason getReason() { return reason; }
    public String getSenderNameForReceiver() { return senderNameForReceiver; }

    public static PaymentEligibilityResponse eligible() { return eligible(null); }

    public static PaymentEligibilityResponse eligible(String senderNameForReceiver) {
        return new PaymentEligibilityResponse(true, PaymentEligibilityReason.ELIGIBLE, senderNameForReceiver);
    }

    public static PaymentEligibilityResponse denied(PaymentEligibilityReason reason) {
        return new PaymentEligibilityResponse(false, reason);
    }
}
