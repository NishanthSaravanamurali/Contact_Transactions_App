package com.contacttx.contactservice.controller;

import com.contacttx.contactservice.dto.request.CreateContactRequest;
import com.contacttx.contactservice.dto.request.UpdateContactRequest;
import com.contacttx.contactservice.dto.response.ContactResponse;
import com.contacttx.contactservice.security.AuthenticatedUserIdResolver;
import com.contacttx.contactservice.service.ContactService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/contacts")
public class ContactController {

    private final ContactService contactService;
    private final AuthenticatedUserIdResolver authenticatedUserIdResolver;

    public ContactController(
            ContactService contactService,
            AuthenticatedUserIdResolver authenticatedUserIdResolver) {
        this.contactService = contactService;
        this.authenticatedUserIdResolver = authenticatedUserIdResolver;
    }

    @PostMapping
    public ResponseEntity<ContactResponse> createContact(
            Authentication authentication,
            @Valid @RequestBody CreateContactRequest request) {
        ContactResponse response = contactService.createContact(ownerUserId(authentication), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public List<ContactResponse> listContacts(Authentication authentication) {
        return contactService.listContacts(ownerUserId(authentication));
    }

    @GetMapping("/{contactId}")
    public ContactResponse getContact(
            Authentication authentication,
            @PathVariable Long contactId) {
        return contactService.getContact(ownerUserId(authentication), contactId);
    }

    @PutMapping("/{contactId}")
    public ContactResponse updateContact(
            Authentication authentication,
            @PathVariable Long contactId,
            @Valid @RequestBody UpdateContactRequest request) {
        return contactService.updateContact(ownerUserId(authentication), contactId, request);
    }

    @DeleteMapping("/{contactId}")
    public ResponseEntity<Void> deleteContact(
            Authentication authentication,
            @PathVariable Long contactId) {
        contactService.deleteContact(ownerUserId(authentication), contactId);
        return ResponseEntity.noContent().build();
    }

    private Long ownerUserId(Authentication authentication) {
        return authenticatedUserIdResolver.resolve(authentication);
    }
}
