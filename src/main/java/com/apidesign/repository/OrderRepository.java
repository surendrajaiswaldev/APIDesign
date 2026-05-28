package com.apidesign.repository;

import com.apidesign.constants.OrderStatus;
import com.apidesign.entity.Order;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderNumber(String orderNumber);

    @Query(
        """
        SELECT DISTINCT o FROM Order o
        JOIN FETCH o.user u
        LEFT JOIN FETCH o.orderItems
        WHERE u.id = :userId
        """)
    Page<Order> findOrdersByUserIdWithDetails(@Param("userId") Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "orderItems"})
    Page<Order> findByUserId(Long userId, Pageable pageable);

    @Query(
        """
        SELECT DISTINCT o FROM Order o
        JOIN FETCH o.user
        WHERE o.orderStatus = :status
        ORDER BY o.createdAt DESC
        """)
    Page<Order> findOrdersByStatus(@Param("status") OrderStatus status, Pageable pageable);

    @Query(
        """
        SELECT o FROM Order o
        WHERE o.createdAt BETWEEN :startDate AND :endDate
        """)
    Page<Order> findOrdersByDateRange(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate,
        Pageable pageable);

    @Query(
        value =
            """
            SELECT * FROM ORDERS
            WHERE TOTAL_AMOUNT > :minimumAmount
            ORDER BY TOTAL_AMOUNT DESC
            """,
        nativeQuery = true)
    Page<Order> findHighValueOrders(
        @Param("minimumAmount") Double minimumAmount, Pageable pageable);

    @Query(
        """
        SELECT o FROM Order o
        WHERE o.orderStatus = :status
          AND o.createdAt > :threshold
        ORDER BY o.createdAt DESC
        """)
    Page<Order> findRecentOrdersByStatus(
        @Param("status") OrderStatus status,
        @Param("threshold") LocalDateTime threshold,
        Pageable pageable);

    long countByUserId(Long userId);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.orderStatus = :status")
    long countByStatus(@Param("status") OrderStatus status);

    /**
     * All orders still in {@code status} with creation timestamp older than {@code threshold}.
     * Used by the cleanup scheduler to auto-cancel stale PENDING orders.
     */
    List<Order> findByOrderStatusAndCreatedAtBefore(OrderStatus status, LocalDateTime threshold);
}
