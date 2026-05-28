package com.apidesign.service;

import com.apidesign.constants.ErrorCodes;
import com.apidesign.dto.CreateProductRequest;
import com.apidesign.dto.ProductDTO;
import com.apidesign.dto.UpdateProductRequest;
import com.apidesign.entity.Product;
import com.apidesign.exception.BusinessLogicException;
import com.apidesign.exception.ResourceNotFoundException;
import com.apidesign.mapper.ProductMapper;
import com.apidesign.repository.ProductRepository;
import com.apidesign.response.PagedResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Product Service - Business logic for product management and inventory.
 *
 * Key Responsibilities:
 * 1. Product CRUD operations
 * 2. Stock management (check availability, reduce stock)
 * 3. Product search and filtering (price ranges, categories)
 * 4. Low stock alerts
 * 5. Business validation (price > 0, unique SKU, etc.)
 */
@Slf4j
@Service
@Transactional
public class ProductService {
    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    public ProductService(ProductRepository productRepository, ProductMapper productMapper) {
        this.productRepository = productRepository;
        this.productMapper = productMapper;
    }

    /**
     * Create a new product.
     *
     * Business Validations:
     * 1. SKU must be unique
     * 2. Price must be positive
     * 3. Stock quantity must be non-negative
     *
     * @param request the create product request
     * @return created product DTO
     * @throws BusinessLogicException if validation fails
     */
    public ProductDTO createProduct(CreateProductRequest request) {
        log.info("Creating product with SKU: {}", request.getSku());

        // Validate unique SKU
        if (productRepository.existsBySku(request.getSku())) {
            log.warn("Product creation failed - SKU already exists: {}", request.getSku());
            throw new BusinessLogicException(
                "Product with SKU " + request.getSku() + " already exists",
                ErrorCodes.PRODUCT_NOT_FOUND
            );
        }

        // Validate price
        if (request.getPrice() == null || request.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessLogicException(
                "Product price must be positive",
                ErrorCodes.PRODUCT_INVALID_PRICE
            );
        }

        Product product = productMapper.toEntity(request);
        Product savedProduct = productRepository.save(product);

        log.info("Product created with ID: {}", savedProduct.getId());
        return productMapper.toDTO(savedProduct);
    }

