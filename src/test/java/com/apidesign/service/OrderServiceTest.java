package com.apidesign.service;

import com.apidesign.constants.ErrorCodes;
import com.apidesign.constants.OrderStatus;
import com.apidesign.dto.CreateOrderRequest;
import com.apidesign.dto.OrderDTO;
import com.apidesign.entity.Order;
import com.apidesign.entity.OrderItem;
import com.apidesign.entity.Product;
import com.apidesign.entity.User;
import com.apidesign.exception.BusinessLogicException;
import com.apidesign.exception.ResourceNotFoundException;
import com.apidesign.mapper.OrderMapper;
import com.apidesign.repository.OrderRepository;
import com.apidesign.repository.ProductRepository;
import com.apidesign.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OrderService - Complex business logic testing.
 *
 * Key Points Tested:
 * 1. Order creation with validation
 * 2. Stock availability checking
 * 3. Inventory reduction
 * 4. Status transitions with validation
 * 5. Order cancellation with stock return
 *
 * This demonstrates testing complex business workflows.
 */
@DisplayName("OrderService Tests")
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private ProductService productService;

    @InjectMocks
    private OrderService orderService;

    private User mockUser;
    private Product mockProduct;
    private CreateOrderRequest createOrderRequest;
    private Order mockOrder;
    private OrderDTO mockOrderDTO;

    @BeforeEach
    void setUp() {
        // User
        mockUser = User.builder()
            .id(1L)
            .firstName("John")
            .lastName("Doe")
            .email("john@example.com")
            .isActive(true)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        // Product
        mockProduct = Product.builder()
            .id(1L)
            .sku("PROD-001")
            .name("Test Product")
            .price(new BigDecimal("99.99"))
            .stockQuantity(100L)
            .minStockLevel(10L)
            .isAvailable(true)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        // Order Request
        CreateOrderRequest.OrderItemRequest itemRequest =
            CreateOrderRequest.OrderItemRequest.builder()
                .productId(1L)
                .quantity(2L)
                .build();

        createOrderRequest = CreateOrderRequest.builder()
            .userId(1L)
            .orderItems(List.of(itemRequest))
            .shippingAddress("123 Main St")
            .build();

        // Order
        mockOrder = Order.builder()
            .id(1L)
            .orderNumber("ORD-20260509-ABC12")
            .user(mockUser)
            .orderStatus(OrderStatus.PENDING)
            .totalAmount(new BigDecimal("199.98"))
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        mockOrderDTO = OrderDTO.builder()
            .id(1L)
            .orderNumber("ORD-20260509-ABC12")
            .orderStatus(OrderStatus.PENDING)
            .totalAmount(new BigDecimal("199.98"))
            .build();
    }

    @Test
    @DisplayName("Should create order successfully with valid data")
    void testCreateOrderSuccess() {
        // Arrange
        when(userRepository.findById(1L))
            .thenReturn(Optional.of(mockUser));
        when(productRepository.findById(1L))
            .thenReturn(Optional.of(mockProduct));
        when(orderRepository.save(any(Order.class)))
            .thenReturn(mockOrder);
        when(orderMapper.toDTO(mockOrder))
            .thenReturn(mockOrderDTO);
        doNothing().when(productService).reduceStock(1L, 2L);

        // Act
        OrderDTO result = orderService.createOrder(createOrderRequest);

        // Assert
        assertNotNull(result);
        assertEquals(OrderStatus.PENDING, result.getOrderStatus());
        assertEquals(new BigDecimal("199.98"), result.getTotalAmount());

        // Verify interactions
        verify(userRepository, times(1)).findById(1L);
        verify(productRepository, times(1)).findById(1L);
        verify(productService, times(1)).reduceStock(1L, 2L);
        verify(orderRepository, times(1)).save(any(Order.class));
    }

    @Test
    @DisplayName("Should fail when user does not exist")
    void testCreateOrderUserNotFound() {
        // Arrange
        when(userRepository.findById(999L))
            .thenReturn(Optional.empty());

        // Act & Assert
        ResourceNotFoundException exception = assertThrows(
            ResourceNotFoundException.class,
            () -> {
                CreateOrderRequest request = createOrderRequest;
                request.setUserId(999L);
                orderService.createOrder(request);
            }
        );

        assertEquals(ErrorCodes.USER_NOT_FOUND, exception.getErrorCode());
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should fail when order has no items")
    void testCreateOrderEmptyItems() {
        // Arrange
        CreateOrderRequest emptyOrder = CreateOrderRequest.builder()
            .userId(1L)
            .orderItems(List.of())  // Empty items
            .build();

        when(userRepository.findById(1L))
            .thenReturn(Optional.of(mockUser));

        // Act & Assert
        BusinessLogicException exception = assertThrows(
            BusinessLogicException.class,
            () -> orderService.createOrder(emptyOrder)
        );

        assertEquals(ErrorCodes.ORDER_EMPTY_ITEMS, exception.getErrorCode());
    }

    @Test
    @DisplayName("Should fail when product not found")
    void testCreateOrderProductNotFound() {
        // Arrange
        when(userRepository.findById(1L))
            .thenReturn(Optional.of(mockUser));
        when(productRepository.findById(1L))
            .thenReturn(Optional.empty());

        // Act & Assert
        ResourceNotFoundException exception = assertThrows(
            ResourceNotFoundException.class,
            () -> orderService.createOrder(createOrderRequest)
        );

        assertEquals(ErrorCodes.ORDER_ITEM_PRODUCT_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("Should fail when insufficient stock")
    void testCreateOrderInsufficientStock() {
        // Arrange
        mockProduct.setStockQuantity(1L); // Only 1, but requesting 2

        when(userRepository.findById(1L))
            .thenReturn(Optional.of(mockUser));
        when(productRepository.findById(1L))
            .thenReturn(Optional.of(mockProduct));

        // Act & Assert
        BusinessLogicException exception = assertThrows(
            BusinessLogicException.class,
            () -> orderService.createOrder(createOrderRequest)
        );

        assertEquals(ErrorCodes.ORDER_INSUFFICIENT_STOCK, exception.getErrorCode());
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should update order status successfully with valid transition")
    void testUpdateOrderStatusSuccess() {
        // Arrange
        when(orderRepository.findById(1L))
            .thenReturn(Optional.of(mockOrder));
        when(orderRepository.save(any(Order.class)))
            .thenReturn(mockOrder);
        when(orderMapper.toDTO(mockOrder))
            .thenReturn(mockOrderDTO);

        // Act
        OrderDTO result = orderService.updateOrderStatus(1L, OrderStatus.CONFIRMED);

        // Assert
        assertNotNull(result);
        verify(orderRepository, times(1)).save(any(Order.class));
    }

    @Test
    @DisplayName("Should fail on invalid status transition")
    void testUpdateOrderStatusInvalidTransition() {
        // Arrange
        mockOrder.setOrderStatus(OrderStatus.DELIVERED); // Terminal state

        when(orderRepository.findById(1L))
            .thenReturn(Optional.of(mockOrder));

        // Act & Assert
        BusinessLogicException exception = assertThrows(
            BusinessLogicException.class,
            () -> orderService.updateOrderStatus(1L, OrderStatus.SHIPPED)
        );

        assertEquals(ErrorCodes.ORDER_INVALID_STATUS_TRANSITION, exception.getErrorCode());
    }

    @Test
    @DisplayName("Should cancel order and return stock")
    void testCancelOrderSuccess() {
        // Arrange
        mockProduct.setStockQuantity(98L); // Already reduced
        OrderItem item = OrderItem.builder()
            .id(1L)
            .product(mockProduct)
            .quantity(2L)
            .build();
        mockOrder.addOrderItem(item);

        when(orderRepository.findById(1L))
            .thenReturn(Optional.of(mockOrder));
        when(orderRepository.save(any(Order.class)))
            .thenReturn(mockOrder);
        when(orderMapper.toDTO(mockOrder))
            .thenReturn(mockOrderDTO);

        // Act
        OrderDTO result = orderService.cancelOrder(1L);

        // Assert
        assertNotNull(result);
        assertEquals(OrderStatus.CANCELLED, mockOrder.getOrderStatus());
        verify(orderRepository, times(1)).save(any(Order.class));
    }

    @Test
    @DisplayName("Should fail when canceling non- PENDING/CONFIRMED order")
    void testCancelOrderInvalidStatus() {
        // Arrange
        mockOrder.setOrderStatus(OrderStatus.DELIVERED); // Terminal state

        when(orderRepository.findById(1L))
            .thenReturn(Optional.of(mockOrder));

        // Act & Assert
        BusinessLogicException exception = assertThrows(
            BusinessLogicException.class,
            () -> orderService.cancelOrder(1L)
        );

        assertEquals(ErrorCodes.ORDER_INVALID_STATUS_TRANSITION, exception.getErrorCode());
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw exception when order not found")
    void testGetOrderByIdNotFound() {
        // Arrange
        when(orderRepository.findById(999L))
            .thenReturn(Optional.empty());

        // Act & Assert
        ResourceNotFoundException exception = assertThrows(
            ResourceNotFoundException.class,
            () -> orderService.getOrderById(999L)
        );

        assertEquals(ErrorCodes.ORDER_NOT_FOUND, exception.getErrorCode());
    }
}

