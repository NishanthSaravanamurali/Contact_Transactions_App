package com.contacttx.userservice.controller;

import com.contacttx.userservice.dto.request.ResolveUserRequest;
import com.contacttx.userservice.dto.response.InternalUserStatusResponse;
import com.contacttx.userservice.service.InternalUserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/users")
public class InternalUserController {

    private final InternalUserService internalUserService;

    public InternalUserController(InternalUserService internalUserService) {
        this.internalUserService = internalUserService;
    }

    @GetMapping("/{userId}/status")
    public ResponseEntity<InternalUserStatusResponse> getStatus(
            @PathVariable Long userId) {
        return ResponseEntity.ok(internalUserService.getStatus(userId));
    }

    @PostMapping("/resolve")
    public ResponseEntity<InternalUserStatusResponse> resolveByMobile(
            @Valid @RequestBody ResolveUserRequest request) {
        return ResponseEntity.ok(internalUserService.resolveByMobile(request));
    }
}
