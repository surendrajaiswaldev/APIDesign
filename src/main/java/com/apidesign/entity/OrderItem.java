package com.apidesign.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Line item on an {@link Order}. Snapshots product name/SKU/price at order creation
 * so price changes after the fact don't rewrite history.
 */
@Entity
@Table(
    name = "ORDER_ITEMS",
    indexes = {
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

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "order_item_seq_gen")
    @SequenceGenerator(
        name = "order_item_seq_gen",
        sequenceName = "ORDER_ITEM_SEQ",
        allocationSize = 50)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "ORDER_ID",
        nullable = false,
        foreignKey = @ForeignKey(name = "FK_ORDER_ITEM_ORDER"))
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "PRODUCT_ID",
        nullable = false,
        foreignKey = @ForeignKey(name = "FK_ORDER_ITEM_PRODUCT"))
    private Product product;

    @Column(name = "PRODUCT_NAME", length = 100, nullable = false)
    private String productName;

    @Column(name = "PRODUCT_SKU", length = 50, nullable = false)
    private String productSku;

    @Positive(message = "Unit price must be positive")
    @Column(name = "UNIT_PRICE", precision = 10, scale = 2, nullable = false)
    private BigDecimal unitPrice;

    @Positive(message = "Quantity must be positive")
    @Column(name = "QUANTITY", nullable = false)
    private Long quantity;

    @Column(name = "DISCOUNT", precision = 10, scale = 2)
    private BigDecimal discount;

    @Column(name = "NOTES", length = 200)
    private String notes;

    public BigDecimal calculateLineTotal() {
        BigDecimal lineTotal = this.unitPrice.multiply(BigDecimal.valueOf(this.quantity));
        if (this.discount != null && this.discount.compareTo(BigDecimal.ZERO) > 0) {
            lineTotal = lineTotal.subtract(this.discount);
        }
        return lineTotal;
    }
}
