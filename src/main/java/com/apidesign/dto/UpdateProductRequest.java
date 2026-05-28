package com.apidesign.dto;

import com.apidesign.validator.PositiveBigDecimal;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record UpdateProductRequest(
    @Size(min = 2, max = 100, message = "Product name must be between 2 and 100 characters")
        String name,
    @Size(max = 500, message = "Description must not exceed 500 characters") String description,
    @PositiveBigDecimal(message = "Price must be greater than zero") BigDecimal price,
    @PositiveOrZero(message = "Stock quantity must be zero or positive") Long stockQuantity,
    @PositiveOrZero(message = "Minimum stock level must be zero or positive") Long minStockLevel,
    String category,
    Boolean isAvailable,
    String supplier) {}
