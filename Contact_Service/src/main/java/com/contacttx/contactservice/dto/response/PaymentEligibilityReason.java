package com.contacttx.contactservice.dto.response;

public enum PaymentEligibilityReason {
    ELIGIBLE,
    SELF_PAYMENT,
    CONTACT_NOT_FOUND,
    CONTACT_PHONE_MISMATCH,
    RECEIVER_INACTIVE
}
