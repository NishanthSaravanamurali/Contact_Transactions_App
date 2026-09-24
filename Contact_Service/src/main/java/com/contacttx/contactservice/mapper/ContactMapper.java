package com.contacttx.contactservice.mapper;

import com.contacttx.contactservice.dto.request.CreateContactRequest;
import com.contacttx.contactservice.dto.request.UpdateContactRequest;
import com.contacttx.contactservice.dto.response.ContactResponse;
import com.contacttx.contactservice.entity.Contact;
import org.springframework.stereotype.Component;

@Component
public class ContactMapper {

    private static final String TEN_DIGIT_PHONE_PATTERN = "[6-9][0-9]{9}";

    public Contact toEntity(
            CreateContactRequest request,
            Long ownerUserId,
            Long linkedUserId) {

        return new Contact(
                ownerUserId,
                linkedUserId,
                request.getContactName(),
                toPhoneNumber(request.getContactPhone())
        );
    }

    public void updateEntity(
            Contact contact,
            UpdateContactRequest request,
            Long linkedUserId) {

        contact.setContactName(request.getContactName());
        contact.setContactPhone(toPhoneNumber(request.getContactPhone()));
        contact.setLinkedUserId(linkedUserId);
        if (request.getFavorite() != null) {
            contact.setFavorite(request.getFavorite());
        }
    }

    public ContactResponse toResponse(Contact contact) {
        ContactResponse response = new ContactResponse(
                contact.getContactId(),
                contact.getContactName(),
                String.valueOf(contact.getContactPhone()),
                contact.getLinkedUserId(),
                contact.getLinkedUserId() != null,
                contact.isFavorite(),
                contact.getCreatedAt(),
                contact.getUpdatedAt()
        );
        // The authenticated owner's frontend needs this ID for makePayment.
        response.setLinkedUserId(contact.getLinkedUserId());
        return response;
    }

    private Long toPhoneNumber(String contactPhone) {
        if (contactPhone == null || !contactPhone.matches(TEN_DIGIT_PHONE_PATTERN)) {
            throw new IllegalArgumentException(
                    "contactPhone must contain exactly 10 digits before conversion"
            );
        }

        return Long.valueOf(contactPhone);
    }
}
