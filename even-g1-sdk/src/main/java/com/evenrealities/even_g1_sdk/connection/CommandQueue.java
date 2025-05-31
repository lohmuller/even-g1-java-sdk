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

    /**
     * Adds a command to the queue.
     * 
     * @param command the command to add
     */
    public void add(EvenOsCommand command) {
        if (command.sides.matchesLeft()) {
            leftQueue.add(command);
        }
        if (command.sides.matchesRight()) {
            rightQueue.add(command);
        }
    }

    /**
     * Removes a command from the queue.
     * 
     * @param command the command to remove
     * @param side the side to remove the command from
     */
    public void remove(EvenOsCommand command, String side) {
        if (side.matchesLeft()) {
            leftQueue.removeIf(entry -> entry == command);
        }
        if (side.matchesRight()) {
            rightQueue.removeIf(entry -> entry == command);
        }
    }

    /**
     * Checks if a command can be added to the queue without causing a byte conflict.
     * 
     * @param command the command to check
     * @return true if the command can be added, false otherwise
     */
    public boolean isAvailable(EvenOsCommand command) {
        if (command.sides.matchesLeft()) {
            for (EvenOsCommand leftQueueCommand : leftQueue) {
                if (hasByteConflict(command.responseHeader, leftQueueCommand.responseHeader)) {
                    return false;
                }
            }
        }
        if (command.sides.matchesRight()) {
            for (EvenOsCommand rightQueueCommand : rightQueue) {
                if (hasByteConflict(command.responseHeader, rightQueueCommand.responseHeader)) {
                    return false;
                }
            }
        }
        return true; 
    }

    /**
     * Checks if there is a byte conflict (matching bytes) between two byte sequences.
     * It is used to prevent send a command that has a matching response header
     * with a command already in the queue.
     * 
     * @param a the first byte sequence
     * @param b the second byte sequence
     * @return true if there is a conflict, false otherwise
     */
    private boolean hasByteConflict(byte[] a, byte[] b) {
        int minLen = Math.min(a.length, b.length);
        for (int i = 0; i < minLen; i++) {
            if (a[i] != b[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Finds all commands in all queues (left and right) that have a matching response header with the given data.
     * 
     * @param data the byte sequence to compare with the response headers
     * @param side the side to check the queue for matching commands
     * @return an array of commands that have a matching response header with the given data
     */
    public List<EvenOsCommand> findMatching(byte[] data, EvenOsApi.Sides side) {
        if (data == null) return Collections.emptyList();

        List<EvenOsCommand> result = new ArrayList<>();

        if (side.matchesLeft()) {
            findMatchingInQueue(leftQueue, data, result);
        }
        if (side.matchesRight()) {
            findMatchingInQueue(rightQueue, data, result);
        }

        return result;
    }

    /**
     * Finds all commands in just one queue that have a matching response header with the given data.
     * 
     * @param queue the queue to search
     * @param data the byte sequence to compare with the response headers
     * @param result the list to store the matching commands
     */
    private void findMatchingInQueue(List<EvenOsCommand> queue, byte[] data, List<EvenOsCommand> result) {
        for (int i = 0; i < queue.size(); i++) {
            EvenOsCommand cmd = queue.get(i);
            byte[] header = cmd.responseHeader;

            if (header == null || data.length < header.length) continue;

            boolean match = true;
            for (int j = 0; j < header.length; j++) {
                if (data[j] != header[j]) {
                    match = false;
                    break;
                }
            }

            if (match) {
                result.add(cmd);
            }
        }
    }
}
