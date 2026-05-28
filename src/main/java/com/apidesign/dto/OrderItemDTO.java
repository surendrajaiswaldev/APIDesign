package com.apidesign.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrderItemDTO(
    Long id,
    Long orderId,
    Long productId,
    String productName,
    String productSku,
    BigDecimal unitPrice,
    Long quantity,
    BigDecimal discount,
    String notes,
    LocalDateTime createdAt) {

    public BigDecimal lineTotal() {
        if (unitPrice == null || quantity == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = unitPrice.multiply(BigDecimal.valueOf(quantity));
        if (discount != null && discount.compareTo(BigDecimal.ZERO) > 0) {
            total = total.subtract(discount);
        }
        return total;
    }
}
