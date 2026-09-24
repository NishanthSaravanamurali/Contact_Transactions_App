package com.contacttx.contactservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "CONTACT", schema = "SYSTEM")
public class Contact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "CONTACT_ID", nullable = false)
    private Long contactId;

    @Column(name = "OWNER_USER_ID", nullable = false)
    private Long ownerUserId;

    @Column(name = "LINKED_USER_ID")
    private Long linkedUserId;

    @Column(name = "CONTACT_NAME", nullable = false, length = 20)
    private String contactName;

    @Column(name = "CONTACT_PHONE", nullable = false, precision = 10, scale = 0)
    private Long contactPhone;

    @Column(name = "IS_FAVORITE", nullable = false)
    private Boolean favorite = false;

    @Column(name = "CREATED_AT", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    protected Contact() {
    }

    public Contact(Long ownerUserId, Long linkedUserId, String contactName, Long contactPhone) {
        this.ownerUserId = ownerUserId;
        this.linkedUserId = linkedUserId;
        this.contactName = contactName;
        this.contactPhone = contactPhone;
    }

    public Long getContactId() {
        return contactId;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public Long getLinkedUserId() {
        return linkedUserId;
    }

    public void setLinkedUserId(Long linkedUserId) {
        this.linkedUserId = linkedUserId;
    }

    public String getContactName() {
        return contactName;
    }

    public void setContactName(String contactName) {
        this.contactName = contactName;
    }

    public Long getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(Long contactPhone) {
        this.contactPhone = contactPhone;
    }

    public boolean isFavorite() {
        return Boolean.TRUE.equals(favorite);
    }

    public void setFavorite(Boolean favorite) {
        this.favorite = favorite;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
