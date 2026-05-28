package com.apidesign.entity;

import com.apidesign.config.AuditingEntityListener;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.SequenceGenerator;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Base entity class providing common auditing fields and ID generation.
 *
 * Why MappedSuperclass: Allows sharing common fields and methods across entities
 * without creating a separate table. Each child entity gets its own table with
 * these fields.
 *
 * Why Oracle Sequences: Using sequences instead of auto-increment provides:
 * - Better performance in distributed systems
 * - Pre-allocation of IDs before DB insert
 * - Control over sequence behavior
 *
 * @EntityListeners(AuditingEntityListener.class):
 * - Registers JPA lifecycle callbacks
 * - Automatically sets createdAt/updatedAt timestamps
 * - Applies to all entities extending BaseEntity
 *
 * NOTE: @Getter/@Setter must be on child classes for Lombok @Builder to work
 * with inherited fields. Child classes should also have @NoArgsConstructor and
 * @AllArgsConstructor for proper builder support.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "id_generator")
    @SequenceGenerator(name = "id_generator", sequenceName = "ID_SEQ", allocationSize = 1)
    protected Long id;

    /**
     * Timestamp when entity was created.
     * Automatically set by JPA lifecycle callback or database trigger.
     */
    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    protected LocalDateTime createdAt;

    /**
     * Timestamp when entity was last updated.
     * Automatically updated on every modification.
     */
    @Column(name = "UPDATED_AT")
    protected LocalDateTime updatedAt;

    /**
     * User who created the entity.
     * Can be set by interceptor or service layer.
     */
    @Column(name = "CREATED_BY", length = 50)
    protected String createdBy;

    /**
     * User who last updated the entity.
     * Can be set by interceptor or service layer.
     */
    @Column(name = "UPDATED_BY", length = 50)
    protected String updatedBy;

    /**
     * JPA lifecycle callback - sets createdAt before persist.
     * Alternative: Use @CreationTimestamp from Hibernate if available.
     */
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * JPA lifecycle callback - updates updatedAt before update.
     * Alternative: Use @UpdateTimestamp from Hibernate if available.
     */
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}

