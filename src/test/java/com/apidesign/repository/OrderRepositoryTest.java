package com.apidesign.repository;

import static org.junit.jupiter.api.Assertions.*;

import com.apidesign.constants.OrderStatus;
import com.apidesign.entity.Order;
import com.apidesign.entity.User;
import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@DisplayName("OrderRepository Tests")
@DataJpaTest
class OrderRepositoryTest {

    @Autowired private OrderRepository orderRepository;
    @Autowired private UserRepository userRepository;

    private User testUser;
    private Order testOrder;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
            .firstName("John")
            .lastName("Doe")
            .email("john@example.com")
            .passwordHash("hash")
            .phoneNumber("1234567890")
            .isActive(true)
            .roles(Set.of("USER"))
            .build();
        userRepository.save(testUser);

        testOrder = Order.builder()
            .orderNumber("ORD-TEST-1")
            .user(testUser)
            .orderStatus(OrderStatus.PENDING)
            .totalAmount(new BigDecimal("199.99"))
            .shippingAddress("123 Test St")
            .build();
        orderRepository.save(testOrder);
    }

    @Test
    @DisplayName("Should find order by order number")
    void testFindByOrderNumber() {
        var result = orderRepository.findByOrderNumber("ORD-TEST-1");
        assertTrue(result.isPresent());
        assertEquals("ORD-TEST-1", result.get().getOrderNumber());
    }

    @Test
    @DisplayName("Should return empty when order number not found")
    void testFindByOrderNumberNotFound() {
        assertTrue(orderRepository.findByOrderNumber("ORD-NONEXISTENT").isEmpty());
    }

    @Test
    @DisplayName("Should find orders by user with pagination")
    void testFindOrdersByUserIdWithPagination() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Order> result = orderRepository.findByUserId(testUser.getId(), pageable);
        assertEquals(1, result.getTotalElements());
    }

    @Test
    @DisplayName("Should find orders by status")
    void testFindOrdersByStatus() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Order> result = orderRepository.findOrdersByStatus(OrderStatus.PENDING, pageable);
        assertTrue(result.getTotalElements() > 0);
        assertTrue(result.getContent().stream()
            .allMatch(o -> OrderStatus.PENDING == o.getOrderStatus()));
    }

    @Test
    @DisplayName("Should count orders by user")
    void testCountByUserId() {
        assertEquals(1, orderRepository.countByUserId(testUser.getId()));
    }

    @Test
    @DisplayName("Should count orders by status")
    void testCountByStatus() {
        assertTrue(orderRepository.countByStatus(OrderStatus.PENDING) >= 1);
    }

    @Test
    @DisplayName("Should return empty page when no orders for user")
    void testFindOrdersByUserNoResults() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Order> result = orderRepository.findByUserId(999L, pageable);
        assertEquals(0, result.getTotalElements());
    }
}
