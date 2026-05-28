package com.apidesign.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

/**
 * Product entity representing items available for purchase.
 *
 * Relationship: Product -> OrderItems (One-to-Many)
 * A product can be part of multiple order items.
 *
 * Why BigDecimal for price: Essential for monetary calculations to avoid
 * floating-point precision issues. Never use double/float for currency!
 *
 * Why LAZY loading: OrderItems will fetch products only when accessed,
 * avoiding unnecessary queries. Use @Fetch(FetchMode.JOIN) in repository
 * queries only when needed.
 */
@Entity
@Table(name = "PRODUCTS", indexes = {
    @Index(name = "IDX_PRODUCT_CATEGORY", columnList = "CATEGORY"),
    @Index(name = "IDX_PRODUCT_SKU", columnList = "SKU")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Product extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /**
     * Stock Keeping Unit - unique product identifier.
     * Important for inventory management and product lookup.
     */
    @NotBlank(message = "SKU is required")
    @Column(name = "SKU", length = 50, nullable = false, unique = true)
    private String sku;

    @NotBlank(message = "Product name is required")
    @Column(name = "NAME", length = 100, nullable = false)
    private String name;

    @Column(name = "DESCRIPTION", length = 500)
    private String description;

    /**
     * Product price in BigDecimal to maintain precision.
     * Scale of 2 represents cents (e.g., 19.99).
     */
    @Positive(message = "Price must be positive")
    @Column(name = "PRICE", precision = 10, scale = 2, nullable = false)
    private BigDecimal price;

    /**
     * Current stock quantity.
     * Can be extended with warehouse-level stock tracking.
     */
    @Column(name = "STOCK_QUANTITY", nullable = false)
    @Builder.Default
    private Long stockQuantity = 0L;

    /**
     * Minimum stock level for reorder alerts.
     */
    @Column(name = "MIN_STOCK_LEVEL")
    @Builder.Default
    private Long minStockLevel = 10L;

    /**
     * Product category for filtering and searching.
     */
    @Column(name = "CATEGORY", length = 50)
    private String category;

    /**
     * Flag to indicate if product is available for purchase.
     */
    @Column(name = "IS_AVAILABLE", nullable = false)
    @Builder.Default
    private Boolean isAvailable = true;

    /**
     * Supplier/Manufacturer information.
     */
    @Column(name = "SUPPLIER", length = 100)
    private String supplier;

    @PrePersist
    protected void onCreate() {
        super.onCreate();
        if (this.stockQuantity == null) {
            this.stockQuantity = 0L;
        }
        if (this.isAvailable == null) {
            this.isAvailable = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        super.onUpdate();
    }

    /**
     * Helper method to check if product has sufficient stock.
     * Used in order validation logic.
     *
     * @param quantity quantity to check
     * @return true if sufficient stock available
     */
    public boolean hasEnoughStock(Long quantity) {
        return this.stockQuantity != null && this.stockQuantity >= quantity;
    }

    /**
     * Helper method to reduce stock after order placement.
     * Called during order processing.
     *
     * @param quantity quantity to reduce
     */
    public void reduceStock(Long quantity) {
        if (this.stockQuantity != null && this.stockQuantity >= quantity) {
            this.stockQuantity -= quantity;
        }
    }
}

