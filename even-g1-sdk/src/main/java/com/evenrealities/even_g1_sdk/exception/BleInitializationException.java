package com.evenrealities.even_g1_sdk.exception;


/**
 * Exception thrown when the BLE initialization fails.
 * Wrong UUIDs, wrong device, etc.
 */
public class BleInitializationException extends RuntimeException {
    public BleInitializationException(String message) {
        super(message);
    }
}