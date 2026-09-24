package com.contacttx.contactservice.mapper;

import com.contacttx.contactservice.dto.request.CreateContactRequest;
import com.contacttx.contactservice.dto.request.UpdateContactRequest;
import com.contacttx.contactservice.dto.response.ContactResponse;
import com.contacttx.contactservice.entity.Contact;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContactMapperTest {

    private final ContactMapper contactMapper = new ContactMapper();

    @Test
    void mapsCreateRequestUsingOnlyTrustedIdentifiers() {
        CreateContactRequest request = new CreateContactRequest(
                "Sam Taylor",
                "9876543210",
                true
        );

        Contact contact = contactMapper.toEntity(request, 10L, 20L);

        assertThat(contact.getOwnerUserId()).isEqualTo(10L);
        assertThat(contact.getLinkedUserId()).isEqualTo(20L);
        assertThat(contact.getContactName()).isEqualTo("Sam Taylor");
        assertThat(contact.getContactPhone()).isEqualTo(9_876_543_210L);
        assertThat(contact.getContactId()).isNull();
        assertThat(contact.getCreatedAt()).isNull();
        assertThat(contact.getUpdatedAt()).isNull();
    }

    @Test
    void updatesOnlyEditableContactFields() {
        Contact contact = new Contact(10L, null, "Before", 9_111_111_111L);
        UpdateContactRequest request = new UpdateContactRequest(
                "After",
                "9222222222",
                true
        );

        contactMapper.updateEntity(contact, request, 20L);

        assertThat(contact.getOwnerUserId()).isEqualTo(10L);
        assertThat(contact.getLinkedUserId()).isEqualTo(20L);
        assertThat(contact.getContactName()).isEqualTo("After");
        assertThat(contact.getContactPhone()).isEqualTo(9_222_222_222L);
    }

    @Test
    void mapsResponseWithLinkedRecipientForPayments() {
        Contact contact = new Contact(10L, 20L, "Sam Taylor", 9_876_543_210L);

        ContactResponse response = contactMapper.toResponse(contact);

        assertThat(response.getContactName()).isEqualTo("Sam Taylor");
        assertThat(response.getContactPhone()).isEqualTo("9876543210");
        assertThat(response.isLinkedToRegisteredUser()).isTrue();
        assertThat(response.getLinkedUserId()).isEqualTo(20L);
    }

    @Test
    void demonstratesThatNumericStorageCannotPreserveALeadingZero() {
        CreateContactRequest request = new CreateContactRequest(
                "Sam Taylor",
                "0123456789",
                false
        );

        Contact contact = contactMapper.toEntity(request, 10L, null);
        ContactResponse response = contactMapper.toResponse(contact);

        assertThat(contact.getContactPhone()).isEqualTo(123_456_789L);
        assertThat(response.getContactPhone()).isEqualTo("123456789");
        assertThat(response.getLinkedUserId()).isNull();
    }

    @Test
    void refusesToConvertAnInvalidPhone() {
        CreateContactRequest request = new CreateContactRequest(
                "Sam Taylor",
                "12345",
                false
        );

        assertThatThrownBy(() -> contactMapper.toEntity(request, 10L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("contactPhone must contain exactly 10 digits before conversion");
    }
}
