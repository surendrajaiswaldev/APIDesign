package com.apidesign.controller;

import com.apidesign.dto.payment.ChargeRequest;
import com.apidesign.service.PaymentService;
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
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/charge")
    @PreAuthorize("hasRole('ADMIN')")
    public String charge(@RequestBody ChargeRequest body) {
        return paymentService.chargeOrder(body.orderId(), body.amount());
    }
}
