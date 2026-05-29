package com.apidesign.controller;

import com.apidesign.assembler.ProductModelAssembler;
import com.apidesign.constants.ApiEndpoints;
import com.apidesign.dto.CreateProductRequest;
import com.apidesign.dto.ProductDTO;
import com.apidesign.dto.UpdateProductRequest;
import com.apidesign.entity.Product;
import com.apidesign.response.ApiResponse;
import com.apidesign.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.PagedModel;
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
@Tag(name = "Products", description = "Catalogue, pricing, stock, and search.")
public class ProductController {

    private final ProductService productService;
    private final ProductModelAssembler productAssembler;
    private final PagedResourcesAssembler<Product> pagedAssembler;

    public ProductController(
        ProductService productService,
        ProductModelAssembler productAssembler,
        PagedResourcesAssembler<Product> pagedAssembler) {
        this.productService = productService;
        this.productAssembler = productAssembler;
        this.pagedAssembler = pagedAssembler;
    }

    @Operation(
        summary = "Create a product (ADMIN only)",
        description = "Creates a new product. Idempotency-Key header recommended for retries.")
    @ApiResponses(
        value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Product created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
        })
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProductDTO>> createProduct(
        @Valid @RequestBody CreateProductRequest request) {
        ProductDTO created = productService.createProduct(request);
        return new ResponseEntity<>(
            ApiResponse.created(created, "Product created successfully"), HttpStatus.CREATED);
    }

    @Operation(
        summary = "Get product by ID",
        description = "Returns a HAL representation with self, products, and by-category links.")
    @ApiResponses(
        value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Product found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
        })
    @GetMapping(value = ApiEndpoints.PRODUCT_BY_ID, produces = MediaTypes.HAL_JSON_VALUE)
    public ResponseEntity<EntityModel<ProductDTO>> getProductById(
        @Parameter(description = "Product identifier", example = "42")
        @PathVariable @Positive(message = "Product ID must be positive") Long id) {
        Product product = productService.loadProduct(id);
        return ResponseEntity.ok(productAssembler.toModel(product));
    }

    @Operation(
        summary = "List products",
        description = "Paginated catalogue. HAL PagedModel with first/prev/next/last links.")
    @ApiResponses(
        value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Page returned")
        })
    @GetMapping(produces = MediaTypes.HAL_JSON_VALUE)
    public ResponseEntity<PagedModel<EntityModel<ProductDTO>>> getAllProducts(
        @Parameter(name = "page", description = "Zero-based page index", example = "0")
        @PageableDefault(size = 20) Pageable pageable) {
        Page<Product> page = productService.findAllProducts(pageable);
        return ResponseEntity.ok(pagedAssembler.toModel(page, productAssembler));
    }

    @Operation(
        summary = "List products by category",
        description = "Returns products in a specific category as a HAL PagedModel.")
    @ApiResponses(
        value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Page returned")
        })
    @GetMapping(value = ApiEndpoints.PRODUCT_BY_CATEGORY, produces = MediaTypes.HAL_JSON_VALUE)
    public ResponseEntity<PagedModel<EntityModel<ProductDTO>>> getProductsByCategory(
        @Parameter(description = "Category name", example = "electronics")
        @PathVariable String category,
        @PageableDefault(size = 20) Pageable pageable) {
        Page<Product> page = productService.findProductsByCategory(category, pageable);
        return ResponseEntity.ok(pagedAssembler.toModel(page, productAssembler));
    }

    @Operation(
        summary = "Search products by price + category",
        description = "All filters optional. minPrice must be <= maxPrice; otherwise 400.")
    @ApiResponses(
        value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Search results returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest")
        })
    @GetMapping(value = "/search", produces = MediaTypes.HAL_JSON_VALUE)
    public ResponseEntity<PagedModel<EntityModel<ProductDTO>>> searchProducts(
        @RequestParam(required = false) String category,
        @Parameter(description = "Lower price bound, inclusive", example = "10.00")
        @RequestParam(required = false) BigDecimal minPrice,
        @Parameter(description = "Upper price bound, inclusive", example = "100.00")
        @RequestParam(required = false) BigDecimal maxPrice,
        @PageableDefault(size = 20) Pageable pageable) {
        Page<Product> page =
            productService.findProductsByPriceRange(category, minPrice, maxPrice, pageable);
        return ResponseEntity.ok(pagedAssembler.toModel(page, productAssembler));
    }

    @Operation(
        summary = "Update a product (ADMIN only)",
        description = "Partial-update on the product row. Caches are evicted.")
    @ApiResponses(
        value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Product updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
        })
    @PutMapping(ApiEndpoints.PRODUCT_BY_ID)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProductDTO>> updateProduct(
        @PathVariable @Positive(message = "Product ID must be positive") Long id,
        @Valid @RequestBody UpdateProductRequest request) {
        ProductDTO updated = productService.updateProduct(id, request);
        return ResponseEntity.ok(ApiResponse.success(updated, "Product updated successfully"));
    }

    @Operation(
        summary = "Delete a product (ADMIN only)",
        description = "Hard-deletes the product row.")
    @ApiResponses(
        value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Product deleted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
        })
    @DeleteMapping(ApiEndpoints.PRODUCT_BY_ID)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteProduct(
        @PathVariable @Positive(message = "Product ID must be positive") Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(
        summary = "List low-stock products (ADMIN only)",
        description = "Products whose stockQuantity has dropped below minStockLevel.")
    @ApiResponses(
        value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Page returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
        })
    @GetMapping(value = "/lowstock", produces = MediaTypes.HAL_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PagedModel<EntityModel<ProductDTO>>> getLowStockProducts(
        @PageableDefault(size = 20) Pageable pageable) {
        Page<Product> page = productService.findLowStockProductsEntity(pageable);
        return ResponseEntity.ok(pagedAssembler.toModel(page, productAssembler));
    }
}
