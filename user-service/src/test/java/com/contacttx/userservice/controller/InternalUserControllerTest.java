package com.contacttx.userservice.controller;

import com.contacttx.userservice.dto.request.ResolveUserRequest;
import com.contacttx.userservice.dto.response.InternalUserStatusResponse;
import com.contacttx.userservice.entity.UserStatus;
import com.contacttx.userservice.service.InternalUserService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InternalUserControllerTest {

    @Test
    void returnsTheStatusContractForAUserId() {
        InternalUserService service = mock(InternalUserService.class);
        InternalUserController controller = new InternalUserController(service);
        InternalUserStatusResponse expected =
                new InternalUserStatusResponse(42L, UserStatus.ACTIVE);
        when(service.getStatus(42L)).thenReturn(expected);

        ResponseEntity<InternalUserStatusResponse> response = controller.getStatus(42L);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(expected, response.getBody());
        verify(service).getStatus(42L);
    }

    @Test
    void returnsTheStatusContractForMobileResolution() {
        InternalUserService service = mock(InternalUserService.class);
        InternalUserController controller = new InternalUserController(service);
        ResolveUserRequest request = new ResolveUserRequest("9876543210");
        InternalUserStatusResponse expected =
                new InternalUserStatusResponse(42L, UserStatus.ACTIVE);
        when(service.resolveByMobile(request)).thenReturn(expected);

        ResponseEntity<InternalUserStatusResponse> response =
                controller.resolveByMobile(request);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(expected, response.getBody());
        verify(service).resolveByMobile(request);
    }
}
