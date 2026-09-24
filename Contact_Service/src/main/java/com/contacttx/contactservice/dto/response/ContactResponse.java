package com.contacttx.contactservice.dto.response;

import java.time.LocalDateTime;

public class ContactResponse {

    private Long contactId;
    private String contactName;
    private String contactPhone;
    private Long linkedUserId;
    private boolean linkedToRegisteredUser;
    private boolean favorite;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public ContactResponse() {
    }

    public ContactResponse(
            Long contactId,
            String contactName,
            String contactPhone,
            Long linkedUserId,
            boolean linkedToRegisteredUser,
            boolean favorite,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        this.contactId = contactId;
        this.contactName = contactName;
        this.contactPhone = contactPhone;
        this.linkedUserId = linkedUserId;
        this.linkedToRegisteredUser = linkedToRegisteredUser;
        this.favorite = favorite;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getContactId() {
        return contactId;
    }

    public void setContactId(Long contactId) {
        this.contactId = contactId;
    }

    public String getContactName() {
        return contactName;
    }

    public void setContactName(String contactName) {
        this.contactName = contactName;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
    }

    public Long getLinkedUserId() {
        return linkedUserId;
    }

    public void setLinkedUserId(Long linkedUserId) {
        this.linkedUserId = linkedUserId;
    }

    public boolean isLinkedToRegisteredUser() {
        return linkedToRegisteredUser;
    }

    public void setLinkedToRegisteredUser(boolean linkedToRegisteredUser) {
        this.linkedToRegisteredUser = linkedToRegisteredUser;
    }

    public boolean isFavorite() {
        return favorite;
    }

    public void setFavorite(boolean favorite) {
        this.favorite = favorite;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
