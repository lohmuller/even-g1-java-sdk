/**
 * ConnectionManager is responsible for orchestrating communication between the Even Realities smart glasses
 * and the application. It manages the left and right BLE (Bluetooth Low Energy) connections, handles
 * command dispatching, response listening, and provides an optional synchronous interface using futures.
 *
 * This class ensures that commands are sent to the correct device (left/right/both), manages a queue
 * to prevent overlapping commands with conflicting responses, and resolves responses using predefined
 * headers and response parsers.
 *
 * It also supports both asynchronous and synchronous command execution (via `sendCommand` and `sendAndWait`)
 * and delegates all BLE operations to the underlying `Connection` instances.
 *
 * Designed to serve as the main communication bridge for SDK-like integrations with Even Realities G1 (firmware 1.5.0),
 * with support for future enhancements like heartbeat monitoring, retries, or extended device status.
 */

package com.evenrealities.even_g1_sdk.connection;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import android.Manifest;
import android.content.Context;
import android.bluetooth.BluetoothDevice;
import android.util.Log;

import androidx.annotation.RequiresPermission;

import com.evenrealities.even_g1_sdk.api.EvenOsApi;
import com.evenrealities.even_g1_sdk.api.EvenOsCommand;
import com.evenrealities.even_g1_sdk.connection.ConnectionConfig;
import com.evenrealities.even_g1_sdk.connection.Connection;
import com.evenrealities.even_g1_sdk.connection.CommandQueue;
import com.evenrealities.even_g1_sdk.api.EvenOsEventListener;


public class ConnectionManager {

    private static final String TAG = "EVEN_G1_ConnectionManager";

    private final Connection leftConnection;
    private final Connection rightConnection;
    private EvenOsApi evenOsApi;
    private final int maxRetries = 3;

    private final CommandQueue commandQueue;
    private final List<ResponseHandler<?>> responseHandlers = new ArrayList<>();
    private final Map<EvenOsEventListener<?>, BiConsumer<?, EvenOsApi.Sides>> responseListeners = new HashMap<>();

    // UUIDs for UART service and characteristics
    private static final UUID UartServiceUuid = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");
    private static final UUID uartTxCharUuid = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e");
    private static final UUID uartRxCharUuid = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e");
    private static final UUID clientCharacteristicConfigUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private final Context context;

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public ConnectionManager(Context context, BluetoothDevice leftDevice, BluetoothDevice rightDevice) {
        this.context = context;
        int mtu = 512;
        ConnectionConfig config = new ConnectionConfig(UartServiceUuid, uartTxCharUuid, uartRxCharUuid, clientCharacteristicConfigUuid, mtu);
        this.leftConnection = new Connection(context, leftDevice, config);
        this.rightConnection = new Connection(context, rightDevice, config);
        this.commandQueue = new CommandQueue();

        this.init();
    }

    public void setEvenOsApi(EvenOsApi evenOsApi) {
        this.evenOsApi = evenOsApi;
    }

    public void init() {
        Log.i(TAG, "init: Setting up RX listeners for LEFT and RIGHT connections.");
        this.leftConnection.setOnRxDataListener((data) -> onDataReceived(data, EvenOsApi.Sides.LEFT));
        this.rightConnection.setOnRxDataListener((data) -> onDataReceived(data, EvenOsApi.Sides.RIGHT));
    }


    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public void destroy() {
        Log.i(TAG, "destroy: Disconnecting both connections.");
        this.leftConnection.disconnect();
        this.rightConnection.disconnect();
    }

    private void setupHeartbeat() {
        //@TODO: implement heartbeat response/reply handler?
    }

