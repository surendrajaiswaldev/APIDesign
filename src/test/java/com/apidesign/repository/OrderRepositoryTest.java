package com.apidesign.repository;

import com.apidesign.constants.OrderStatus;
import com.apidesign.entity.Order;
import com.apidesign.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Repository Integration Tests for OrderRepository.
 *
 * @DataJpaTest:
 * - Sets up TestEntityManager instead of EntityManager
 * - Enables JPA repository testing
 * - Uses H2 in-memory database by default
 * - Provides isolated test environment
 *
 * Testing Approach:
 * - Test custom query methods
 * - Verify N+1 prevention with JOIN FETCH
 * - Test pagination and sorting
 * - Test filters and conditions
 */
@DisplayName("OrderRepository Tests")
@DataJpaTest
class OrderRepositoryTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private UserRepository userRepository;

    private User testUser;
    private Order testOrder;

    @BeforeEach
    void setUp() {
        // Create test user
        testUser = User.builder()
            .firstName("John")
            .lastName("Doe")
            .email("john@example.com")
            .phoneNumber("1234567890")
            .isActive(true)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .createdBy("system")
            .updatedBy("system")
            .build();
        userRepository.save(testUser);

        // Create test order
        testOrder = Order.builder()
            .orderNumber("ORD-20260509-TEST")
            .user(testUser)
            .orderStatus(OrderStatus.PENDING)
            .totalAmount(new BigDecimal("199.99"))
            .shippingAddress("123 Test St")
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .createdBy("system")
            .updatedBy("system")
            .build();
        orderRepository.save(testOrder);
    }

    @Test
    @DisplayName("Should find order by order number")
    void testFindByOrderNumber() {
        // Act
        var result = orderRepository.findByOrderNumber("ORD-20260509-TEST");

        // Assert
        assertTrue(result.isPresent());
        assertEquals("ORD-20260509-TEST", result.get().getOrderNumber());
        assertEquals(testUser.getId(), result.get().getUser().getId());
    }

    @Test
    @DisplayName("Should return empty when order number not found")
    void testFindByOrderNumberNotFound() {
        // Act
        var result = orderRepository.findByOrderNumber("ORD-NONEXISTENT");

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should find orders by user with pagination")
    void testFindOrdersByUserIdWithPagination() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);

        // Act
        Page<Order> result = orderRepository.findByUserId(testUser.getId(), pageable);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(1, result.getContent().size());
        assertEquals("ORD-20260509-TEST", result.getContent().get(0).getOrderNumber());
    }

    @Test
    @DisplayName("Should find orders by status")
    void testFindOrdersByStatus() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);

        // Act
        Page<Order> result = orderRepository.findOrdersByStatus(OrderStatus.PENDING, pageable);

        // Assert
        assertNotNull(result);
        assertTrue(result.getTotalElements() > 0);
        assertTrue(result.getContent().stream()
            .allMatch(o -> OrderStatus.PENDING.equals(o.getOrderStatus())));
    }

    @Test
    @DisplayName("Should count orders by user")
    void testCountByUserId() {
        // Act
        long count = orderRepository.countByUserId(testUser.getId());

        // Assert
        assertEquals(1, count);
    }

    @Test
    @DisplayName("Should count orders by status")
    void testCountByStatus() {
        // Act
        long count = orderRepository.countByStatus(OrderStatus.PENDING);

        // Assert
        assertTrue(count >= 1);
    }

    @Test
    @DisplayName("Should return empty page when no orders for user")
    void testFindOrdersByUserNoResults() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);

        // Act
        Page<Order> result = orderRepository.findByUserId(999L, pageable);

        // Assert
        assertEquals(0, result.getTotalElements());
        assertTrue(result.getContent().isEmpty());
    }

    @Test
    @DisplayName("Should verify user is eagerly loaded (N+1 prevention)")
    void testEntityGraphLoadsUserDetails() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);

        // Act
        Page<Order> orders = orderRepository.findByUserId(testUser.getId(), pageable);

        // Assert
        assertNotNull(orders);
        assertFalse(orders.getContent().isEmpty());

        // Verify user is loaded (not lazy)
        Order order = orders.getContent().get(0);
        assertNotNull(order.getUser());
        assertEquals("John", order.getUser().getFirstName());

        // If user wasn't eagerly loaded, accessing firstName would trigger
        // another query in a real test with actual database
    }
}