    /**
     * Get product by ID.
     *
     * @param productId the product ID
     * @return product DTO
     * @throws ResourceNotFoundException if not found
     */
    @Transactional(readOnly = true)
    public ProductDTO getProductById(Long productId) {
        log.debug("Fetching product: {}", productId);

        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Product not found with ID: " + productId,
                ErrorCodes.PRODUCT_NOT_FOUND
            ));

        return productMapper.toDTO(product);
    }

    /**
     * Get product by SKU.
     *
     * @param sku the product SKU
     * @return product DTO
     * @throws ResourceNotFoundException if not found
     */
    @Transactional(readOnly = true)
    public ProductDTO getProductBySku(String sku) {
        log.debug("Fetching product by SKU: {}", sku);

        Product product = productRepository.findBySku(sku)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Product not found with SKU: " + sku,
                ErrorCodes.PRODUCT_NOT_FOUND
            ));

        return productMapper.toDTO(product);
    }

    /**
     * Get all available products with pagination.
     *
     * @param pageable pagination parameters
     * @return paginated products
     */
    @Transactional(readOnly = true)
    public PagedResponse<ProductDTO> getAllProducts(Pageable pageable) {
        log.debug("Fetching all available products - Page: {}, Size: {}",
                pageable.getPageNumber(), pageable.getPageSize());

        Page<Product> products = productRepository.findAll(pageable);
        return PagedResponse.from(products.map(productMapper::toDTO));
    }

    /**
     * Update an existing product.
     *
     * @param productId the product ID
     * @param request the update request
     * @return updated product DTO
     * @throws ResourceNotFoundException if not found
     */
    public ProductDTO updateProduct(Long productId, UpdateProductRequest request) {
        log.info("Updating product: {}", productId);

        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Product not found with ID: " + productId,
                ErrorCodes.PRODUCT_NOT_FOUND
            ));

        // Validate price if being updated
        if (request.getPrice() != null &&
            request.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessLogicException(
                "Product price must be positive",
                ErrorCodes.PRODUCT_INVALID_PRICE
            );
        }

        productMapper.updateEntityFromRequest(request, product);
        Product updatedProduct = productRepository.save(product);

        log.info("Product updated: {}", productId);
        return productMapper.toDTO(updatedProduct);
    }

    /**
     * Delete a product.
     * Note: For audit trail, implement soft delete instead.
     *
     * @param productId the product ID
     * @throws ResourceNotFoundException if not found
     */
    public void deleteProduct(Long productId) {
        log.info("Deleting product: {}", productId);

        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException(
                "Product not found with ID: " + productId,
                ErrorCodes.PRODUCT_NOT_FOUND
            );
        }

        productRepository.deleteById(productId);
        log.info("Product deleted: {}", productId);
    }

    /**
     * Get products by category with pagination.
     *
     * @param category the product category
     * @param pageable pagination parameters
     * @return paginated products
     */
    @Transactional(readOnly = true)
    public PagedResponse<ProductDTO> getProductsByCategory(String category, Pageable pageable) {
        log.debug("Fetching products by category: {}", category);

        Page<Product> products = productRepository.findByCategory(category, pageable);
        return PagedResponse.from(products.map(productMapper::toDTO));
    }

    /**
     * Search products by price range and category.
     *
     * Real-world use: E-commerce search/filter functionality
     *
     * @param category product category
     * @param minPrice minimum price
     * @param maxPrice maximum price
     * @param pageable pagination
     * @return matching products
     */
    @Transactional(readOnly = true)
    public PagedResponse<ProductDTO> searchProductsByPriceRange(
            String category,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Pageable pageable) {

        log.debug("Searching products - Category: {}, Price range: {} to {}",
                category, minPrice, maxPrice);

        Page<Product> products = productRepository.searchProducts(
            category, minPrice, maxPrice, pageable);

        return PagedResponse.from(products.map(productMapper::toDTO));
    }

    /**
     * Get products with low stock (reorder alerts).
     *
     * Business Logic:
     * - stockQuantity < minStockLevel
     * - Useful for inventory management
     *
     * @param pageable pagination
     * @return low stock products
     */
    @Transactional(readOnly = true)
    public PagedResponse<ProductDTO> getLowStockProducts(Pageable pageable) {
        log.debug("Fetching low stock products");

        Page<Product> products = productRepository.findLowStockProducts(pageable);
        return PagedResponse.from(products.map(productMapper::toDTO));
    }

    /**
     * Check if product has sufficient stock for order.
     * Used by OrderService during order placement.
     *
     * @param productId the product ID
     * @param quantity quantity needed
     * @return true if sufficient stock
     * @throws ResourceNotFoundException if product not found
     * @throws BusinessLogicException if insufficient stock
     */
    @Transactional(readOnly = true)
    public boolean checkStockAvailability(Long productId, Long quantity) {
        log.debug("Checking stock for product: {} - Quantity: {}", productId, quantity);

        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Product not found with ID: " + productId,
                ErrorCodes.PRODUCT_NOT_FOUND
            ));

        if (!product.hasEnoughStock(quantity)) {
            log.warn("Insufficient stock for product: {} - Available: {}, Requested: {}",
                    productId, product.getStockQuantity(), quantity);
            throw new BusinessLogicException(
                "Insufficient stock for product: " + product.getName(),
                ErrorCodes.PRODUCT_OUT_OF_STOCK
            );
        }

        return true;
    }

    /**
     * Reduce product stock after order placement.
     * Called during order confirmation in OrderService.
     *
     * IMPORTANT: Method is public but should only be called from OrderService!
     * Consider making package-private if not needed elsewhere.
     *
     * @param productId the product ID
     * @param quantity quantity to reduce
     * @throws ResourceNotFoundException if product not found
     */
    public void reduceStock(Long productId, Long quantity) {
        log.info("Reducing stock for product: {} - Quantity: {}", productId, quantity);

        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Product not found with ID: " + productId,
                ErrorCodes.PRODUCT_NOT_FOUND
            ));

        product.reduceStock(quantity);
        productRepository.save(product);

        log.info("Stock reduced successfully for product: {}", productId);
    }
}