    /**
     * Envia um comando para o dispositivo e retorna a resposta
     * @param sendCommand
     * @return
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public <T> CompletableFuture<T> sendCommand(EvenOsCommand<T> sendCommand) {
        Log.d(TAG, "sendCommand: Attempting to send command: " + sendCommand + ", sides: " + sendCommand.sides);
        if (!this.commandQueue.isAvailable(sendCommand)) {
            Log.w(TAG, "sendCommand: Command not available (conflict in queue): " + sendCommand);
            //@TODO retry logic, create a sleep between retries
            return null;
        }
        for (byte[] packet : sendCommand.requestPackets) {
            this.commandQueue.add(sendCommand);
            Log.d(TAG, "sendCommand: Command added to queue: " + sendCommand);
        }
        if (sendCommand.sides == EvenOsApi.Sides.LEFT || sendCommand.sides == EvenOsApi.Sides.BOTH) {
            for (byte[] packet : sendCommand.requestPackets) {
                Log.d(TAG, "sendCommand: Sending packet to LEFT: " + Arrays.toString(packet));
                this.leftConnection.send(packet);
            }
        }
        if (sendCommand.sides == EvenOsApi.Sides.RIGHT || sendCommand.sides == EvenOsApi.Sides.BOTH) {
            for (byte[] packet : sendCommand.requestPackets) {
                Log.d(TAG, "sendCommand: Sending packet to RIGHT: " + Arrays.toString(packet));
                this.rightConnection.send(packet);
            }
        }

        Log.d(TAG, "sendCommand: Command sent: " + sendCommand);
        return sendCommand.future;
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public <T> T sendAndWait(EvenOsCommand<T> command, long timeoutMillis) throws Exception {
        Log.d(TAG, "sendAndWait: Sending command: " + command);
        CompletableFuture<T> future = sendCommand(command);
        Log.d(TAG, "sendAndWait: Waiting for command to complete: " + command);
        return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
    }

    public <T> void setOnDataReceived(EvenOsApi.Sides side, BiConsumer<byte[], EvenOsApi.Sides> handler) {
        if (side == EvenOsApi.Sides.LEFT) {
            this.leftConnection.setOnRxDataListener(data -> handler.accept(data, EvenOsApi.Sides.LEFT));
        } else if (side == EvenOsApi.Sides.RIGHT) {
            this.rightConnection.setOnRxDataListener(data -> handler.accept(data, EvenOsApi.Sides.RIGHT));
        }
    }

    public <T> void setOnResponse(EvenOsApi.Sides side, EvenOsEventListener<T> listener, BiConsumer<T, EvenOsApi.Sides> handler) {
        // Remove old entry if it exists
        responseHandlers.removeIf(entry ->
            entry.listener.equals(listener) && entry.side == side
        );

        // Add the new handler
        responseHandlers.add(new ResponseHandler<>(side, listener, handler));
    }

    /**
     * Add a response listener for a specific event.
     * @param listener The event listener to add.
     * @param handler The handler to call when the event occurs.
     */
    public void addResponseListener(EvenOsEventListener<?> listener, BiConsumer<?, EvenOsApi.Sides> handler) {
        responseListeners.put(listener, handler);
    }

    /**
     * Remove a response listener for a specific event.
     * @param listener The event listener to remove.
     */
    public void removeResponseListener(EvenOsEventListener<?> listener) {
        responseListeners.remove(listener);
    }

    private void onDataReceived(byte[] data, EvenOsApi.Sides side) {
        Log.d(TAG, "onDataReceived: Data received: " + Arrays.toString(data));
        boolean isUnknownCommand = true;
        EvenOsCommand[] matches = this.commandQueue.findMatching(data, side);
        for (EvenOsCommand matching : matches) {
            try {
                Log.d(TAG, "onDataReceived: Processing command: " + matching);
                Object result = matching.onDataReceived.apply(data);
                matching.future.complete(result);
            } catch (Exception e) {
                Log.e(TAG, "onDataReceived: Error processing command: " + matching, e);
                matching.future.completeExceptionally(e);
            }
            this.commandQueue.remove(matching, side.name());
        }
        
        // TODO: Padronizar uso de responseHandlers ou responseListeners
        for (Map.Entry<EvenOsEventListener<?>, BiConsumer<?, EvenOsApi.Sides>> entry : responseListeners.entrySet()) {
            EvenOsEventListener<?> listener = entry.getKey();
            if (listener.matches(data, side)) {
                isUnknownCommand = false;
                Object parsed = listener.parse(data, side);
                @SuppressWarnings("unchecked")
                BiConsumer<Object, EvenOsApi.Sides> handler = (BiConsumer<Object, EvenOsApi.Sides>) entry.getValue();
                handler.accept(parsed, side);
                break;
            }
        }

        if (matches.length == 0 && isUnknownCommand) {
            StringBuilder hex = new StringBuilder();
            for (byte b : data) hex.append(String.format("%02X ", b));
            Log.d(TAG, "onDataReceived: Unknown Command received on side " + side + ": [" + hex.toString().trim() + "]");
        }
    }
    
}

/**
 * Handle for command response
 */
class ResponseHandler<T> {
    public final EvenOsApi.Sides side;
    public final EvenOsEventListener<T> listener;
    public final BiConsumer<T, EvenOsApi.Sides> handler;
    public ResponseHandler(EvenOsApi.Sides side, EvenOsEventListener<T> listener, BiConsumer<T, EvenOsApi.Sides> handler) {
        this.side = side;
        this.listener = listener;
        this.handler = handler;
    }
}