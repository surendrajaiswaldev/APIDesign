package com.apidesign.dto;

import com.apidesign.validator.PositiveBigDecimal;
import com.apidesign.validator.ValidSku;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Request DTO for creating a new Product.
 *
 * Validation Strategy:
 * - @NotBlank: Required fields
 * - @Size: String length constraints
 * - @Positive: Numeric constraints
 * - @ValidSku: Custom business logic (SKU format)
 * - @PositiveBigDecimal: Custom decimal validation
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateProductRequest {
    @NotBlank(message = "SKU is required")
    @ValidSku(message = "SKU must be alphanumeric with hyphens, 3-20 characters")
    private String sku;

    @NotBlank(message = "Product name is required")
    @Size(min = 2, max = 100, message = "Product name must be between 2 and 100 characters")
    private String name;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    @NotBlank(message = "Price is required")
    @PositiveBigDecimal(message = "Price must be greater than zero")
    private BigDecimal price;

    @NotBlank(message = "Stock quantity is required")
    @Positive(message = "Stock quantity must be positive")
    private Long stockQuantity;

    @Positive(message = "Minimum stock level must be positive")
    private Long minStockLevel;

    @Size(max = 50, message = "Category must not exceed 50 characters")
    private String category;

    @Size(max = 100, message = "Supplier must not exceed 100 characters")
    private String supplier;
}

