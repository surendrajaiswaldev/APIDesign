package com.apidesign.config;

import com.apidesign.entity.BaseEntity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * JPA Lifecycle Callback Handler for entity auditing.
 *
 * JPA Lifecycle Callbacks:
 * - @PrePersist: Called before INSERT
 * - @PostPersist: Called after INSERT
 * - @PreUpdate: Called before UPDATE
 * - @PostUpdate: Called after UPDATE
 * - @PreRemove: Called before DELETE
 * - @PostRemove: Called after DELETE
 * - @PostLoad: Called after entity is loaded from DB
 *
 * Benefits:
 * - Automatic timestamp management
 * - Centralized audit field handling
 * - Consistency across all entities extending BaseEntity
 * - No manual timestamp setting in service layer
 *
 * Alternative Approaches:
 * 1. Database triggers: Less portable, harder to test
 * 2. Manual setting in service: Error-prone, code duplication
 * 3. Hibernate listeners: More powerful but more complex
 * 4. JPA callbacks (used here): Simple, portable, centralized
 *
 * Limitations:
 * - Only called for single entity operations (not bulk updates)
 * - Requires entity to be managed by Hibernate
 * - For bulk updates, use @Query or database-level approaches
 *
 * Bulk Update Workaround:
 * For bulk updates, either:
 * 1. Load entities, update in memory, save (uses callbacks)
 * 2. Use @Query with JPQL UPDATE and manually set updatedAt
 */
@Slf4j
@Component
public class AuditingEntityListener {

    /**
     * Set creation timestamp before entity insertion.
     *
     * Called automatically by Hibernate for any entity extending BaseEntity
     * when using EntityManager.persist() or repository.save() on new entity.
     *
     * Important: This is called on managed entities in persistence context.
     * Transient entities won't trigger this until they're persisted.
     *
     * @param entity the entity being persisted
     */
    @PrePersist
    public void onPrePersist(Object entity) {
        if (entity instanceof BaseEntity) {
            BaseEntity baseEntity = (BaseEntity) entity;
            LocalDateTime now = LocalDateTime.now();
            
            baseEntity.setCreatedAt(now);
            baseEntity.setUpdatedAt(now);
            
            log.debug("Audit: Setting creation/update timestamp for new entity: {}",
                    entity.getClass().getSimpleName());
        }
    }

    /**
     * Update modification timestamp before entity update.
     *
     * Called automatically by Hibernate before any UPDATE statement.
     * This runs for:
     * - Explicit repository.save() on existing entity
     * - Changes to managed entity fields in transaction
     * - Service layer updates via repository
     *
     * Note: Bulk updates via @Query or native SQL won't trigger this.
     * For bulk updates, handle timestamps manually in queries.
     *
     * @param entity the entity being updated
     */
    @PreUpdate
    public void onPreUpdate(Object entity) {
        if (entity instanceof BaseEntity) {
            BaseEntity baseEntity = (BaseEntity) entity;
            baseEntity.setUpdatedAt(LocalDateTime.now());
            
            log.debug("Audit: Updating timestamp for modified entity: {}",
                    entity.getClass().getSimpleName());
        }
    }
}
