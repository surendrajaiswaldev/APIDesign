package com.apidesign.controller;

import com.apidesign.constants.ApiEndpoints;
import com.apidesign.dto.CreateProductRequest;
import com.apidesign.dto.ProductDTO;
import com.apidesign.dto.UpdateProductRequest;
import com.apidesign.response.ApiResponse;
import com.apidesign.response.PagedResponse;
import com.apidesign.service.ProductService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * Product REST Controller for product management endpoints.
 *
 * Features:
 * - Create, read, update, delete products
 * - Search products by category and price range
 * - Monitor low stock products
 * - Pagination and sorting support
 */
@Slf4j
@RestController
@RequestMapping(ApiEndpoints.PRODUCT_BASE_PATH)
@Validated
public class ProductController {
    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /**
     * Create a new product.
     *
     * HTTP: POST /api/v1/products
     * Status: 201 (Created)
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ProductDTO>> createProduct(
            @Valid @RequestBody CreateProductRequest request) {

        log.info("Creating product - SKU: {}", request.getSku());
        ProductDTO createdProduct = productService.createProduct(request);

        ApiResponse<ProductDTO> response = ApiResponse.success(
            createdProduct,
            "Product created successfully"
        );

        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * Get product by ID.
     *
     * HTTP: GET /api/v1/products/{id}
     */
    @GetMapping(ApiEndpoints.PRODUCT_BY_ID)
    public ResponseEntity<ApiResponse<EntityModel<ProductDTO>>> getProductById(
            @PathVariable
            @Positive(message = "Product ID must be positive")
            Long id) {

        log.info("Fetching product: {}", id);
        ProductDTO product = productService.getProductById(id);

        EntityModel<ProductDTO> productModel = EntityModel.of(product,
            WebMvcLinkBuilder.linkTo(
                WebMvcLinkBuilder.methodOn(ProductController.class).getProductById(id))
                .withSelfRel(),
            WebMvcLinkBuilder.linkTo(ProductController.class)
                .withRel("all-products")
        );

        ApiResponse<EntityModel<ProductDTO>> response = ApiResponse.success(
            productModel,
            "Product retrieved successfully"
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Get all products with pagination.
     *
     * HTTP: GET /api/v1/products?page=0&size=20
     */
    @GetMapping
    public ResponseEntity<ApiResponse<CollectionModel<ProductDTO>>> getAllProducts(
            @PageableDefault(size = 20, page = 0) Pageable pageable) {

        log.info("Fetching all products - Page: {}, Size: {}",
                pageable.getPageNumber(), pageable.getPageSize());

        PagedResponse<ProductDTO> pagedProducts = productService.getAllProducts(pageable);

        CollectionModel<ProductDTO> productCollection = CollectionModel.of(
            pagedProducts.getContent(),
            WebMvcLinkBuilder.linkTo(
                WebMvcLinkBuilder.methodOn(ProductController.class).getAllProducts(pageable))
                .withSelfRel()
        );

        ApiResponse<CollectionModel<ProductDTO>> response = ApiResponse.success(
            productCollection,
            String.format("Retrieved %d products", pagedProducts.getContent().size())
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Get products by category.
     *
     * HTTP: GET /api/v1/products/category/{category}?page=0&size=20
     */
    @GetMapping(ApiEndpoints.PRODUCT_BY_CATEGORY)
    public ResponseEntity<ApiResponse<CollectionModel<ProductDTO>>> getProductsByCategory(
            @PathVariable String category,
            @PageableDefault(size = 20, page = 0) Pageable pageable) {

        log.info("Fetching products by category: {}", category);
        PagedResponse<ProductDTO> pagedProducts = productService.getProductsByCategory(category, pageable);

        CollectionModel<ProductDTO> productCollection = CollectionModel.of(
            pagedProducts.getContent(),
            WebMvcLinkBuilder.linkTo(
                WebMvcLinkBuilder.methodOn(ProductController.class)
                    .getProductsByCategory(category, pageable))
                .withSelfRel()
        );

        ApiResponse<CollectionModel<ProductDTO>> response = ApiResponse.success(
            productCollection,
            "Products retrieved successfully"
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Search products by price range and category.
     *
     * HTTP: GET /api/v1/products/search?category=Electronics&minPrice=10&maxPrice=1000&page=0
     */
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<CollectionModel<ProductDTO>>> searchProducts(
            @RequestParam String category,
            @RequestParam BigDecimal minPrice,
            @RequestParam BigDecimal maxPrice,
            @PageableDefault(size = 20, page = 0) Pageable pageable) {

        log.info("Searching products - Category: {}, Price: {} to {}",
                category, minPrice, maxPrice);

        PagedResponse<ProductDTO> pagedProducts = productService.searchProductsByPriceRange(
            category, minPrice, maxPrice, pageable);

        CollectionModel<ProductDTO> productCollection = CollectionModel.of(
            pagedProducts.getContent(),
            WebMvcLinkBuilder.linkTo(
                WebMvcLinkBuilder.methodOn(ProductController.class)
                    .searchProducts(category, minPrice, maxPrice, pageable))
                .withSelfRel()
        );

        ApiResponse<CollectionModel<ProductDTO>> response = ApiResponse.success(
            productCollection,
            "Search results retrieved successfully"
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Update a product.
     *
     * HTTP: PUT /api/v1/products/{id}
     */
    @PutMapping(ApiEndpoints.PRODUCT_BY_ID)
    public ResponseEntity<ApiResponse<ProductDTO>> updateProduct(
            @PathVariable
            @Positive(message = "Product ID must be positive")
            Long id,
            @Valid @RequestBody UpdateProductRequest request) {

        log.info("Updating product: {}", id);
        ProductDTO updatedProduct = productService.updateProduct(id, request);

        ApiResponse<ProductDTO> response = ApiResponse.success(
            updatedProduct,
            "Product updated successfully"
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Delete a product.
     *
     * HTTP: DELETE /api/v1/products/{id}
     */
    @DeleteMapping(ApiEndpoints.PRODUCT_BY_ID)
    public ResponseEntity<Void> deleteProduct(
            @PathVariable
            @Positive(message = "Product ID must be positive")
            Long id) {

        log.info("Deleting product: {}", id);
        productService.deleteProduct(id);

        return ResponseEntity.noContent().build();
    }

    /**
     * Get low stock products (inventory alerts).
     *
     * HTTP: GET /api/v1/products/lowstock?page=0&size=20
     */
    @GetMapping("/lowstock")
    public ResponseEntity<ApiResponse<CollectionModel<ProductDTO>>> getLowStockProducts(
            @PageableDefault(size = 20, page = 0) Pageable pageable) {

        log.info("Fetching low stock products");
        PagedResponse<ProductDTO> lowStockProducts = productService.getLowStockProducts(pageable);

        CollectionModel<ProductDTO> productCollection = CollectionModel.of(
            lowStockProducts.getContent(),
            WebMvcLinkBuilder.linkTo(
                WebMvcLinkBuilder.methodOn(ProductController.class).getLowStockProducts(pageable))
                .withSelfRel()
        );

        ApiResponse<CollectionModel<ProductDTO>> response = ApiResponse.success(
            productCollection,
            "Low stock products retrieved successfully"
        );

        return ResponseEntity.ok(response);
    }
}

