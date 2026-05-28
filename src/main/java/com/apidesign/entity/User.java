package com.apidesign.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.HashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Application user (customer / admin). Authenticated via email + bcrypt password hash.
 *
 * Roles are persisted as a string set ({@code USER}, {@code ADMIN}) and exposed to Spring
 * Security as authorities prefixed with {@code ROLE_}.
 */
@Entity
@Table(
    name = "USERS",
    uniqueConstraints = {@UniqueConstraint(columnNames = "EMAIL", name = "UK_USER_EMAIL")})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class User extends BaseEntity {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "user_seq_gen")
    @SequenceGenerator(name = "user_seq_gen", sequenceName = "USER_SEQ", allocationSize = 50)
    private Long id;

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

    /** BCrypt-encoded password. Never returned in DTOs. */
    @Column(name = "PASSWORD_HASH", length = 100, nullable = false)
    private String passwordHash;

    @NotBlank(message = "Phone number is required")
    @Column(name = "PHONE_NUMBER", length = 20, nullable = false)
    private String phoneNumber;

    @Column(name = "ADDRESS", length = 255)
    private String address;

    @Column(name = "CITY", length = 50)
    private String city;

    @Column(name = "STATE", length = 50)
    private String state;

    @Column(name = "ZIPCODE", length = 20)
    private String zipcode;

    @Column(name = "IS_ACTIVE", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "USER_TYPE", length = 20)
    @Builder.Default
    private String userType = "CUSTOMER";

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "USER_ROLES",
        joinColumns = @JoinColumn(name = "USER_ID", foreignKey = @ForeignKey(name = "FK_USER_ROLES_USER")))
    @Column(name = "ROLE", length = 30, nullable = false)
    @Builder.Default
    private Set<String> roles = new HashSet<>();
}
