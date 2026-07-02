package com.learning.catalog.dto;

/**
 * Response for {@code POST /register}: the assigned instance id.
 */
public class RegisterResponse {

    private String id;

    public RegisterResponse() {
    }

    public RegisterResponse(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}
