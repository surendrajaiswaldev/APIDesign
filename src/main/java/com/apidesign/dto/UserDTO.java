package com.apidesign.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * Public-facing user representation. Never includes {@code passwordHash}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserDTO(
    Long id,
    String firstName,
    String lastName,
    String email,
    String phoneNumber,
    String address,
    String city,
    String state,
    String zipcode,
    Boolean isActive,
    String userType,
    Set<String> roles,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {}
