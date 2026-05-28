package com.apidesign.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO for OrderItem entity - used in API responses.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderItemDTO {
    private Long id;
    private Long orderId;
    private Long productId;
    private String productName;
    private String productSku;
    private BigDecimal unitPrice;
    private Long quantity;
    private BigDecimal discount;
    private String notes;
    private LocalDateTime createdAt;

    /**
     * Helper method to calculate line item total.
     */
    public BigDecimal getLineTotal() {
        if (unitPrice == null || quantity == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = unitPrice.multiply(new BigDecimal(quantity));
        if (discount != null && discount.compareTo(BigDecimal.ZERO) > 0) {
            total = total.subtract(discount);
        }
        return total;
    }
}

