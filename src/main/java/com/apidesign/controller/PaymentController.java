package com.apidesign.controller;

import com.apidesign.dto.payment.ChargeRequest;
import com.apidesign.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Demonstration endpoint for the resilience patterns wired into {@link PaymentService}.
 *
 * <p>Hit it in a loop to see the circuit breaker open and the fallback ref take over;
 * stop, wait 10s ({@code wait-duration-in-open-state}), and watch it half-open and
 * recover.
 */
@RestController
@RequestMapping("/payments")
@Tag(
    name = "Payments",
    description = "Demonstration of Resilience4j circuit-breaker / retry / bulkhead stacking on a fake payment gateway.")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Operation(
        summary = "Charge an order (ADMIN only)",
        description =
            "Simulated payment with random 30% failure and 100-2000ms latency. Wrapped by "
                + "Resilience4j; observable circuit open/half-open transitions when called in a loop.")
    @ApiResponses(
        value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Payment processed (or fell back to DEGRADED:order-... when the circuit is open)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
        })
    @PostMapping("/charge")
    @PreAuthorize("hasRole('ADMIN')")
    public String charge(@RequestBody ChargeRequest body) {
        return paymentService.chargeOrder(body.orderId(), body.amount());
    }
}
