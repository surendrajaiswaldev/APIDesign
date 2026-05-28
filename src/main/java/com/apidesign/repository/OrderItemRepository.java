package com.apidesign.repository;

import com.apidesign.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * OrderItem repository for line item operations.
 *
 * Typically used internally by OrderService.
 * Not directly exposed through REST API (accessed via Order endpoints).
 */
@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    /**
     * DERIVED QUERY: Find all items for an order.
     *
     * @param orderId the order ID
     * @return list of order items
     */
    List<OrderItem> findByOrderId(Long orderId);

    /**
     * JPQL Query: Find items by order ID with product details (JOIN).
     * JOIN helps fetch product information in single query.
     *
     * @param orderId the order ID
     * @return order items with product data populated
     */
    @Query("SELECT oi FROM OrderItem oi JOIN FETCH oi.product WHERE oi.order.id = :orderId")
    List<OrderItem> findItemsByOrderIdWithProduct(@Param("orderId") Long orderId);

    /**
     * DERIVED QUERY: Count items in an order.
     *
     * @param orderId the order ID
     * @return number of items
     */
    long countByOrderId(Long orderId);
}

