package com.github.dimitryivaniuta.gateway.search.lb;

/**
 * Thrown when registry reached the maximum allowed capacity.
 */
public class CapacityExceededException extends RuntimeException {

    public CapacityExceededException(int maxCapacity) {
        super("Load balancer capacity exceeded. Max allowed: " + maxCapacity);
    }
}
