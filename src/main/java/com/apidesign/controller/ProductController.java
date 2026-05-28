package com.apidesign.controller;

import com.apidesign.constants.ApiEndpoints;
import com.apidesign.dto.CreateProductRequest;
import com.apidesign.dto.ProductDTO;
import com.apidesign.dto.UpdateProductRequest;
import com.apidesign.response.ApiResponse;
import com.apidesign.response.PagedResponse;
import com.apidesign.service.ProductService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping(ApiEndpoints.PRODUCT_BASE_PATH)
@Validated
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProductDTO>> createProduct(
        @Valid @RequestBody CreateProductRequest request) {
        ProductDTO created = productService.createProduct(request);
        return new ResponseEntity<>(
            ApiResponse.created(created, "Product created successfully"), HttpStatus.CREATED);
    }

    @GetMapping(ApiEndpoints.PRODUCT_BY_ID)
    public ResponseEntity<ApiResponse<ProductDTO>> getProductById(
        @PathVariable @Positive(message = "Product ID must be positive") Long id) {
        ProductDTO product = productService.getProductById(id);
        return ResponseEntity.ok(ApiResponse.success(product, "Product retrieved successfully"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<ProductDTO>>> getAllProducts(
        @PageableDefault(size = 20) Pageable pageable) {
        PagedResponse<ProductDTO> paged = productService.getAllProducts(pageable);
        return ResponseEntity.ok(
            ApiResponse.success(paged,
                String.format("Retrieved %d products", paged.content().size())));
    }

    @GetMapping(ApiEndpoints.PRODUCT_BY_CATEGORY)
    public ResponseEntity<ApiResponse<PagedResponse<ProductDTO>>> getProductsByCategory(
        @PathVariable String category, @PageableDefault(size = 20) Pageable pageable) {
        PagedResponse<ProductDTO> paged = productService.getProductsByCategory(category, pageable);
        return ResponseEntity.ok(ApiResponse.success(paged, "Products retrieved successfully"));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<PagedResponse<ProductDTO>>> searchProducts(
        @RequestParam(required = false) String category,
        @RequestParam(required = false) BigDecimal minPrice,
        @RequestParam(required = false) BigDecimal maxPrice,
        @PageableDefault(size = 20) Pageable pageable) {
        PagedResponse<ProductDTO> paged =
            productService.searchProductsByPriceRange(category, minPrice, maxPrice, pageable);
        return ResponseEntity.ok(ApiResponse.success(paged, "Search results retrieved successfully"));
    }

    @PutMapping(ApiEndpoints.PRODUCT_BY_ID)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProductDTO>> updateProduct(
        @PathVariable @Positive(message = "Product ID must be positive") Long id,
        @Valid @RequestBody UpdateProductRequest request) {
        ProductDTO updated = productService.updateProduct(id, request);
        return ResponseEntity.ok(ApiResponse.success(updated, "Product updated successfully"));
    }

    @DeleteMapping(ApiEndpoints.PRODUCT_BY_ID)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteProduct(
        @PathVariable @Positive(message = "Product ID must be positive") Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/lowstock")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PagedResponse<ProductDTO>>> getLowStockProducts(
        @PageableDefault(size = 20) Pageable pageable) {
        PagedResponse<ProductDTO> paged = productService.getLowStockProducts(pageable);
        return ResponseEntity.ok(
            ApiResponse.success(paged, "Low stock products retrieved successfully"));
    }
}
