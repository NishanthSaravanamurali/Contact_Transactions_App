package com.contacttx.contactservice.client.dto;

public class ResolveUserRequest {

    private String mobileNo;

    public ResolveUserRequest() {
    }

    public ResolveUserRequest(String mobileNo) {
        this.mobileNo = mobileNo;
    }

    public String getMobileNo() {
        return mobileNo;
    }

    public void setMobileNo(String mobileNo) {
        this.mobileNo = mobileNo;
    }
}
