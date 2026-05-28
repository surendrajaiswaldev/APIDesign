package com.apidesign.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * User entity representing customers in the order management system.
 *
 * Relationship: User -> Orders (One-to-Many)
 * A user can have multiple orders. The relationship is managed by Order entity
 * with LAZY loading to avoid N+1 query problems.
 *
 * Why not use @Data: Avoid automatic generation of equals/hashCode on entities
 * as it can cause issues with lazy-loaded collections.
 */
@Entity
@Table(name = "USERS", uniqueConstraints = {
    @UniqueConstraint(columnNames = "EMAIL", name = "UK_USER_EMAIL")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class User extends BaseEntity {
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "First name is required")
    @Column(name = "FIRST_NAME", length = 50, nullable = false)
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Column(name = "LAST_NAME", length = 50, nullable = false)
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email should be valid")
    @Column(name = "EMAIL", length = 100, nullable = false, unique = true)
    private String email;

    @NotBlank(message = "Phone number is required")
    @Column(name = "PHONE_NUMBER", length = 20, nullable = false)
    private String phoneNumber;

    /**
     * Street address of the user.
     * Kept simple for this demo; can be extended to separate Address entity.
     */
    @Column(name = "ADDRESS", length = 255)
    private String address;

    @Column(name = "CITY", length = 50)
    private String city;

    @Column(name = "STATE", length = 50)
    private String state;

    @Column(name = "ZIPCODE", length = 20)
    private String zipcode;

    /**
     * Account status flag.
     * Why not enum: Simpler for this basic implementation, can be upgraded
     * to @Enumerated(EnumType.STRING) for type safety if needed.
     */
    @Column(name = "IS_ACTIVE", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    /**
     * User's role/type in the system.
     * Can be extended with role-based access control.
     */
    @Column(name = "USER_TYPE", length = 20)
    @Builder.Default
    private String userType = "CUSTOMER";

    /**
     * Pre-persist lifecycle callback to set default values and timestamps.
     */
    @PrePersist
    protected void onCreate() {
        super.onCreate();
        // Set defaults if not already set
        if (this.isActive == null) {
            this.isActive = true;
        }
        if (this.userType == null) {
            this.userType = "CUSTOMER";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        super.onUpdate();
    }
}

