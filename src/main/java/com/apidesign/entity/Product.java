package com.apidesign.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Product available for purchase. Stock writes go through atomic SQL in
 * {@code ProductRepository.decrementStock(...)} / {@code restoreStock(...)}; the in-memory
 * helpers below remain for non-concurrent paths (snapshotting, tests).
 *
 * Carries an optimistic lock {@link Version} field — concurrent modifications of the same
 * row will fail with {@code OptimisticLockingFailureException} and surface as 409.
 */
@Entity
@Table(
    name = "PRODUCTS",
    indexes = {
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

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "product_seq_gen")
    @SequenceGenerator(name = "product_seq_gen", sequenceName = "PRODUCT_SEQ", allocationSize = 50)
    private Long id;

    @Version
    @Column(name = "VERSION")
    private Long version;

    @NotBlank(message = "SKU is required")
    @Column(name = "SKU", length = 50, nullable = false, unique = true)
    private String sku;

    @NotBlank(message = "Product name is required")
    @Column(name = "NAME", length = 100, nullable = false)
    private String name;

    @Column(name = "DESCRIPTION", length = 500)
    private String description;

    @Positive(message = "Price must be positive")
    @Column(name = "PRICE", precision = 10, scale = 2, nullable = false)
    private BigDecimal price;

    @Column(name = "STOCK_QUANTITY", nullable = false)
    @Builder.Default
    private Long stockQuantity = 0L;

    @Column(name = "MIN_STOCK_LEVEL")
    @Builder.Default
    private Long minStockLevel = 10L;

    @Column(name = "CATEGORY", length = 50)
    private String category;

    @Column(name = "IS_AVAILABLE", nullable = false)
    @Builder.Default
    private Boolean isAvailable = true;

    @Column(name = "SUPPLIER", length = 100)
    private String supplier;

    public boolean hasEnoughStock(Long quantity) {
        return this.stockQuantity != null && quantity != null && this.stockQuantity >= quantity;
    }
}
