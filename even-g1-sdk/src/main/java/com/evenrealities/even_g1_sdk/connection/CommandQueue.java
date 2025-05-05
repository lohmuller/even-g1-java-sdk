/**
 * CommandQueue is responsible for managing pending commands for the Even Realities device,
 * maintaining separate queues for LEFT and RIGHT connections.
 *
 * It ensures that only one command per side is active at a time, preventing overlapping
 * commands with similar response headers, which could cause unexpected behavior during
 * response parsing.
 *
 * The main goal is to avoid command collisions by validating whether a command can safely
 * be added to the queue (`isAvailable`) before sending it, based on its expected response signature.
 */

package com.evenrealities.even_g1_sdk.connection;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import com.evenrealities.even_g1_sdk.api.EvenOsApi;
import com.evenrealities.even_g1_sdk.api.EvenOsCommand;

public class CommandQueue {

    private final List<EvenOsCommand> leftQueue = new CopyOnWriteArrayList<>();
    private final List<EvenOsCommand> rightQueue = new CopyOnWriteArrayList<>();

    public void add(EvenOsCommand command) {
        if (command.sides == EvenOsApi.Sides.LEFT || command.sides == EvenOsApi.Sides.BOTH) {
            leftQueue.add(command);
        }
        if (command.sides == EvenOsApi.Sides.RIGHT || command.sides == EvenOsApi.Sides.BOTH) {
            rightQueue.add(command);
        }
    }

    public void remove(EvenOsCommand command, String side) {
        if (side.equals("LEFT") || side.equals("BOTH")) {
            leftQueue.removeIf(entry -> entry == command);
        }
        if (side.equals("RIGHT") || side.equals("BOTH")) {
            rightQueue.removeIf(entry -> entry == command);
        }
    }

    public boolean isAvailable(EvenOsCommand command) {
        if (command.sides == EvenOsApi.Sides.LEFT || command.sides == EvenOsApi.Sides.BOTH) {
            for (EvenOsCommand leftQueueCommand : leftQueue) {
                if (hasByteConflict(command.responseHeader, leftQueueCommand.responseHeader)) {
                    return false;
                }
            }
        }
        if (command.sides == EvenOsApi.Sides.RIGHT || command.sides == EvenOsApi.Sides.BOTH) {
            for (EvenOsCommand rightQueueCommand : rightQueue) {
                if (hasByteConflict(command.responseHeader, rightQueueCommand.responseHeader)) {
                    return false;
                }
            }
        }
        return true; 
    }

    private boolean hasByteConflict(byte[] a, byte[] b) {
        int minLen = Math.min(a.length, b.length);
        for (int i = 0; i < minLen; i++) {
            if (a[i] != b[i]) {
                return false;
            }
        }
        return true;
    }

    public EvenOsCommand[] findMatching(byte[] data, EvenOsApi.Sides side) {
        List<EvenOsCommand> matchingCommands = new ArrayList<>();
        List<EvenOsCommand> queueToCheck = new ArrayList<>();

        if (side == EvenOsApi.Sides.LEFT || side == EvenOsApi.Sides.BOTH) {
            queueToCheck.addAll(leftQueue);
        }
        if (side == EvenOsApi.Sides.RIGHT || side == EvenOsApi.Sides.BOTH) {
            queueToCheck.addAll(rightQueue);
        }

        for (EvenOsCommand command : queueToCheck) {
            byte[] header = command.responseHeader;
            if (header != null && data != null && data.length >= header.length) {
                boolean matches = true;
                for (int i = 0; i < header.length; i++) {
                    if (data[i] != header[i]) {
                        matches = false;
                        break;
                    }
                }
                if (matches) {
                    matchingCommands.add(command);
                }
            }
        }
        return matchingCommands.toArray(new EvenOsCommand[0]);
    }
    
}
