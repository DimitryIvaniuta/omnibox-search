package com.github.dimitryivaniuta.gateway.search.lb;

import java.util.List;

/**
 * Simple registry-style load balancer that stores unique instance addresses.
 * <p>
 * Thread-safety:
 * - All implementations MUST be safe for concurrent register() + reads.
 */
public interface LoadBalancer {

    /**
     * Register new instance address.
     * <p>
     * Rules:
     * - Address must be non-null/non-blank.
     * - Address must not already be registered.
     * - Registry cannot exceed MAX_CAPACITY.
     *
     * @param address service instance address (e.g. "http://10.0.0.12:8080")
     * @throws IllegalArgumentException  if address is null/blank
     * @throws DuplicateAddressException if address already exists
     * @throws CapacityExceededException if capacity (10) already reached
     */
    void register(String address);

    /**
     * @return immutable snapshot of all registered addresses
     * in insertion order.
     */
    List<String> getInstances();

    /**
     * @return how many instances are currently registered
     */
    int size();
}
