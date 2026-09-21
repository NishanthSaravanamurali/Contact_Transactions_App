package com.contacttx.contactservice.service;

import com.contacttx.contactservice.client.UserServiceClient;
import com.contacttx.contactservice.client.dto.ResolveUserResponse;
import com.contacttx.contactservice.client.dto.UserStatusResponse;
import com.contacttx.contactservice.dto.request.CreateContactRequest;
import com.contacttx.contactservice.dto.request.UpdateContactRequest;
import com.contacttx.contactservice.dto.response.ContactResponse;
import com.contacttx.contactservice.entity.Contact;
import com.contacttx.contactservice.exception.ContactNotFoundException;
import com.contacttx.contactservice.exception.LinkedUserInactiveException;
import com.contacttx.contactservice.exception.LinkedUserNotFoundException;
import com.contacttx.contactservice.exception.SelfLinkNotAllowedException;
import com.contacttx.contactservice.mapper.ContactMapper;
import com.contacttx.contactservice.repository.ContactRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ContactService {

    private static final String ACTIVE_STATUS = "ACTIVE";

    private final ContactRepository contactRepository;
    private final UserServiceClient userServiceClient;
    private final ContactMapper contactMapper;
    private final EntityManager entityManager;

    public ContactService(
            ContactRepository contactRepository,
            UserServiceClient userServiceClient,
            ContactMapper contactMapper,
            EntityManager entityManager) {
        this.contactRepository = contactRepository;
        this.userServiceClient = userServiceClient;
        this.contactMapper = contactMapper;
        this.entityManager = entityManager;
    }

    @Transactional
    public ContactResponse createContact(
            Long ownerUserId,
            CreateContactRequest request) {

        Long linkedUserId = resolveLinkedUserId(
                ownerUserId,
                request.getContactPhone(),
                request.getLinkToRegisteredUser()
        );

        Contact contact = contactMapper.toEntity(request, ownerUserId, linkedUserId);
        Contact savedContact = contactRepository.save(contact);

        refreshOracleManagedValues(savedContact);
        return contactMapper.toResponse(savedContact);
    }

    @Transactional(readOnly = true)
    public List<ContactResponse> listContacts(Long ownerUserId) {
        return contactRepository
                .findAllByOwnerUserIdOrderByContactIdAsc(ownerUserId)
                .stream()
                .map(contactMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ContactResponse getContact(Long ownerUserId, Long contactId) {
        return contactMapper.toResponse(findOwnedContact(ownerUserId, contactId));
    }

    @Transactional
    public ContactResponse updateContact(
            Long ownerUserId,
            Long contactId,
            UpdateContactRequest request) {

        Contact contact = findOwnedContact(ownerUserId, contactId);
        Long linkedUserId = resolveLinkedUserId(
                ownerUserId,
                request.getContactPhone(),
                request.getLinkToRegisteredUser()
        );

        contactMapper.updateEntity(contact, request, linkedUserId);
        refreshOracleManagedValues(contact);

        return contactMapper.toResponse(contact);
    }

    @Transactional
    public void deleteContact(Long ownerUserId, Long contactId) {
        long deletedCount = contactRepository.deleteByContactIdAndOwnerUserId(
                contactId,
                ownerUserId
        );

        if (deletedCount == 0) {
            throw new ContactNotFoundException();
        }
    }

    @Transactional(readOnly = true)
    public boolean isPaymentEligible(Long senderUserId, Long receiverUserId) {
        if (senderUserId.equals(receiverUserId)
                || !contactRepository.existsByOwnerUserIdAndLinkedUserId(
                        senderUserId,
                        receiverUserId)) {
            return false;
        }

        return userServiceClient.getUserStatus(receiverUserId)
                .filter(response -> receiverUserId.equals(response.getUserId()))
                .map(UserStatusResponse::getStatus)
                .map(ACTIVE_STATUS::equals)
                .orElse(false);
    }

    private Contact findOwnedContact(Long ownerUserId, Long contactId) {
        return contactRepository
                .findByContactIdAndOwnerUserId(contactId, ownerUserId)
                .orElseThrow(ContactNotFoundException::new);
    }

    private Long resolveLinkedUserId(
            Long ownerUserId,
            String contactPhone,
            Boolean linkToRegisteredUser) {

        if (!Boolean.TRUE.equals(linkToRegisteredUser)) {
            return null;
        }

        ResolveUserResponse resolvedUser = userServiceClient
                .resolveUser(contactPhone)
                .orElseThrow(LinkedUserNotFoundException::new);

        if (resolvedUser.getUserId().equals(ownerUserId)) {
            throw new SelfLinkNotAllowedException();
        }

        if (!ACTIVE_STATUS.equals(resolvedUser.getStatus())) {
            throw new LinkedUserInactiveException();
        }

        return resolvedUser.getUserId();
    }

    private void refreshOracleManagedValues(Contact contact) {
        entityManager.flush();
        entityManager.refresh(contact);
    }
}
