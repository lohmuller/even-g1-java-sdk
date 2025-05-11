// MainActivity.java
package com.evenrealities;

import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;
import com.evenrealities.even_g1_sdk.api.*;
import com.evenrealities.even_g1_sdk.connection.ConnectionManager;
import com.evenrealities.even_g1_sdk.connection.Connection;

/**
 * Main Activity for the Even Realities G1 SDK Demo App.
 * This app demonstrates how to connect to and communicate with Even Realities G1 smart glasses.
 * 
 * Key features:
 * - Bluetooth device discovery and bonding
 * - BLE connection management
 * - Command sending and response handling
 * - Connection state monitoring
 * - Automatic reconnection on timeout
 */
public class MainActivity extends AppCompatActivity {
    private static final String TAG = "EVEN_G1_Debug";
    
    // Connection management
    private EvenOsApi api;
    private ConnectionManager connectionManager;
    private Handler connectionTimeoutHandler = new Handler(Looper.getMainLooper());
    private static final int CONNECTION_TIMEOUT_MS = 5000; // 5 seconds
    private boolean isConnectionError = false;

    /**
     * Called when the activity is first created.
     * Sets up the UI and initializes Bluetooth connection.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UIHelper.appendLog(TAG, "Starting application...");
        UIHelper.setupUI(this);

        // Main command buttons
        UIHelper.addButtonRow(this,
            UIHelper.createButton(this, "Init", v -> onSendInitialize()),
            UIHelper.createButton(this, "SendText", v -> onSendText()),
            UIHelper.createButton(this, "ClearLog", v -> UIHelper.clearLog())
        );

        // Connection management buttons
        UIHelper.addButtonRow(this,
            UIHelper.createButton(this, "Reconnect", v -> onReconnect())
        );

        // Initialize Bluetooth and set up connection callbacks
        UIHelper.appendLog(TAG, "Initializing Bluetooth...");
        BluetoothHelper.init(this, new BluetoothHelper.BluetoothCallback() {
            @Override
            public void onLog(String tag, String message) {
                UIHelper.appendLog(tag, message);
            }

            @Override
            public void onStatusUpdate(EvenOsApi.Sides side, String status) {
                UIHelper.appendLog(TAG, "Status update - " + side + ": " + status);
                UIHelper.updateBluetoothStatus(side, status);
            }

            @Override
            public void onDevicesBonded(BluetoothDevice left, BluetoothDevice right) {
                UIHelper.appendLog(TAG, "Devices bonded. Starting connection process...");
                api = new EvenOs_1_5_0();
                connectionManager = new ConnectionManager(MainActivity.this, left, right);
                isConnectionError = false;

                // Monitor left device connection state
                connectionManager.getLeftConnection().setConnectionStateListener(state -> {
                    UIHelper.appendLog(TAG, "Left connection state changed: " + state);
                    if (state == Connection.ConnectionState.DISCONNECTED) {
                        UIHelper.updateBluetoothStatus(EvenOsApi.Sides.LEFT, "Bonded (Disconnected)");
                    } else if (state == Connection.ConnectionState.CONNECTING) {
                        UIHelper.updateBluetoothStatus(EvenOsApi.Sides.LEFT, "Bonded (Connecting...)");
                    } else if (state == Connection.ConnectionState.CONNECTED) {
                        UIHelper.updateBluetoothStatus(EvenOsApi.Sides.LEFT, "Bonded (Connected)");
                    }
                });

                // Monitor right device connection state
                connectionManager.getRightConnection().setConnectionStateListener(state -> {
                    UIHelper.appendLog(TAG, "Right connection state changed: " + state);
                    if (state == Connection.ConnectionState.DISCONNECTED) {
                        UIHelper.updateBluetoothStatus(EvenOsApi.Sides.RIGHT, "Bonded (Disconnected)");
                    } else if (state == Connection.ConnectionState.CONNECTING) {
                        UIHelper.updateBluetoothStatus(EvenOsApi.Sides.RIGHT, "Bonded (Connecting...)");
                    } else if (state == Connection.ConnectionState.CONNECTED) {
                        UIHelper.updateBluetoothStatus(EvenOsApi.Sides.RIGHT, "Bonded (Connected)");
                    }
                });

                // Handle successful connection
                connectionManager.addResponseListener(api.onBlePairedSuccess(), (data, side) -> {
                    UIHelper.appendLog(TAG, "Connection successful for " + side);
                    UIHelper.updateBluetoothStatus(side, "Bonded (Connected)");
                    connectionTimeoutHandler.removeCallbacks(connectionTimeoutRunnable);
                });

                // Start connection process
                UIHelper.appendLog(TAG, "Starting connection process...");
                connectionManager.connect();
                
                // Start connection timeout
                UIHelper.appendLog(TAG, "Starting connection timeout timer (" + CONNECTION_TIMEOUT_MS + "ms)");
                connectionTimeoutHandler.postDelayed(connectionTimeoutRunnable, CONNECTION_TIMEOUT_MS);
            }
        });
    }

    /**
     * Handles connection timeout.
     * If connection fails within the timeout period, marks as error and attempts to reconnect.
     */
    private final Runnable connectionTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            if (connectionManager != null && !isConnectionError) {
                UIHelper.appendLog(TAG, "Connection timeout reached, attempting to reconnect...");
                isConnectionError = true;
                connectionManager.destroy();
                connectionManager.connect();
                
                // Update status to show timeout error
                UIHelper.updateBluetoothStatus(EvenOsApi.Sides.LEFT, "Bonded (Timeout Error)");
                UIHelper.updateBluetoothStatus(EvenOsApi.Sides.RIGHT, "Bonded (Timeout Error)");
            }
        }
    };

    /**
     * Attempts to reconnect to the devices.
     * This is a softer reconnection that tries to maintain the current connection state.
     */
    private void onReconnect() {
        if (connectionManager != null) {
            UIHelper.appendLog(TAG, "Manual reconnection requested...");
            isConnectionError = false;
            connectionManager.reconnect();
            
            // Start the connection timeout
            UIHelper.appendLog(TAG, "Starting reconnection timeout timer (" + CONNECTION_TIMEOUT_MS + "ms)");
            connectionTimeoutHandler.postDelayed(connectionTimeoutRunnable, CONNECTION_TIMEOUT_MS);
        } else {
            UIHelper.appendLog(TAG, "Cannot reconnect: ConnectionManager is null");
        }
    }

    /**
     * Sends the initialization command to the glasses.
     * This is required after connection is established.
     */
    private void onSendInitialize() {
        UIHelper.appendLog(TAG, "Sending initialization command...");
        sendCommand("initialize", api.initialize());
    }

    /**
     * Sends a test text message to the glasses.
     * This is a simple command to verify communication is working.
     */
    private void onSendText() {
        UIHelper.appendLog(TAG, "Sending test text message...");
        sendCommand("sendText", api.sendText("Hello, World!"));
    }

    /**
     * Generic method to send commands to the glasses.
     * Handles command sending, response waiting, and error handling.
     * 
     * @param name Name of the command for logging
     * @param command The command to send
     */
    private void sendCommand(String name, EvenOsCommand command) {
        if (connectionManager == null) {
            UIHelper.appendLog(TAG, "Cannot send command: ConnectionManager not ready");
            return;
        }
        try {
            UIHelper.appendLog(TAG, "Sending command: " + name);
            Object result = connectionManager.sendAndWait(command, 1000);
            UIHelper.appendLog(TAG, "Command " + name + " completed with result: " + result);
        } catch (Exception e) {
            UIHelper.appendLog(TAG, "Error sending command " + name + ": " + e.getMessage());
        }
    }
}