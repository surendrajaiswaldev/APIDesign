package com.apidesign.dto;

import com.apidesign.validator.PositiveBigDecimal;
import com.apidesign.validator.ValidSku;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateProductRequest(
    @NotBlank(message = "SKU is required")
        @ValidSku(message = "SKU must be alphanumeric with hyphens, 3-20 characters")
        String sku,
    @NotBlank(message = "Product name is required")
        @Size(min = 2, max = 100, message = "Product name must be between 2 and 100 characters")
        String name,
    @Size(max = 500, message = "Description must not exceed 500 characters") String description,
    @NotNull(message = "Price is required")
        @PositiveBigDecimal(message = "Price must be greater than zero")
        BigDecimal price,
    @NotNull(message = "Stock quantity is required")
        @PositiveOrZero(message = "Stock quantity must be zero or positive")
        Long stockQuantity,
    @PositiveOrZero(message = "Minimum stock level must be zero or positive") Long minStockLevel,
    @Size(max = 50, message = "Category must not exceed 50 characters") String category,
    @Size(max = 100, message = "Supplier must not exceed 100 characters") String supplier) {}
