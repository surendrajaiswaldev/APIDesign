package com.apidesign.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import com.apidesign.constants.ErrorCodes;
import com.apidesign.constants.OrderStatus;
import com.apidesign.dto.CreateOrderRequest;
import com.apidesign.dto.OrderDTO;
import com.apidesign.entity.Order;
import com.apidesign.entity.OrderItem;
import com.apidesign.entity.Product;
import com.apidesign.entity.User;
import com.apidesign.event.OrderEventPublisher;
import com.apidesign.exception.BusinessLogicException;
import com.apidesign.exception.ResourceNotFoundException;
import com.apidesign.mapper.OrderMapper;
import com.apidesign.repository.OrderItemRepository;
import com.apidesign.repository.OrderRepository;
import com.apidesign.repository.ProductRepository;
import com.apidesign.repository.UserRepository;
import com.apidesign.saga.OrderSagaOrchestrator;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("OrderService Tests")
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private UserRepository userRepository;
    @Mock private ProductRepository productRepository;
    @Mock private OrderMapper orderMapper;
    @Mock private OrderEventPublisher eventPublisher;
    // Order creation is now delegated to the saga orchestrator. Service-level tests
    // assert the delegation; full step coverage lives in OrderSagaOrchestratorTest.
    @Mock private OrderSagaOrchestrator sagaOrchestrator;

    @InjectMocks private OrderService orderService;

    private User mockUser;
    private Product mockProduct;
    private CreateOrderRequest createOrderRequest;
    private Order mockOrder;
    private OrderDTO mockOrderDTO;

    @BeforeEach
    void setUp() {
        mockUser = User.builder().id(1L).firstName("John").lastName("Doe")
            .email("john@example.com").isActive(true).build();
        mockUser.setCreatedAt(LocalDateTime.now());

        mockProduct = Product.builder().id(1L).sku("PROD-001").name("Test Product")
            .price(new BigDecimal("99.99")).stockQuantity(100L).minStockLevel(10L)
            .isAvailable(true).build();

        CreateOrderRequest.OrderItemRequest itemRequest =
            new CreateOrderRequest.OrderItemRequest(1L, 2L, null);
        createOrderRequest = new CreateOrderRequest(1L, List.of(itemRequest), "123 Main St", null);

        mockOrder = Order.builder().id(1L).orderNumber("ORD-1-ABCDEF12")
            .user(mockUser).orderStatus(OrderStatus.PENDING)
            .totalAmount(new BigDecimal("199.98")).build();

        mockOrderDTO = new OrderDTO(1L, "ORD-1-ABCDEF12", null, OrderStatus.PENDING,
            new BigDecimal("199.98"), null, null, null, null, null, null);
    }

    @Test
    @DisplayName("createOrder should delegate to the saga orchestrator")
    void testCreateOrderDelegatesToSaga() {
        when(sagaOrchestrator.start(createOrderRequest)).thenReturn(mockOrderDTO);

        OrderDTO result = orderService.createOrder(createOrderRequest);

        assertNotNull(result);
        assertEquals(OrderStatus.PENDING, result.orderStatus());
        verify(sagaOrchestrator, times(1)).start(createOrderRequest);
        // Direct repo calls now belong to the orchestrator — service should not invoke them itself.
        verify(productRepository, never()).decrementStock(any(), any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("createOrder should surface ResourceNotFoundException from the saga")
    void testCreateOrderUserNotFound() {
        CreateOrderRequest req = new CreateOrderRequest(
            999L, createOrderRequest.orderItems(), createOrderRequest.shippingAddress(), null);
        when(sagaOrchestrator.start(req))
            .thenThrow(new ResourceNotFoundException("nope", ErrorCodes.USER_NOT_FOUND));

        ResourceNotFoundException exception =
            assertThrows(ResourceNotFoundException.class, () -> orderService.createOrder(req));

        assertEquals(ErrorCodes.USER_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("createOrder should surface empty-items BusinessLogicException from the saga")
    void testCreateOrderEmptyItems() {
        CreateOrderRequest emptyOrder = new CreateOrderRequest(1L, List.of(), null, null);
        when(sagaOrchestrator.start(emptyOrder))
            .thenThrow(new BusinessLogicException("empty", ErrorCodes.ORDER_EMPTY_ITEMS));

        BusinessLogicException exception =
            assertThrows(BusinessLogicException.class, () -> orderService.createOrder(emptyOrder));

        assertEquals(ErrorCodes.ORDER_EMPTY_ITEMS, exception.getErrorCode());
    }

    @Test
    @DisplayName("createOrder should surface insufficient-stock BusinessLogicException from the saga")
    void testCreateOrderInsufficientStock() {
        when(sagaOrchestrator.start(createOrderRequest))
            .thenThrow(
                new BusinessLogicException("no stock", ErrorCodes.ORDER_INSUFFICIENT_STOCK));

        BusinessLogicException exception =
            assertThrows(BusinessLogicException.class,
                () -> orderService.createOrder(createOrderRequest));
        assertEquals(ErrorCodes.ORDER_INSUFFICIENT_STOCK, exception.getErrorCode());
    }

    @Test
    @DisplayName("Should update order status successfully with valid transition")
    void testUpdateOrderStatusSuccess() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(mockOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(mockOrder);
        when(orderMapper.toDTO(mockOrder)).thenReturn(mockOrderDTO);

        OrderDTO result = orderService.updateOrderStatus(1L, OrderStatus.CONFIRMED);

        assertNotNull(result);
        verify(orderRepository, times(1)).save(any(Order.class));
    }

    @Test
    @DisplayName("Should fail on invalid status transition")
    void testUpdateOrderStatusInvalidTransition() {
        mockOrder.setOrderStatus(OrderStatus.DELIVERED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(mockOrder));

        BusinessLogicException exception =
            assertThrows(BusinessLogicException.class,
                () -> orderService.updateOrderStatus(1L, OrderStatus.SHIPPED));

        assertEquals(ErrorCodes.ORDER_INVALID_STATUS_TRANSITION, exception.getErrorCode());
    }

    @Test
    @DisplayName("Should cancel order and return stock")
    void testCancelOrderSuccess() {
        OrderItem item = OrderItem.builder().id(1L).product(mockProduct).quantity(2L).build();
        mockOrder.addOrderItem(item);

        when(orderRepository.findById(1L)).thenReturn(Optional.of(mockOrder));
        when(productRepository.restoreStock(anyLong(), anyLong())).thenReturn(1);
        when(orderRepository.save(any(Order.class))).thenReturn(mockOrder);
        when(orderMapper.toDTO(mockOrder)).thenReturn(mockOrderDTO);

        OrderDTO result = orderService.cancelOrder(1L);

        assertNotNull(result);
        assertEquals(OrderStatus.CANCELLED, mockOrder.getOrderStatus());
        verify(productRepository, times(1)).restoreStock(1L, 2L);
    }

    @Test
    @DisplayName("Should fail when canceling non-PENDING/CONFIRMED order")
    void testCancelOrderInvalidStatus() {
        mockOrder.setOrderStatus(OrderStatus.DELIVERED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(mockOrder));

        BusinessLogicException exception =
            assertThrows(BusinessLogicException.class, () -> orderService.cancelOrder(1L));

        assertEquals(ErrorCodes.ORDER_INVALID_STATUS_TRANSITION, exception.getErrorCode());
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw exception when order not found")
    void testGetOrderByIdNotFound() {
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        ResourceNotFoundException exception =
            assertThrows(ResourceNotFoundException.class, () -> orderService.getOrderById(999L));

        assertEquals(ErrorCodes.ORDER_NOT_FOUND, exception.getErrorCode());
    }
}
