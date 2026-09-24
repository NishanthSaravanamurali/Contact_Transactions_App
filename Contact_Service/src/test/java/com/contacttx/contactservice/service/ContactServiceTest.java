package com.contacttx.contactservice.service;

import com.contacttx.contactservice.client.UserServiceClient;
import com.contacttx.contactservice.client.dto.ResolveUserResponse;
import com.contacttx.contactservice.dto.request.CreateContactRequest;
import com.contacttx.contactservice.dto.request.UpdateContactRequest;
import com.contacttx.contactservice.dto.response.ContactResponse;
import com.contacttx.contactservice.dto.response.PaymentEligibilityReason;
import com.contacttx.contactservice.entity.Contact;
import com.contacttx.contactservice.exception.ContactNotFoundException;
import com.contacttx.contactservice.exception.DuplicateContactPhoneException;
import com.contacttx.contactservice.exception.LinkedUserInactiveException;
import com.contacttx.contactservice.exception.LinkedUserNotFoundException;
import com.contacttx.contactservice.exception.SelfLinkNotAllowedException;
import com.contacttx.contactservice.exception.UserServiceUnavailableException;
import com.contacttx.contactservice.mapper.ContactMapper;
import com.contacttx.contactservice.repository.ContactRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContactServiceTest {

    private static final Long OWNER_USER_ID = 101L;
    private static final Long CONTACT_ID = 25L;

    @Mock
    private ContactRepository contactRepository;

    @Mock
    private UserServiceClient userServiceClient;

    @Mock
    private EntityManager entityManager;

    private ContactService contactService;

    @BeforeEach
    void setUp() {
        contactService = new ContactService(
                contactRepository,
                userServiceClient,
                new ContactMapper(),
                entityManager
        );
    }

    @Test
    void createExternalContactDoesNotCallUserService() {
        CreateContactRequest request = new CreateContactRequest(
                "Sam Taylor",
                "9876543210",
                false
        );
        when(contactRepository.save(any(Contact.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ContactResponse response = contactService.createContact(OWNER_USER_ID, request);

        ArgumentCaptor<Contact> contactCaptor = ArgumentCaptor.forClass(Contact.class);
        verify(contactRepository).save(contactCaptor.capture());
        Contact savedContact = contactCaptor.getValue();

        assertEquals(OWNER_USER_ID, savedContact.getOwnerUserId());
        assertNull(savedContact.getLinkedUserId());
        assertEquals("Sam Taylor", savedContact.getContactName());
        assertEquals(9_876_543_210L, savedContact.getContactPhone());
        assertFalse(savedContact.isFavorite());
        assertFalse(response.isFavorite());
        assertFalse(response.isLinkedToRegisteredUser());
        verifyNoInteractions(userServiceClient);
        verify(entityManager).flush();
        verify(entityManager).refresh(savedContact);
    }

    @Test
    void createContactPersistsFavoriteWhenRequested() {
        CreateContactRequest request = new CreateContactRequest(
                "Sam Taylor",
                "9876543210",
                false,
                true
        );
        when(contactRepository.save(any(Contact.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ContactResponse response = contactService.createContact(OWNER_USER_ID, request);

        ArgumentCaptor<Contact> contactCaptor = ArgumentCaptor.forClass(Contact.class);
        verify(contactRepository).save(contactCaptor.capture());
        assertTrue(contactCaptor.getValue().isFavorite());
        assertTrue(response.isFavorite());
    }

    @Test
    void createContactRejectsDuplicatePhoneForTheSameOwner() {
        CreateContactRequest request = new CreateContactRequest(
                "Sam Taylor",
                "9876543210",
                false
        );
        when(contactRepository.existsByOwnerUserIdAndContactPhone(
                OWNER_USER_ID,
                9_876_543_210L))
                .thenReturn(true);

        assertThrows(
                DuplicateContactPhoneException.class,
                () -> contactService.createContact(OWNER_USER_ID, request)
        );

        verify(contactRepository).existsByOwnerUserIdAndContactPhone(
                OWNER_USER_ID,
                9_876_543_210L
        );
        verify(contactRepository, never()).save(any(Contact.class));
        verifyNoInteractions(userServiceClient, entityManager);
    }

    @Test
    void createContactScopesThePhoneCheckToTheOwner() {
        CreateContactRequest request = new CreateContactRequest(
                "Sam Taylor",
                "9876543210",
                false
        );
        when(contactRepository.existsByOwnerUserIdAndContactPhone(
                OWNER_USER_ID,
                9_876_543_210L))
                .thenReturn(false);
        when(contactRepository.save(any(Contact.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        contactService.createContact(OWNER_USER_ID, request);

        verify(contactRepository).existsByOwnerUserIdAndContactPhone(
                OWNER_USER_ID,
                9_876_543_210L
        );
        verify(contactRepository).save(any(Contact.class));
    }

    @Test
    void createLinkedContactStoresResolvedActiveUserId() {
        CreateContactRequest request = new CreateContactRequest(
                "Sam Taylor",
                "9876543210",
                true
        );
        when(userServiceClient.resolveUser("9876543210"))
                .thenReturn(Optional.of(new ResolveUserResponse(202L, "ACTIVE")));
        when(contactRepository.save(any(Contact.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ContactResponse response = contactService.createContact(OWNER_USER_ID, request);

        ArgumentCaptor<Contact> contactCaptor = ArgumentCaptor.forClass(Contact.class);
        verify(contactRepository).save(contactCaptor.capture());
        assertEquals(202L, contactCaptor.getValue().getLinkedUserId());
        assertTrue(response.isLinkedToRegisteredUser());
    }

    @Test
    void createLinkedContactRejectsUnknownPhone() {
        CreateContactRequest request = linkedCreateRequest();
        when(userServiceClient.resolveUser("9876543210"))
                .thenReturn(Optional.empty());

        assertThrows(
                LinkedUserNotFoundException.class,
                () -> contactService.createContact(OWNER_USER_ID, request)
        );

        verify(contactRepository, never()).save(any(Contact.class));
        verifyNoInteractions(entityManager);
    }

    @Test
    void createLinkedContactRejectsSelfLinkingBeforePersistence() {
        CreateContactRequest request = linkedCreateRequest();
        when(userServiceClient.resolveUser("9876543210"))
                .thenReturn(Optional.of(new ResolveUserResponse(OWNER_USER_ID, "ACTIVE")));

        assertThrows(
                SelfLinkNotAllowedException.class,
                () -> contactService.createContact(OWNER_USER_ID, request)
        );

        verify(contactRepository, never()).save(any(Contact.class));
        verifyNoInteractions(entityManager);
    }

    @Test
    void createLinkedContactRejectsInactiveUser() {
        CreateContactRequest request = linkedCreateRequest();
        when(userServiceClient.resolveUser("9876543210"))
                .thenReturn(Optional.of(new ResolveUserResponse(202L, "INACTIVE")));

        assertThrows(
                LinkedUserInactiveException.class,
                () -> contactService.createContact(OWNER_USER_ID, request)
        );

        verify(contactRepository, never()).save(any(Contact.class));
        verifyNoInteractions(entityManager);
    }

    @Test
    void createLinkedContactPropagatesUserServiceUnavailability() {
        CreateContactRequest request = linkedCreateRequest();
        UserServiceUnavailableException unavailableException =
                new UserServiceUnavailableException(new RuntimeException("connection failed"));
        when(userServiceClient.resolveUser("9876543210"))
                .thenThrow(unavailableException);

        RuntimeException thrown = assertThrows(
                UserServiceUnavailableException.class,
                () -> contactService.createContact(OWNER_USER_ID, request)
        );

        assertSame(unavailableException, thrown);
        verify(contactRepository, never()).save(any(Contact.class));
        verifyNoInteractions(entityManager);
    }

    @Test
    void listContactsUsesOnlyTheRequestedOwner() {
        Contact first = new Contact(OWNER_USER_ID, null, "Sam Taylor", 9_876_543_210L);
        Contact second = new Contact(OWNER_USER_ID, 202L, "Lee Parker", 9_123_456_789L);
        when(contactRepository.findAllByOwnerUserIdOrderByContactIdAsc(OWNER_USER_ID))
                .thenReturn(List.of(first, second));

        List<ContactResponse> responses = contactService.listContacts(OWNER_USER_ID);

        assertEquals(2, responses.size());
        assertEquals("Sam Taylor", responses.get(0).getContactName());
        assertFalse(responses.get(0).isLinkedToRegisteredUser());
        assertEquals("Lee Parker", responses.get(1).getContactName());
        assertTrue(responses.get(1).isLinkedToRegisteredUser());
        verify(contactRepository).findAllByOwnerUserIdOrderByContactIdAsc(OWNER_USER_ID);
    }

    @Test
    void getContactUsesContactIdAndOwnerIdTogether() {
        Contact contact = new Contact(OWNER_USER_ID, null, "Sam Taylor", 9_876_543_210L);
        when(contactRepository.findByContactIdAndOwnerUserId(CONTACT_ID, OWNER_USER_ID))
                .thenReturn(Optional.of(contact));

        ContactResponse response = contactService.getContact(OWNER_USER_ID, CONTACT_ID);

        assertEquals("Sam Taylor", response.getContactName());
        verify(contactRepository).findByContactIdAndOwnerUserId(CONTACT_ID, OWNER_USER_ID);
    }

    @Test
    void getContactReturnsNotFoundWhenOwnerScopedLookupIsEmpty() {
        when(contactRepository.findByContactIdAndOwnerUserId(CONTACT_ID, OWNER_USER_ID))
                .thenReturn(Optional.empty());

        assertThrows(
                ContactNotFoundException.class,
                () -> contactService.getContact(OWNER_USER_ID, CONTACT_ID)
        );
    }

    @Test
    void updateContactChangesOnlyEditableFieldsAndUsesDirtyChecking() {
        Contact contact = new Contact(OWNER_USER_ID, null, "Old Name", 9_000_000_000L);
        UpdateContactRequest request = new UpdateContactRequest(
                "New Name",
                "9123456789",
                true
        );
        when(contactRepository.findByContactIdAndOwnerUserId(CONTACT_ID, OWNER_USER_ID))
                .thenReturn(Optional.of(contact));
        when(userServiceClient.resolveUser("9123456789"))
                .thenReturn(Optional.of(new ResolveUserResponse(202L, "ACTIVE")));

        ContactResponse response = contactService.updateContact(
                OWNER_USER_ID,
                CONTACT_ID,
                request
        );

        assertEquals(OWNER_USER_ID, contact.getOwnerUserId());
        assertEquals(202L, contact.getLinkedUserId());
        assertEquals("New Name", contact.getContactName());
        assertEquals(9_123_456_789L, contact.getContactPhone());
        assertTrue(response.isLinkedToRegisteredUser());
        verify(contactRepository, never()).save(any(Contact.class));
        verify(entityManager).flush();
        verify(entityManager).refresh(contact);
    }

    @Test
    void updateContactRejectsAnotherOwnedContactWithTheSamePhone() {
        Contact contact = new Contact(OWNER_USER_ID, null, "Old Name", 9_000_000_000L);
        UpdateContactRequest request = new UpdateContactRequest(
                "New Name",
                "9123456789",
                false
        );
        when(contactRepository.findByContactIdAndOwnerUserId(CONTACT_ID, OWNER_USER_ID))
                .thenReturn(Optional.of(contact));
        when(contactRepository.existsByOwnerUserIdAndContactPhoneAndContactIdNot(
                OWNER_USER_ID,
                9_123_456_789L,
                CONTACT_ID))
                .thenReturn(true);

        assertThrows(
                DuplicateContactPhoneException.class,
                () -> contactService.updateContact(OWNER_USER_ID, CONTACT_ID, request)
        );

        assertEquals("Old Name", contact.getContactName());
        assertEquals(9_000_000_000L, contact.getContactPhone());
        verify(contactRepository).existsByOwnerUserIdAndContactPhoneAndContactIdNot(
                OWNER_USER_ID,
                9_123_456_789L,
                CONTACT_ID
        );
        verifyNoInteractions(userServiceClient, entityManager);
    }

    @Test
    void updateContactCanRemoveExistingLinkWithoutCallingUserService() {
        Contact contact = new Contact(OWNER_USER_ID, 202L, "Old Name", 9_000_000_000L);
        UpdateContactRequest request = new UpdateContactRequest(
                "External Contact",
                "9123456789",
                false
        );
        when(contactRepository.findByContactIdAndOwnerUserId(CONTACT_ID, OWNER_USER_ID))
                .thenReturn(Optional.of(contact));

        ContactResponse response = contactService.updateContact(
                OWNER_USER_ID,
                CONTACT_ID,
                request
        );

        assertNull(contact.getLinkedUserId());
        assertFalse(response.isLinkedToRegisteredUser());
        verifyNoInteractions(userServiceClient);
    }

    @Test
    void updateContactDoesNotModifyEntityWhenLinkedUserIsInactive() {
        Contact contact = new Contact(OWNER_USER_ID, null, "Original Name", 9_000_000_000L);
        UpdateContactRequest request = new UpdateContactRequest(
                "Changed Name",
                "9123456789",
                true
        );
        when(contactRepository.findByContactIdAndOwnerUserId(CONTACT_ID, OWNER_USER_ID))
                .thenReturn(Optional.of(contact));
        when(userServiceClient.resolveUser("9123456789"))
                .thenReturn(Optional.of(new ResolveUserResponse(202L, "INACTIVE")));

        assertThrows(
                LinkedUserInactiveException.class,
                () -> contactService.updateContact(OWNER_USER_ID, CONTACT_ID, request)
        );

        assertEquals("Original Name", contact.getContactName());
        assertEquals(9_000_000_000L, contact.getContactPhone());
        assertNull(contact.getLinkedUserId());
        verifyNoInteractions(entityManager);
    }

    @Test
    void deleteContactUsesContactIdAndOwnerIdTogether() {
        when(contactRepository.deleteByContactIdAndOwnerUserId(CONTACT_ID, OWNER_USER_ID))
                .thenReturn(1L);

        contactService.deleteContact(OWNER_USER_ID, CONTACT_ID);

        verify(contactRepository).deleteByContactIdAndOwnerUserId(CONTACT_ID, OWNER_USER_ID);
    }

    @Test
    void deleteContactReturnsNotFoundWhenNothingWasDeleted() {
        when(contactRepository.deleteByContactIdAndOwnerUserId(CONTACT_ID, OWNER_USER_ID))
                .thenReturn(0L);

        RuntimeException thrown = assertThrows(
                ContactNotFoundException.class,
                () -> contactService.deleteContact(OWNER_USER_ID, CONTACT_ID)
        );

        assertInstanceOf(ContactNotFoundException.class, thrown);
    }

    @Test
    void paymentIsEligibleForAnActiveLinkedContact() {
        Long receiverUserId = 202L;
        Contact contact = linkedContact(receiverUserId);
        when(contactRepository.findAllByOwnerUserIdAndLinkedUserIdOrderByContactIdAsc(
                OWNER_USER_ID, receiverUserId)).thenReturn(List.of(contact));
        when(userServiceClient.resolveUser("9876543210"))
                .thenReturn(Optional.of(new ResolveUserResponse(receiverUserId, "ACTIVE")));

        var decision = contactService.checkPaymentEligibility(
                OWNER_USER_ID, receiverUserId);

        assertTrue(decision.isAllowed());
        assertEquals(PaymentEligibilityReason.ELIGIBLE, decision.getReason());
    }

    @Test
    void paymentIsNotEligibleWithoutARegisteredLinkedContact() {
        Long receiverUserId = 202L;
        when(contactRepository.findAllByOwnerUserIdAndLinkedUserIdOrderByContactIdAsc(
                OWNER_USER_ID, receiverUserId)).thenReturn(List.of());

        var decision = contactService.checkPaymentEligibility(
                OWNER_USER_ID, receiverUserId);

        assertFalse(decision.isAllowed());
        assertEquals(PaymentEligibilityReason.CONTACT_NOT_FOUND, decision.getReason());
        verifyNoInteractions(userServiceClient);
    }

    @Test
    void paymentIsNotEligibleWhenTheLinkedUserIsInactive() {
        Long receiverUserId = 202L;
        Contact contact = linkedContact(receiverUserId);
        when(contactRepository.findAllByOwnerUserIdAndLinkedUserIdOrderByContactIdAsc(
                OWNER_USER_ID, receiverUserId)).thenReturn(List.of(contact));
        when(userServiceClient.resolveUser("9876543210"))
                .thenReturn(Optional.of(new ResolveUserResponse(receiverUserId, "INACTIVE")));

        var decision = contactService.checkPaymentEligibility(
                OWNER_USER_ID, receiverUserId);

        assertFalse(decision.isAllowed());
        assertEquals(PaymentEligibilityReason.RECEIVER_INACTIVE, decision.getReason());
    }

    @Test
    void paymentIsNotEligibleWhenTheSavedPhoneNoLongerResolves() {
        Long receiverUserId = 202L;
        Contact contact = linkedContact(receiverUserId);
        when(contactRepository.findAllByOwnerUserIdAndLinkedUserIdOrderByContactIdAsc(
                OWNER_USER_ID, receiverUserId)).thenReturn(List.of(contact));
        when(userServiceClient.resolveUser("9876543210"))
                .thenReturn(Optional.empty());

        var decision = contactService.checkPaymentEligibility(
                OWNER_USER_ID, receiverUserId);

        assertFalse(decision.isAllowed());
        assertEquals(PaymentEligibilityReason.CONTACT_PHONE_MISMATCH, decision.getReason());
    }

    @Test
    void paymentIsNotEligibleWhenTheSavedPhoneNowBelongsToAnotherUser() {
        Long receiverUserId = 202L;
        Contact contact = linkedContact(receiverUserId);
        when(contactRepository.findAllByOwnerUserIdAndLinkedUserIdOrderByContactIdAsc(
                OWNER_USER_ID, receiverUserId)).thenReturn(List.of(contact));
        when(userServiceClient.resolveUser("9876543210"))
                .thenReturn(Optional.of(new ResolveUserResponse(303L, "ACTIVE")));

        var decision = contactService.checkPaymentEligibility(
                OWNER_USER_ID, receiverUserId);

        assertFalse(decision.isAllowed());
        assertEquals(PaymentEligibilityReason.CONTACT_PHONE_MISMATCH, decision.getReason());
    }

    @Test
    void paymentIsNotEligibleForTheSameUser() {
        var decision = contactService.checkPaymentEligibility(
                OWNER_USER_ID, OWNER_USER_ID);

        assertFalse(decision.isAllowed());
        assertEquals(PaymentEligibilityReason.SELF_PAYMENT, decision.getReason());
        verifyNoInteractions(contactRepository, userServiceClient);
    }

    private CreateContactRequest linkedCreateRequest() {
        return new CreateContactRequest("Sam Taylor", "9876543210", true);
    }

    private Contact linkedContact(Long linkedUserId) {
        return new Contact(
                OWNER_USER_ID,
                linkedUserId,
                "Sam Taylor",
                9_876_543_210L
        );
    }
}
