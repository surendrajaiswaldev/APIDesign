package com.apidesign.repository;

import com.apidesign.entity.Product;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findBySku(String sku);

    Page<Product> findByCategory(String category, Pageable pageable);

    @Query(
        """
        SELECT p FROM Product p
        WHERE p.price BETWEEN :minPrice AND :maxPrice
          AND p.isAvailable = true
        """)
    Page<Product> findByPriceRange(
        @Param("minPrice") BigDecimal minPrice,
        @Param("maxPrice") BigDecimal maxPrice,
        Pageable pageable);

    @Query(
        """
        SELECT p FROM Product p
        WHERE p.stockQuantity < p.minStockLevel
        ORDER BY p.stockQuantity ASC
        """)
    Page<Product> findLowStockProducts(Pageable pageable);

    /**
     * Search with all filters optional. {@code null} parameters disable that predicate.
     */
    @Query(
        """
        SELECT p FROM Product p
        WHERE (:category IS NULL OR p.category = :category)
          AND (:minPrice IS NULL OR p.price >= :minPrice)
          AND (:maxPrice IS NULL OR p.price <= :maxPrice)
          AND p.isAvailable = true
        ORDER BY p.name ASC
        """)
    Page<Product> searchProducts(
        @Param("category") String category,
        @Param("minPrice") BigDecimal minPrice,
        @Param("maxPrice") BigDecimal maxPrice,
        Pageable pageable);

    @Query(
        value =
            """
            SELECT p.* FROM PRODUCTS p
            WHERE p.IS_AVAILABLE = 1
            ORDER BY p.CREATED_AT DESC
            """,
        nativeQuery = true,
        countQuery = "SELECT COUNT(*) FROM PRODUCTS p WHERE p.IS_AVAILABLE = 1")
    Page<Product> findRecentProducts(Pageable pageable);

    boolean existsBySku(String sku);

    @Query("SELECT COUNT(p) FROM Product p WHERE p.isAvailable = true")
    long countAvailableProducts();

    /**
     * Atomically decrement stock iff there is enough on hand.
     *
     * @return 1 if the row was updated, 0 if stock was insufficient (or row missing).
     */
    @Modifying
    @Query(
        """
        UPDATE Product p
        SET p.stockQuantity = p.stockQuantity - :qty
        WHERE p.id = :id AND p.stockQuantity >= :qty
        """)
    int decrementStock(@Param("id") Long id, @Param("qty") Long qty);

    /**
     * Atomically return stock back to the product (used on order cancellation).
     */
    @Modifying
    @Query(
        """
        UPDATE Product p
        SET p.stockQuantity = p.stockQuantity + :qty
        WHERE p.id = :id
        """)
    int restoreStock(@Param("id") Long id, @Param("qty") Long qty);
}
