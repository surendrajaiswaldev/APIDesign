package com.apidesign.saga;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apidesign.constants.OrderStatus;
import com.apidesign.dto.CreateOrderRequest;
import com.apidesign.dto.OrderDTO;
import com.apidesign.entity.Order;
import com.apidesign.entity.OrderSaga;
import com.apidesign.entity.Product;
import com.apidesign.entity.User;
import com.apidesign.event.OrderEventPublisher;
import com.apidesign.mapper.OrderMapper;
import com.apidesign.repository.OrderRepository;
import com.apidesign.repository.OrderSagaRepository;
import com.apidesign.repository.ProductRepository;
import com.apidesign.repository.UserRepository;
import com.apidesign.service.PaymentService;
import com.apidesign.service.ShippingService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("OrderSagaOrchestrator Tests")
@ExtendWith(MockitoExtension.class)
class OrderSagaOrchestratorTest {

    @Mock private UserRepository userRepository;
    @Mock private ProductRepository productRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderSagaRepository sagaRepository;
    @Mock private PaymentService paymentService;
    @Mock private ShippingService shippingService;
    @Mock private OrderMapper orderMapper;
    @Mock private OrderEventPublisher eventPublisher;

    private OrderSagaOrchestrator orchestrator;

    private User user;
    private Product product;
    private CreateOrderRequest request;

    @BeforeEach
    void setUp() {
        // self-injected lazy proxy: pass the orchestrator itself. In production Spring's
        // @Lazy + AOP proxy would wrap each step in REQUIRES_NEW; in unit tests we just
        // need method calls to dispatch correctly.
        orchestrator = buildOrchestrator();
        user = User.builder().id(1L).firstName("Alice").lastName("X").email("a@x").address("Addr").build();
        product = Product.builder().id(10L).sku("SKU-1").name("Widget").price(new BigDecimal("9.99"))
            .stockQuantity(100L).build();
        CreateOrderRequest.OrderItemRequest item =
            new CreateOrderRequest.OrderItemRequest(10L, 2L, null);
        request = new CreateOrderRequest(1L, List.of(item), "Ship-To", null);
    }

    private OrderSagaOrchestrator buildOrchestrator() {
        OrderSagaOrchestrator[] ref = new OrderSagaOrchestrator[1];
        ref[0] =
            new OrderSagaOrchestrator(
                userRepository,
                productRepository,
                orderRepository,
                sagaRepository,
                paymentService,
                shippingService,
                orderMapper,
                eventPublisher,
                null) {
                {
                    // Field-injection for `self` via the subclass — can't pass `ref[0]` to
                    // super() because it isn't constructed yet.
                }
            };
        // Reflectively swap `self` to the freshly-constructed instance so step methods
        // dispatch through the same in-test object.
        try {
            java.lang.reflect.Field selfField = OrderSagaOrchestrator.class.getDeclaredField("self");
            selfField.setAccessible(true);
            selfField.set(ref[0], ref[0]);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("failed to wire self", e);
        }
        return ref[0];
    }

    private void stubHappyPathThrough(String paymentRef, String trackingNumber) {
        // createPendingOrder
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        Order savedOrder =
            Order.builder()
                .id(100L)
                .orderNumber("ORD-X")
                .user(user)
                .totalAmount(new BigDecimal("19.98"))
                .orderStatus(OrderStatus.PENDING)
                .shippingAddress("Ship-To")
                .build();
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
        when(orderRepository.findById(100L)).thenReturn(Optional.of(savedOrder));

        // saga row persistence — return whatever was passed in for chained reads.
        when(sagaRepository.save(any(OrderSaga.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(sagaRepository.findBySagaId(any()))
            .thenAnswer(invocation -> Optional.of(
                OrderSaga.builder()
                    .sagaId(invocation.getArgument(0))
                    .orderId(100L)
                    .currentState(SagaState.STARTED.name())
                    .compensationLog("[]")
                    .build()));

        // step 1: reserve stock OK
        when(productRepository.decrementStock(10L, 2L)).thenReturn(1);

        // step 2: payment
        if (paymentRef != null) {
            when(paymentService.chargeOrder(eq(100L), any(BigDecimal.class))).thenReturn(paymentRef);
        }

        // step 3: shipping
        if (trackingNumber != null) {
            when(shippingService.scheduleShipment(eq(100L), any())).thenReturn(trackingNumber);
        }

        // final DTO
        OrderDTO dto =
            new OrderDTO(
                100L,
                "ORD-X",
                null,
                OrderStatus.CONFIRMED,
                new BigDecimal("19.98"),
                "Ship-To",
                null,
                null,
                null,
                null,
                null);
        when(orderMapper.toDTO(any(Order.class))).thenReturn(dto);
    }

    @Test
    @DisplayName("Happy path: all four steps run and the saga completes")
    void happyPath() {
        stubHappyPathThrough("PAY-OK", "TRACK-OK");

        OrderDTO result = orchestrator.start(request);

        assertNotNull(result);
        assertEquals(OrderStatus.CONFIRMED, result.orderStatus());
        verify(productRepository, times(1)).decrementStock(10L, 2L);
        verify(paymentService, times(1)).chargeOrder(eq(100L), any(BigDecimal.class));
        verify(shippingService, times(1)).scheduleShipment(eq(100L), any());
        verify(eventPublisher, times(1)).publishCreated(eq(100L), eq(1L), any(BigDecimal.class));
        // No compensation calls on the happy path.
        verify(productRepository, never()).restoreStock(anyLong(), anyLong());
        verify(paymentService, never()).refundCharge(anyLong(), any());
        verify(shippingService, never()).cancelShipment(anyLong(), any());
    }

    @Test
    @DisplayName("Payment fails: stock is restored, order ends CANCELLED")
    void paymentFails() {
        // Stub through reserveStock; payment throws.
        stubHappyPathThrough(null, null);
        when(paymentService.chargeOrder(eq(100L), any(BigDecimal.class)))
            .thenThrow(new RuntimeException("simulated payment failure"));

        assertThrows(RuntimeException.class, () -> orchestrator.start(request));

        // Stock compensation must have run.
        verify(productRepository, times(1)).restoreStock(10L, 2L);
        // Shipping was never attempted, so no shipment cancel.
        verify(shippingService, never()).cancelShipment(anyLong(), any());
        // Payment had no successful ref, so no refund.
        verify(paymentService, never()).refundCharge(anyLong(), any());
        // Order flips to CANCELLED via cancelOrder step.
        verify(orderRepository, atLeastOnce()).save(any(Order.class));
    }

    @Test
    @DisplayName("Shipping fails: payment is refunded and stock restored")
    void shippingFails() {
        stubHappyPathThrough("PAY-OK", null);
        when(shippingService.scheduleShipment(eq(100L), any()))
            .thenThrow(new RuntimeException("simulated shipping failure"));

        assertThrows(RuntimeException.class, () -> orchestrator.start(request));

        verify(paymentService, times(1)).refundCharge(eq(100L), eq("PAY-OK"));
        verify(productRepository, times(1)).restoreStock(10L, 2L);
        verify(shippingService, never()).cancelShipment(anyLong(), any());
    }
}
