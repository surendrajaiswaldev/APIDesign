package com.apidesign.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

/**
 * OrderItem entity representing a line item in an order.
 *
 * Relationship model:
 * - Order (Many-to-One): The owning side. FK to Order.
 * - Product (Many-to-One): Reference to product. Can be LAZY as product details
 *   may not always be needed in API responses.
 *
 * Why separate OrderItem entity:
 * 1. Supports multiple quantities per product in single order
 * 2. Stores historical price (unit price when order was placed)
 * 3. Maintains order audit trail even if product is deleted
 * 4. Enables complex queries (e.g., most ordered products, revenue by product)
 *
 * Why unitPrice stored here (not fetched from Product):
 * CRITICAL: Price can change over time. OrderItem.unitPrice captures the price
 * at time of order, ensuring accurate historical records and revenue calculations.
 * This is why we NEVER use product.price directly - always use orderItem.unitPrice!
 */
@Entity
@Table(name = "ORDER_ITEMS", indexes = {
    @Index(name = "IDX_ORDER_ITEM_ORDER", columnList = "ORDER_ID"),
    @Index(name = "IDX_ORDER_ITEM_PRODUCT", columnList = "PRODUCT_ID")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class OrderItem extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /**
     * Reference to the parent order.
     * ManyToOne relationship: Multiple items can belong to one order.
     * ForeignKey constraint helps maintain referential integrity.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ORDER_ID", nullable = false, foreignKey = @ForeignKey(name = "FK_ORDER_ITEM_ORDER"))
    private Order order;

    /**
     * Reference to the product ordering.
     * Product can be deleted but order history remains (no CASCADE.REMOVE).
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "PRODUCT_ID", nullable = false, foreignKey = @ForeignKey(name = "FK_ORDER_ITEM_PRODUCT"))
    private Product product;

    /**
     * Product name snapshot at time of order.
     * Helps in reporting even if product is deleted later.
     */
    @Column(name = "PRODUCT_NAME", length = 100, nullable = false)
    private String productName;

    /**
     * Product SKU snapshot at time of order.
     */
    @Column(name = "PRODUCT_SKU", length = 50, nullable = false)
    private String productSku;

    /**
     * CRITICAL: Unit price at time of order placed (in BigDecimal).
     * Never use Product.price - product price changes over time!
     * This is the actual price the customer paid.
     * Scale of 2 for cents (e.g., 19.99).
     */
    @Positive(message = "Unit price must be positive")
    @Column(name = "UNIT_PRICE", precision = 10, scale = 2, nullable = false)
    private BigDecimal unitPrice;

    /**
     * Quantity of this item in the order.
     */
    @Positive(message = "Quantity must be positive")
    @Column(name = "QUANTITY", nullable = false)
    private Long quantity;

    /**
     * Discount applied to this item line (in percentage or amount).
     * Optional field for future enhancement.
     */
    @Column(name = "DISCOUNT", precision = 10, scale = 2)
    private BigDecimal discount;

    /**
     * Notes specific to this line item.
     */
    @Column(name = "NOTES", length = 200)
    private String notes;

    @PrePersist
    protected void onCreate() {
        super.onCreate();
    }

    @PreUpdate
    protected void onUpdate() {
        super.onUpdate();
    }

    /**
     * Calculates the line item total (unitPrice * quantity - discount).
     *
     * @return line item total amount
     */
    public BigDecimal calculateLineTotal() {
        BigDecimal lineTotal = this.unitPrice.multiply(new BigDecimal(this.quantity));
        if (this.discount != null && this.discount.compareTo(BigDecimal.ZERO) > 0) {
            lineTotal = lineTotal.subtract(this.discount);
        }
        return lineTotal;
    }
}

