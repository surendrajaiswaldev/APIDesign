package com.apidesign.event;

/** Fired after an order's status has transitioned to SHIPPED and committed. */
public record OrderShippedEvent(Long orderId, Long userId) {}
