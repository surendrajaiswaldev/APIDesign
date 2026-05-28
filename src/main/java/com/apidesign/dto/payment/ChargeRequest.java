package com.apidesign.dto.payment;

import java.math.BigDecimal;

/** Body for POST /payments/charge. */
public record ChargeRequest(Long orderId, BigDecimal amount) {}
