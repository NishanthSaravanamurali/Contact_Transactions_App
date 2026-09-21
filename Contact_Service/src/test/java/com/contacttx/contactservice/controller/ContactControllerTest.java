package com.contacttx.contactservice.controller;

import com.contacttx.contactservice.dto.request.CreateContactRequest;
import com.contacttx.contactservice.dto.request.UpdateContactRequest;
import com.contacttx.contactservice.dto.response.ContactResponse;
import com.contacttx.contactservice.security.AuthenticatedUserIdResolver;
import com.contacttx.contactservice.service.ContactService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContactControllerTest {

    private static final Long OWNER_USER_ID = 101L;
    private static final Long CONTACT_ID = 55L;

    @Mock
    private ContactService contactService;

    @Mock
    private AuthenticatedUserIdResolver authenticatedUserIdResolver;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private ContactController contactController;

    @Test
    void createContactUsesAuthenticatedOwnerAndReturnsCreated() {
        CreateContactRequest request = new CreateContactRequest("Sam Taylor", "9876543210", false);
        ContactResponse expected = new ContactResponse();
        when(authenticatedUserIdResolver.resolve(authentication)).thenReturn(OWNER_USER_ID);
        when(contactService.createContact(OWNER_USER_ID, request)).thenReturn(expected);

        ResponseEntity<ContactResponse> response = contactController.createContact(authentication, request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertSame(expected, response.getBody());
        verify(contactService).createContact(OWNER_USER_ID, request);
    }

    @Test
    void listContactsUsesAuthenticatedOwner() {
        List<ContactResponse> expected = List.of(new ContactResponse());
        when(authenticatedUserIdResolver.resolve(authentication)).thenReturn(OWNER_USER_ID);
        when(contactService.listContacts(OWNER_USER_ID)).thenReturn(expected);

        assertSame(expected, contactController.listContacts(authentication));
        verify(contactService).listContacts(OWNER_USER_ID);
    }

    @Test
    void getContactUsesAuthenticatedOwner() {
        ContactResponse expected = new ContactResponse();
        when(authenticatedUserIdResolver.resolve(authentication)).thenReturn(OWNER_USER_ID);
        when(contactService.getContact(OWNER_USER_ID, CONTACT_ID)).thenReturn(expected);

        assertSame(expected, contactController.getContact(authentication, CONTACT_ID));
        verify(contactService).getContact(OWNER_USER_ID, CONTACT_ID);
    }

    @Test
    void updateContactUsesAuthenticatedOwner() {
        UpdateContactRequest request = new UpdateContactRequest("Sam Taylor", "9876543210", false);
        ContactResponse expected = new ContactResponse();
        when(authenticatedUserIdResolver.resolve(authentication)).thenReturn(OWNER_USER_ID);
        when(contactService.updateContact(OWNER_USER_ID, CONTACT_ID, request)).thenReturn(expected);

        assertSame(expected, contactController.updateContact(authentication, CONTACT_ID, request));
        verify(contactService).updateContact(OWNER_USER_ID, CONTACT_ID, request);
    }

    @Test
    void deleteContactUsesAuthenticatedOwnerAndReturnsNoContent() {
        when(authenticatedUserIdResolver.resolve(authentication)).thenReturn(OWNER_USER_ID);

        ResponseEntity<Void> response = contactController.deleteContact(authentication, CONTACT_ID);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(contactService).deleteContact(OWNER_USER_ID, CONTACT_ID);
    }
}
