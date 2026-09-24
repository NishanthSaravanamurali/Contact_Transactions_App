package com.contacttx.contactservice.controller;

import com.contacttx.contactservice.dto.request.PaymentEligibilityRequest;
import com.contacttx.contactservice.dto.response.PaymentEligibilityResponse;
import com.contacttx.contactservice.service.ContactService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/contacts")
public class InternalContactController {

    private final ContactService contactService;

    public InternalContactController(ContactService contactService) {
        this.contactService = contactService;
    }

    @PostMapping("/payment-eligibility")
    public PaymentEligibilityResponse paymentEligibility(
            @Valid @RequestBody PaymentEligibilityRequest request) {
        return contactService.checkPaymentEligibility(
                request.getSenderUserId(),
                request.getReceiverUserId()
        );
    }
}
