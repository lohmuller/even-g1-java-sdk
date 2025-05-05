package com.evenrealities.even_g1_sdk.api;

/**
 * Interface for handling EvenOS events
 * @param <T> The type of data returned by the event
 */
public interface EvenOsEventListener<T> {
    /**
     * Check if the event matches the given data
     * @param data The data to check
     * @param side The side the event came from
     * @return true if the event matches
     */
    boolean matches(byte[] data, EvenOsApi.Sides side);

    /**
     * Parse the event data
     * @param data The data to parse
     * @param side The side the event came from
     * @return The parsed data
     */
    T parse(byte[] data, EvenOsApi.Sides side);
} 