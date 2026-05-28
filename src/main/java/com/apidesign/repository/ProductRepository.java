package com.apidesign.repository;

import com.apidesign.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Product repository demonstrating filtering, sorting, and complex queries.
 *
 * Real-world scenarios:
 * - Search products by multiple criteria
 * - Price range filtering
 * - Stock availability checks
 * - Category-based filtering with pagination
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * DERIVED QUERY: Find product by SKU.
     */
    Optional<Product> findBySku(String sku);

    /**
     * DERIVED QUERY: Find all products in a category.
     */
    Page<Product> findByCategory(String category, Pageable pageable);

    /**
     * JPQL Query: Find products by price range.
     * Demonstrates: Parameter binding, comparison operators
     *
     * @param minPrice minimum price
     * @param maxPrice maximum price
     * @param pageable pagination
     * @return products within price range
     */
    @Query("SELECT p FROM Product p WHERE p.price BETWEEN :minPrice AND :maxPrice AND p.isAvailable = true")
    Page<Product> findByPriceRange(@Param("minPrice") BigDecimal minPrice,
                                    @Param("maxPrice") BigDecimal maxPrice,
                                    Pageable pageable);

    /**
     * JPQL Query: Find products that are low on stock.
     * Demonstrates: Where clause with field comparison
     *
     * @param pageable pagination
     * @return products with stock below minimum level
     */
    @Query("SELECT p FROM Product p WHERE p.stockQuantity < p.minStockLevel ORDER BY p.stockQuantity ASC")
    Page<Product> findLowStockProducts(Pageable pageable);

    /**
     * JPQL Query: Find available products with complex filtering.
     * Useful for e-commerce search/filter scenarios.
     *
     * @param category product category
     * @param minPrice minimum price
     * @param maxPrice maximum price
     * @param pageable pagination info
     * @return filtered and paginated products
     */
    @Query("SELECT p FROM Product p WHERE p.category = :category " +
           "AND p.price BETWEEN :minPrice AND :maxPrice " +
           "AND p.isAvailable = true " +
           "ORDER BY p.name ASC")
    Page<Product> searchProducts(@Param("category") String category,
                                 @Param("minPrice") BigDecimal minPrice,
                                 @Param("maxPrice") BigDecimal maxPrice,
                                 Pageable pageable);

    /**
     * NATIVE SQL Query: Find products with ORDER BY using database functions.
     * Example: Most popular products by order count.
     * Demonstrates: Join between tables, aggregation
     *
     * This shows relationship with OrderItems - later in Order queries.
     */
    @Query(value = "SELECT p.* FROM PRODUCTS p WHERE p.IS_AVAILABLE = 1 " +
           "ORDER BY p.CREATED_AT DESC",
           nativeQuery = true,
           countQuery = "SELECT COUNT(*) FROM PRODUCTS p WHERE p.IS_AVAILABLE = 1")
    Page<Product> findRecentProducts(Pageable pageable);

    /**
     * DERIVED QUERY: Check if product exists by SKU.
     * More efficient than findBySku for existence checks.
     */
    boolean existsBySku(String sku);

    /**
     * COUNT Query: Total available products.
     */
    @Query("SELECT COUNT(p) FROM Product p WHERE p.isAvailable = true")
    long countAvailableProducts();
}

