package com.contacttx.contactservice.repository;

import com.contacttx.contactservice.entity.Contact;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;

public interface ContactRepository extends Repository<Contact, Long> {

    <S extends Contact> S save(S contact);

    List<Contact> findAllByOwnerUserIdOrderByContactIdAsc(Long ownerUserId);

    Optional<Contact> findByContactIdAndOwnerUserId(Long contactId, Long ownerUserId);

    List<Contact> findAllByOwnerUserIdAndLinkedUserIdOrderByContactIdAsc(
            Long ownerUserId,
            Long linkedUserId);

    boolean existsByOwnerUserIdAndContactPhone(Long ownerUserId, Long contactPhone);

    boolean existsByOwnerUserIdAndContactPhoneAndContactIdNot(
            Long ownerUserId,
            Long contactPhone,
            Long contactId);
            
    boolean existsByOwnerUserIdAndLinkedUserId(Long ownerUserId, Long linkedUserId);

    long deleteByContactIdAndOwnerUserId(Long contactId, Long ownerUserId);
}
