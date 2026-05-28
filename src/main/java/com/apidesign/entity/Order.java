package com.apidesign.entity;

import com.apidesign.constants.OrderStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Order entity representing customer orders in the system.
 *
 * Key Relationships:
 * - User relationship: LAZY loading (Many-to-One). User lookup only when needed.
 * - OrderItems relationship: LAZY loading by default, but fetch EAGER when needed
 *   using join fetch or entity graphs to avoid N+1 problems.
 *
 * Why LAZY for User: Prevents loading user details for every order query.
 * Use repository methods with @Query and join fetch when user data is needed.
 *
 * Why LAZY for OrderItems: Prevents automatic loading of all line items.
 * For API responses, explicitly load items using @EntityGraph or join fetch.
 *
 * IMPORTANT - N+1 Problem Example:
 * BAD:  orders = repository.findAll(); // Then iterate and access user/items
 *       CAUSES: 1 query for orders + N queries for each user + N queries for items
 * GOOD: orders = repository.findOrdersWithDetails(); // Uses join fetch
 *       CAUSES: 1 query with joins
 *
 * Solution implemented in repository layer using @Query with JOIN FETCH.
 */
@Entity
@Table(name = "ORDERS", indexes = {
    @Index(name = "IDX_ORDER_USER", columnList = "USER_ID"),
    @Index(name = "IDX_ORDER_STATUS", columnList = "ORDER_STATUS"),
    @Index(name = "IDX_ORDER_CREATED_AT", columnList = "CREATED_AT")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Order extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /**
     * Order number - unique, customer-facing identifier.
     * Different from ID for better UX and security (hiding internal IDs).
     */
    @Column(name = "ORDER_NUMBER", length = 50, nullable = false, unique = true)
    private String orderNumber;

    /**
     * User who placed the order.
     * LAZY loading: User is only fetched when explicitly accessed.
     * For API responses, use @EntityGraph or join fetch to load eagerly.
     * ForeignKey name helps maintain referential integrity.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "USER_ID", nullable = false, foreignKey = @ForeignKey(name = "FK_ORDER_USER"))
    private User user;

    /**
     * Current status of the order.
     * Values: PENDING, CONFIRMED, SHIPPED, DELIVERED, CANCELLED
     * Status transitions are validated in service layer using OrderStatus.isValidTransition()
     */
    @Column(name = "ORDER_STATUS", length = 20, nullable = false)
    @Builder.Default
    private String orderStatus = OrderStatus.PENDING;

    /**
     * Line items in this order.
     * LAZY loading: Items are loaded only when explicitly accessed.
     * CASCADE.PERSIST: When order is saved, items are also saved automatically.
     * CASCADE.REMOVE: Currently NOT set - implement soft delete for audit trail.
     * For queries needing items, use repository method with JOIN FETCH.
     *
     * Example fix for N+1:
     * @Query("SELECT o FROM Order o JOIN FETCH o.orderItems WHERE o.id = ?1")
     */
    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY, cascade = CascadeType.PERSIST, orphanRemoval = false)
    @Builder.Default
    private List<OrderItem> orderItems = new ArrayList<>();

    /**
     * Total order amount (sum of all items * quantity * price).
     * Denormalized for query performance and reporting.
     * Recalculated during order processing.
     */
    @Positive(message = "Order total must be positive")
    @Column(name = "TOTAL_AMOUNT", precision = 12, scale = 2, nullable = false)
    private BigDecimal totalAmount;

    /**
     * Shipping address (can differ from user's registered address).
     */
    @Column(name = "SHIPPING_ADDRESS", length = 255)
    private String shippingAddress;

    /**
     * Special notes or instructions for this order.
     */
    @Column(name = "NOTES", length = 500)
    private String notes;

    /**
     * Estimated delivery date.
     */
    @Column(name = "ESTIMATED_DELIVERY")
    private java.time.LocalDateTime estimatedDelivery;

    @PrePersist
    protected void onCreate() {
        super.onCreate();
        if (this.orderStatus == null) {
            this.orderStatus = OrderStatus.PENDING;
        }
        if (this.totalAmount == null) {
            this.totalAmount = BigDecimal.ZERO;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        super.onUpdate();
    }

    /**
     * Helper method to add order item to this order.
     * Maintains bidirectional relationship consistency.
     *
     * @param item the order item to add
     */
    public void addOrderItem(OrderItem item) {
        item.setOrder(this);
        this.orderItems.add(item);
    }

    /**
     * Helper method to calculate total amount from order items.
     * Should be called before persistence.
     */
    public void calculateTotalAmount() {
        this.totalAmount = this.orderItems.stream()
            .map(item -> item.getUnitPrice().multiply(new BigDecimal(item.getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}


