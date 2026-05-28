package com.apidesign.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * Partial-update payload. All fields optional. Activation/deactivation has its own endpoint.
 */
public record UpdateUserRequest(
    @Size(min = 2, max = 50, message = "First name must be between 2 and 50 characters")
        String firstName,
    @Size(min = 2, max = 50, message = "Last name must be between 2 and 50 characters")
        String lastName,
    @Email(message = "Email should be valid") String email,
    @Size(min = 10, max = 20, message = "Phone number must be between 10 and 20 characters")
        String phoneNumber,
    String address,
    String city,
    String state,
    String zipcode) {}
