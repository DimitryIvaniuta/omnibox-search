package com.github.dimitryivaniuta.gateway.search.lb;

/**
 * Thrown when trying to register an address that is already present.
 */
public class DuplicateAddressException extends RuntimeException {

    public DuplicateAddressException(String address) {
        super("Address already registered: " + address);
    }
}