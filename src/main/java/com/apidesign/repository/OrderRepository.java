package com.apidesign.repository;

import com.apidesign.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Order repository demonstrating advanced query patterns.
 *
 * KEY CONCEPTS:
 *
 * N+1 PROBLEM EXAMPLE (WHAT TO AVOID):
 * =====================================
 * BAD Code:
 *   orders = repository.findAll();  // 1 SQL query
 *   for(Order o : orders) {
 *       User user = o.getUser();   // N additional queries (one per order)!
 *   }
 * TOTAL: 1 + N = N+1 queries
 *
 * SOLUTION 1 - JOIN FETCH in JPQL:
 * ================================
 * @Query("SELECT o FROM Order o JOIN FETCH o.user WHERE ...")
 * RESULT: 1 query with join
 *
 * SOLUTION 2 - @EntityGraph:
 * ==========================
 * @EntityGraph(attributePaths = {"user", "orderItems"})
 * RESULT: Hibernate generates optimized query
 *
 * This repository demonstrates both approaches.
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    /**
     * DERIVED QUERY: Find order by order number.
     */
    Optional<Order> findByOrderNumber(String orderNumber);

    /**
     * JPQL Query: Find orders by user.
     * SOLUTION to N+1: JOIN FETCH user and items
     *
     * Why JOIN FETCH:
     * - Ensures user is loaded in same query as orders
     * - Prevents lazy-loading multiple times
     * - Single query instead of N+1
     *
     * @param userId the user's ID
     * @param pageable pagination
     * @return orders for user with user and items fetched
     */
    @Query("SELECT DISTINCT o FROM Order o " +
           "JOIN FETCH o.user u " +
           "LEFT JOIN FETCH o.orderItems " +
           "WHERE u.id = :userId")
    Page<Order> findOrdersByUserIdWithDetails(@Param("userId") Long userId, Pageable pageable);

    /**
     * @EntityGraph Approach: Alternative to JOIN FETCH.
     * EntityGraph tells Spring Data which relationships to eagerly load.
     *
     * Benefits of @EntityGraph:
     * - Cleaner code than JOIN FETCH
     * - Hibernate optimizes query execution
     * - Works with derived queries
     *
     * @param userId the user ID
     * @param pageable pagination
     * @return orders with user and items loaded
     */
    @EntityGraph(attributePaths = {"user", "orderItems"})
    Page<Order> findByUserId(Long userId, Pageable pageable);

    /**
     * JPQL Query: Find orders by status.
     * JOIN FETCH to prevent N+1 on user lookups.
     *
     * @param status the order status
     * @param pageable pagination
     * @return orders with given status
     */
    @Query("SELECT DISTINCT o FROM Order o " +
           "JOIN FETCH o.user " +
           "WHERE o.orderStatus = :status " +
           "ORDER BY o.createdAt DESC")
    Page<Order> findOrdersByStatus(@Param("status") String status, Pageable pageable);

    /**
     * JPQL Query: Find orders within date range.
     * Useful for reporting and analytics.
     *
     * @param startDate from date
     * @param endDate to date
     * @param pageable pagination
     * @return orders created within date range
     */
    @Query("SELECT o FROM Order o WHERE o.createdAt BETWEEN :startDate AND :endDate")
    Page<Order> findOrdersByDateRange(@Param("startDate") LocalDateTime startDate,
                                       @Param("endDate") LocalDateTime endDate,
                                       Pageable pageable);

    /**
     * NATIVE SQL Query: Find high-value orders (complex aggregation).
     * Demonstrates: Complex SQL, aggregate functions
     *
     * Real-world use: Reports, business analytics
     *
     * @param minimumAmount minimum total amount
     * @param pageable pagination
     * @return high-value orders
     */
    @Query(value = "SELECT * FROM ORDERS WHERE TOTAL_AMOUNT > :minimumAmount " +
           "ORDER BY TOTAL_AMOUNT DESC",
           nativeQuery = true)
    Page<Order> findHighValueOrders(@Param("minimumAmount") Double minimumAmount, Pageable pageable);

    /**
     * JPQL Query: Find recent orders (optimization example).
     * Shows: Limiting results, ordering
     *
     * @param status order status
     * @param threshold the cutoff date/time (typically calculated as: LocalDateTime.now().minusDays(days))
     * @param pageable pagination
     * @return recent orders
     */
    @Query("SELECT o FROM Order o " +
           "WHERE o.orderStatus = :status " +
           "AND o.createdAt > :threshold " +
           "ORDER BY o.createdAt DESC")
    Page<Order> findRecentOrdersByStatus(@Param("status") String status,
                                         @Param("threshold") LocalDateTime threshold,
                                         Pageable pageable);

    /**
     * COUNT Query: Total orders for a user.
     */
    long countByUserId(Long userId);

    /**
     * COUNT Query: Orders by status.
     */
    @Query("SELECT COUNT(o) FROM Order o WHERE o.orderStatus = :status")
    long countByStatus(@Param("status") String status);
}

