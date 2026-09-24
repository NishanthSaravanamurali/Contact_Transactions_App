package com.contacttx.contactservice.dto.request;

import com.contacttx.contactservice.validation.StrictStringDeserializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.annotation.JsonDeserialize;

public class CreateContactRequest {

    @NotBlank(message = "contactName is required")
    @Size(min = 2, max = 20, message = "contactName must be between 2 and 20 characters")
    private String contactName;

    @NotBlank(message = "contactPhone is required")
    @Pattern(
            regexp = "[6-9][0-9]{9}",
            message = "contactPhone must be a 10-digit mobile number beginning with 6, 7, 8, or 9")
    @JsonDeserialize(using = StrictStringDeserializer.class)
    private String contactPhone;

    @NotNull(message = "linkToRegisteredUser is required")
    private Boolean linkToRegisteredUser;

    private Boolean favorite;

    public CreateContactRequest() {
    }

    public CreateContactRequest(
            String contactName,
            String contactPhone,
            Boolean linkToRegisteredUser) {
        setContactName(contactName);
        this.contactPhone = contactPhone;
        this.linkToRegisteredUser = linkToRegisteredUser;
    }

    public CreateContactRequest(
            String contactName,
            String contactPhone,
            Boolean linkToRegisteredUser,
            Boolean favorite) {
        this(contactName, contactPhone, linkToRegisteredUser);
        this.favorite = favorite;
    }

    public String getContactName() {
        return contactName;
    }

    public void setContactName(String contactName) {
        this.contactName = contactName == null ? null : contactName.trim();
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
    }

    public Boolean getLinkToRegisteredUser() {
        return linkToRegisteredUser;
    }

    public void setLinkToRegisteredUser(Boolean linkToRegisteredUser) {
        this.linkToRegisteredUser = linkToRegisteredUser;
    }

    public Boolean getFavorite() {
        return favorite;
    }

    public void setFavorite(Boolean favorite) {
        this.favorite = favorite;
    }
}
