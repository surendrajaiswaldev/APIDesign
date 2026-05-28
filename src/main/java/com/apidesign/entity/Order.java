package com.apidesign.entity;

import com.apidesign.constants.OrderStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Customer order. Aggregate root that owns its {@link OrderItem}s.
 *
 * Concurrency: carries a {@link Version} so concurrent status/total updates fail loudly.
 * Items use {@code orphanRemoval = true} so removing one from {@link #orderItems}
 * cascades to a DELETE.
 */
@Entity
@Table(
    name = "ORDERS",
    indexes = {
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

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "order_seq_gen")
    @SequenceGenerator(name = "order_seq_gen", sequenceName = "ORDER_SEQ", allocationSize = 50)
    private Long id;

    @Version
    @Column(name = "VERSION")
    private Long version;

    @Column(name = "ORDER_NUMBER", length = 50, nullable = false, unique = true)
    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "USER_ID", nullable = false, foreignKey = @ForeignKey(name = "FK_ORDER_USER"))
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "ORDER_STATUS", length = 20, nullable = false)
    @Builder.Default
    private OrderStatus orderStatus = OrderStatus.PENDING;

    @OneToMany(
        mappedBy = "order",
        fetch = FetchType.LAZY,
        cascade = CascadeType.ALL,
        orphanRemoval = true)
    @Builder.Default
    private List<OrderItem> orderItems = new ArrayList<>();

    @Positive(message = "Order total must be positive")
    @Column(name = "TOTAL_AMOUNT", precision = 12, scale = 2, nullable = false)
    private BigDecimal totalAmount;

    @Column(name = "SHIPPING_ADDRESS", length = 255)
    private String shippingAddress;

    @Column(name = "NOTES", length = 500)
    private String notes;

    @Column(name = "ESTIMATED_DELIVERY")
    private LocalDateTime estimatedDelivery;

    /** Bidirectional add helper — keeps both sides of the relationship in sync. */
    public void addOrderItem(OrderItem item) {
        item.setOrder(this);
        this.orderItems.add(item);
    }

    /** Bidirectional remove helper. Combined with orphanRemoval, this deletes the row. */
    public void removeOrderItem(OrderItem item) {
        this.orderItems.remove(item);
        item.setOrder(null);
    }

    public void calculateTotalAmount() {
        this.totalAmount = this.orderItems.stream()
            .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
