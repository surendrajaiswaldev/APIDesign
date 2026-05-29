package com.apidesign.service;

import java.util.concurrent.ThreadLocalRandom;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Stub shipping carrier. Returns a fake tracking number on success; throws on a random
 * ~10% of calls so the saga's SCHEDULE_SHIPPING compensation path is exercisable.
 */
@Slf4j
@Service
public class ShippingService {

    private static final int SIMULATED_FAILURE_PCT = 10;

    /**
     * @param orderId order being shipped
     * @param address destination
     * @return synthetic tracking number {@code TRACK-<orderId>-<epochMs>}
     * @throws RuntimeException simulated carrier outage
     */
    public String scheduleShipment(Long orderId, String address) {
        if (ThreadLocalRandom.current().nextInt(100) < SIMULATED_FAILURE_PCT) {
            log.warn("Simulated shipping failure for order {} (address={})", orderId, address);
            throw new RuntimeException("Simulated shipping carrier outage");
        }
        String tracking = "TRACK-" + orderId + "-" + System.currentTimeMillis();
        log.info("Scheduled shipment for order {} address='{}' tracking={}", orderId, address, tracking);
        return tracking;
    }

    /** Compensation: cancel the previously-scheduled shipment. Always succeeds in this stub. */
    public void cancelShipment(Long orderId, String trackingNumber) {
        log.info("Cancelled shipment tracking={} for order {}", trackingNumber, orderId);
    }
}
