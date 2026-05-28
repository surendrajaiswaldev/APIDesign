package com.apidesign.event;

/** Fired after an order has been cancelled (user-initiated or auto-cancelled by scheduler). */
public record OrderCancelledEvent(Long orderId, Long userId, String reason) {}
