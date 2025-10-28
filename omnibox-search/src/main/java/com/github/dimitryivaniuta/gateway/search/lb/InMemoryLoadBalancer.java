package com.github.dimitryivaniuta.gateway.search.lb;

import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe in-memory LoadBalancer implementation.
 * Guarantees:
 *  - preserves insertion order (LinkedHashSet)
 *  - enforces uniqueness
 *  - enforces max capacity = 10
 */
public class InMemoryLoadBalancer implements LoadBalancer {
    public static final int MAX_CAPACITY = 10;
    // LinkedHashSet -> predictable iteration order + uniqueness.
    private final Set<String> addresses = new LinkedHashSet<>();
    // ReadWriteLock -> concurrent reads, exclusive writes.
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);
    @Override
    public void register(String address) {
        if (!StringUtils.hasText(address)) {
            throw new IllegalArgumentException("address must not be null/blank");
        }
        lock.writeLock().lock();
        try {
            if (addresses.contains(address)) {
                throw new DuplicateAddressException(address);
            }
            if (addresses.size() >= MAX_CAPACITY) {
                throw new CapacityExceededException(MAX_CAPACITY);
            }
            addresses.add(address);
        } finally { lock.writeLock().unlock(); }
    }
    @Override
    public List<String> getInstances() {
        lock.readLock().lock();
        try {
            // snapshot copy -> caller cannot mutate our internal state
            return List.copyOf(addresses);
        } finally { lock.readLock().unlock(); }
    }
    @Override
    public int size() {
        lock.readLock().lock();
        try {
            return addresses.size();
        } finally { lock.readLock().unlock(); }
    }
}
