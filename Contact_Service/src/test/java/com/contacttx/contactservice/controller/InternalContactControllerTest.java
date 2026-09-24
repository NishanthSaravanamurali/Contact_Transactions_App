package com.contacttx.contactservice.controller;

import com.contacttx.contactservice.dto.request.PaymentEligibilityRequest;
import com.contacttx.contactservice.dto.response.PaymentEligibilityReason;
import com.contacttx.contactservice.dto.response.PaymentEligibilityResponse;
import com.contacttx.contactservice.service.ContactService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalContactControllerTest {

    @Mock
    private ContactService contactService;

    @InjectMocks
    private InternalContactController internalContactController;

    @Test
    void returnsTheEligibilityDecisionForTheSuppliedUsers() {
        PaymentEligibilityRequest request = new PaymentEligibilityRequest(42L, 99L);
        PaymentEligibilityResponse expected = PaymentEligibilityResponse.eligible();
        when(contactService.checkPaymentEligibility(42L, 99L)).thenReturn(expected);

        PaymentEligibilityResponse response = internalContactController.paymentEligibility(request);

        assertTrue(response.isAllowed());
        org.junit.jupiter.api.Assertions.assertEquals(
                PaymentEligibilityReason.ELIGIBLE,
                response.getReason());
        verify(contactService).checkPaymentEligibility(42L, 99L);
    }
}
