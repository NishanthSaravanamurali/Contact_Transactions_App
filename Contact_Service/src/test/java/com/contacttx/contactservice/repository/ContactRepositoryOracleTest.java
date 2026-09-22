package com.contacttx.contactservice.repository;

import com.contacttx.contactservice.entity.Contact;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "DB_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
class ContactRepositoryOracleTest {

    private static final Long OWNER_ID = 9_000_000_001L;
    private static final Long OTHER_OWNER_ID = 9_000_000_002L;

    @Autowired
    private ContactRepository contactRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void generatesIdentityAndMapsNumericValuesAndTimestamps() {
        Contact contact = new Contact(OWNER_ID, null, "Sam Taylor", 9_876_543_210L);

        Contact savedContact = contactRepository.save(contact);
        entityManager.flush();
        entityManager.refresh(savedContact);

        assertThat(savedContact.getContactId()).isNotNull();
        assertThat(savedContact.getContactId()).isInstanceOf(Long.class);
        assertThat(savedContact.getOwnerUserId()).isEqualTo(OWNER_ID);
        assertThat(savedContact.getContactPhone()).isEqualTo(9_876_543_210L);
        assertThat(savedContact.getCreatedAt()).isNotNull();
        assertThat(savedContact.getUpdatedAt()).isNotNull();
    }

    @Test
    void scopesLookupAndDeleteToTheOwner() {
        Contact contact = contactRepository.save(
                new Contact(OWNER_ID, null, "Owned Contact", 9_123_456_789L)
        );
        entityManager.flush();
        entityManager.clear();

        assertThat(contactRepository.findByContactIdAndOwnerUserId(contact.getContactId(), OWNER_ID))
                .isPresent();
        assertThat(contactRepository.findByContactIdAndOwnerUserId(contact.getContactId(), OTHER_OWNER_ID))
                .isEmpty();

        List<Contact> ownerContacts = contactRepository
                .findAllByOwnerUserIdOrderByContactIdAsc(OWNER_ID);
        assertThat(ownerContacts)
                .allMatch(ownerContact -> OWNER_ID.equals(ownerContact.getOwnerUserId()))
                .extracting(Contact::getContactId)
                .contains(contact.getContactId());

        assertThat(contactRepository.deleteByContactIdAndOwnerUserId(
                contact.getContactId(), OTHER_OWNER_ID
        )).isZero();
        assertThat(contactRepository.deleteByContactIdAndOwnerUserId(
                contact.getContactId(), OWNER_ID
        )).isOne();
    }

    @Test
    void findsRegisteredLinksByOwnerAndLinkedUser() {
        Long linkedUserId = 9_000_000_003L;
        contactRepository.save(new Contact(
                OWNER_ID,
                linkedUserId,
                "Registered Contact",
                9_123_456_789L
        ));
        entityManager.flush();

        assertThat(contactRepository.existsByOwnerUserIdAndLinkedUserId(OWNER_ID, linkedUserId))
                .isTrue();
        assertThat(contactRepository.existsByOwnerUserIdAndLinkedUserId(OTHER_OWNER_ID, linkedUserId))
                .isFalse();
    }

    @Test
    void rejectsSelfLinkingWithTheOracleCheckConstraint() {
        Contact selfLinkedContact = new Contact(
                OWNER_ID,
                OWNER_ID,
                "Self Link",
                9_234_567_890L
        );

        assertThatThrownBy(() -> {
            contactRepository.save(selfLinkedContact);
            entityManager.flush();
        })
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("CK_CONTACT_NOT_SELF");
    }

    @Test
    void letsTheOracleTriggerUpdateTheTimestamp() throws InterruptedException {
        Contact contact = contactRepository.save(
                new Contact(OWNER_ID, null, "Before Update", 9_345_678_901L)
        );
        entityManager.flush();
        entityManager.refresh(contact);

        LocalDateTime createdAt = contact.getCreatedAt();
        LocalDateTime firstUpdatedAt = contact.getUpdatedAt();

        Thread.sleep(10);
        contact.setContactName("After Update");
        entityManager.flush();
        entityManager.refresh(contact);

        assertThat(contact.getCreatedAt()).isEqualTo(createdAt);
        assertThat(contact.getUpdatedAt()).isAfter(firstUpdatedAt);
    }
}
