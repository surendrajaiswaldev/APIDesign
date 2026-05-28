package com.apidesign.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * DTO for User entity - used in API responses.
 * Follows DTO best practices:
 * - Only includes fields needed for client consumption
 * - Excludes sensitive information (passwords, audit fields)
 * - Uses DTOs instead of exposing entities directly (separation of concerns)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserDTO {
    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private String phoneNumber;
    private String address;
    private String city;
    private String state;
    private String zipcode;
    private Boolean isActive;
    private String userType;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

