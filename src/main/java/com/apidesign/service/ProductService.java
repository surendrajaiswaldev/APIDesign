package com.apidesign.service;

import com.apidesign.constants.ErrorCodes;
import com.apidesign.dto.CreateProductRequest;
import com.apidesign.dto.ProductDTO;
import com.apidesign.dto.UpdateProductRequest;
import com.apidesign.entity.Product;
import com.apidesign.exception.BusinessLogicException;
import com.apidesign.exception.ResourceNotFoundException;
import com.apidesign.exception.ValidationException;
import com.apidesign.mapper.ProductMapper;
import com.apidesign.repository.ProductRepository;
import com.apidesign.response.PagedResponse;
import java.math.BigDecimal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    // A new product can change category listings already in the by-category cache, so
    // clear that entire cache. The single-product cache cannot have a stale entry for
    // this ID yet — nothing has read it — so leave it alone.
    @CacheEvict(cacheNames = "productsByCategory", allEntries = true)
    public ProductDTO createProduct(CreateProductRequest request) {
        log.info("Creating product sku={}", request.sku());
        if (productRepository.existsBySku(request.sku())) {
            throw new BusinessLogicException(
                "Product with SKU " + request.sku() + " already exists",
                ErrorCodes.PRODUCT_DUPLICATE_SKU);
        }
        Product product = productMapper.toEntity(request);
        if (product.getIsAvailable() == null) {
            product.setIsAvailable(true);
        }
        if (product.getStockQuantity() == null) {
            product.setStockQuantity(0L);
        }
        Product saved = productRepository.save(product);
        return productMapper.toDTO(saved);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "products", key = "#productId")
    public ProductDTO getProductById(Long productId) {
        return productMapper.toDTO(loadProduct(productId));
    }

    /** Entity-returning variant for HAL assemblers. Not cached (caches DTO at the other entry). */
    @Transactional(readOnly = true)
    public Product loadProduct(Long productId) {
        return productRepository.findById(productId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Product not found with ID: " + productId, ErrorCodes.PRODUCT_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public ProductDTO getProductBySku(String sku) {
        Product product = productRepository.findBySku(sku)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Product not found with SKU: " + sku, ErrorCodes.PRODUCT_NOT_FOUND));
        return productMapper.toDTO(product);
    }

    @Transactional(readOnly = true)
    public PagedResponse<ProductDTO> getAllProducts(Pageable pageable) {
        Page<Product> products = productRepository.findAll(pageable);
        return PagedResponse.from(products.map(productMapper::toDTO));
    }

    /** Entity-returning variant for HAL assemblers. */
    @Transactional(readOnly = true)
    public Page<Product> findAllProducts(Pageable pageable) {
        return productRepository.findAll(pageable);
    }

    /** Entity-returning variant for HAL assemblers. */
    @Transactional(readOnly = true)
    public Page<Product> findProductsByCategory(String category, Pageable pageable) {
        return productRepository.findByCategory(category, pageable);
    }

    /** Entity-returning variant for HAL assemblers. */
    @Transactional(readOnly = true)
    public Page<Product> findProductsByPriceRange(
        String category, java.math.BigDecimal minPrice, java.math.BigDecimal maxPrice, Pageable pageable) {
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new ValidationException(
                "minPrice must be less than or equal to maxPrice", ErrorCodes.INVALID_PRICE_RANGE);
        }
        return productRepository.searchProducts(category, minPrice, maxPrice, pageable);
    }

    /** Entity-returning variant for HAL assemblers. */
    @Transactional(readOnly = true)
    public Page<Product> findLowStockProductsEntity(Pageable pageable) {
        return productRepository.findLowStockProducts(pageable);
    }

    // Single-product change → drop just that entry. Category listings are page-keyed and
    // updating one row can shuffle any page, so dump the whole by-category cache.
    @Caching(evict = {
        @CacheEvict(cacheNames = "products", key = "#productId"),
        @CacheEvict(cacheNames = "productsByCategory", allEntries = true)
    })
    public ProductDTO updateProduct(Long productId, UpdateProductRequest request) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Product not found with ID: " + productId, ErrorCodes.PRODUCT_NOT_FOUND));

        if (request.price() != null && request.price().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessLogicException(
                "Product price must be positive", ErrorCodes.PRODUCT_INVALID_PRICE);
        }
        productMapper.updateEntityFromRequest(request, product);
        return productMapper.toDTO(productRepository.save(product));
    }

    @Caching(evict = {
        @CacheEvict(cacheNames = "products", key = "#productId"),
        @CacheEvict(cacheNames = "productsByCategory", allEntries = true)
    })
    public void deleteProduct(Long productId) {
        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException(
                "Product not found with ID: " + productId, ErrorCodes.PRODUCT_NOT_FOUND);
        }
        productRepository.deleteById(productId);
    }

    @Transactional(readOnly = true)
    @Cacheable(
        cacheNames = "productsByCategory",
        key = "#category + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
    public PagedResponse<ProductDTO> getProductsByCategory(String category, Pageable pageable) {
        Page<Product> products = productRepository.findByCategory(category, pageable);
        return PagedResponse.from(products.map(productMapper::toDTO));
    }

    @Transactional(readOnly = true)
    public PagedResponse<ProductDTO> searchProductsByPriceRange(
        String category, BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable) {
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new ValidationException(
                "minPrice must be less than or equal to maxPrice", ErrorCodes.INVALID_PRICE_RANGE);
        }
        Page<Product> products = productRepository.searchProducts(category, minPrice, maxPrice, pageable);
        return PagedResponse.from(products.map(productMapper::toDTO));
    }

    @Transactional(readOnly = true)
    public PagedResponse<ProductDTO> getLowStockProducts(Pageable pageable) {
        Page<Product> products = productRepository.findLowStockProducts(pageable);
        return PagedResponse.from(products.map(productMapper::toDTO));
    }
}
