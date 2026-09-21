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
    @Pattern(regexp = "[0-9]{10}", message = "contactPhone must contain exactly 10 digits")
    @JsonDeserialize(using = StrictStringDeserializer.class)
    private String contactPhone;

    @NotNull(message = "linkToRegisteredUser is required")
    private Boolean linkToRegisteredUser;

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
}
