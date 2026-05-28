package com.apidesign.event;

import java.math.BigDecimal;

/** Fired after an Order row has been inserted and the surrounding transaction has committed. */
public record OrderCreatedEvent(Long orderId, Long userId, BigDecimal totalAmount) {}
