package com.evenrealities.even_g1_sdk.api;

import java.util.function.Function;
import java.util.concurrent.CompletableFuture;
import com.evenrealities.even_g1_sdk.api.EvenOsApi;

public class EvenOsCommand<T> {

    public final byte[][] requestPackets;
    public final byte[] responseHeader;
    public final EvenOsApi.Sides sides;
    public final CompletableFuture<T> future = new CompletableFuture<>();

    public EvenOsCommand(byte[][] requestPackets, byte[] responseHeader, EvenOsApi.Sides sides) {
        this.requestPackets = requestPackets;
        this.responseHeader = responseHeader;
        this.sides = sides;
    }

    public EvenOsCommand(byte[] singleRequest, byte[] responseHeader, EvenOsApi.Sides sides) {
        this(new byte[][]{ singleRequest }, responseHeader, sides);
    }
}