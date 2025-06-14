// MainActivity.java
package com.evenrealities;

import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.evenrealities.even_g1_sdk.api.*;
import com.evenrealities.even_g1_sdk.connection.ConnectionManager;
import com.evenrealities.even_g1_sdk.connection.Connection;
import android.content.DialogInterface;
import android.content.Intent;
import androidx.appcompat.app.AlertDialog;
import android.util.Log;
import java.io.PrintWriter;
import java.io.StringWriter;

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
    private static final String TAG = "MainActivity";
    
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
        
        // Set up global error handler
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            handleUncaughtException(thread, throwable);
        });


        try {
            // Create the UI
            UIHelper.setupUI(this);
            

            // Main command buttons
            UIHelper.addButtonRow(this,
                UIHelper.createButton(this, "Init", v -> sendInitialize()),
                UIHelper.createButton(this, "SendText", v -> sendText()),
                UIHelper.createButton(this, "ClearLog", v -> UIHelper.clearLog())
            );

            // Connection management buttons
            UIHelper.addButtonRow(this,
                UIHelper.createButton(this, "Reconnect", v -> onReconnect())
            );

            // Initialize Bluetooth and set up connection callbacks
            BluetoothHelper.init(this, new BluetoothHelper.BluetoothCallback() {
                @Override
                public void onLog(String tag, String message) {
                    UIHelper.appendLog(tag, message);
                }

                @Override
                public void onPairingStatusUpdate(EvenOsApi.Sides side, BluetoothHelper.PairingStatus status) {
                    UIHelper.setBlePairingStatus(side, status);
                }

                @Override
                public void onDevicesBonded(BluetoothDevice left, BluetoothDevice right) {
                    UIHelper.appendLog(TAG, "Devices bonded. Starting connection process...");
                    connectionManager = new ConnectionManager(MainActivity.this, left, right);
                    api = new EvenOsApi(connectionManager);
                    isConnectionError = false;

                    // Monitor left device connection state
                    connectionManager.getLeftConnection().setConnectionStateListener(state -> {
                        UIHelper.appendLog(TAG, "Left connection state changed: " + state);
                        if (state == Connection.ConnectionState.DISCONNECTED) {
                            UIHelper.setBleConnectionStatus(EvenOsApi.Sides.LEFT, BluetoothHelper.ConnectionStatus.DISCONNECTED);
                        } else if (state == Connection.ConnectionState.CONNECTING) {
                            UIHelper.setBleConnectionStatus(EvenOsApi.Sides.LEFT, BluetoothHelper.ConnectionStatus.CONNECTING);
                        } else if (state == Connection.ConnectionState.CONNECTED) {
                            UIHelper.setBleConnectionStatus(EvenOsApi.Sides.LEFT, BluetoothHelper.ConnectionStatus.CONNECTED);
                        } else if (state == Connection.ConnectionState.INITIALIZED) {
                            UIHelper.setBleConnectionStatus(EvenOsApi.Sides.LEFT, BluetoothHelper.ConnectionStatus.INITIALIZED);
                        }
                    });

                    // Monitor right device connection state
                    connectionManager.getRightConnection().setConnectionStateListener(state -> {
                        UIHelper.appendLog(TAG, "Right connection state changed: " + state);
                        if (state == Connection.ConnectionState.DISCONNECTED) {
                            UIHelper.setBleConnectionStatus(EvenOsApi.Sides.RIGHT, BluetoothHelper.ConnectionStatus.DISCONNECTED);
                        } else if (state == Connection.ConnectionState.CONNECTING) {
                            UIHelper.setBleConnectionStatus(EvenOsApi.Sides.RIGHT, BluetoothHelper.ConnectionStatus.CONNECTING);
                        } else if (state == Connection.ConnectionState.CONNECTED) {
                            UIHelper.setBleConnectionStatus(EvenOsApi.Sides.RIGHT, BluetoothHelper.ConnectionStatus.CONNECTED);
                        } else if (state == Connection.ConnectionState.INITIALIZED) {
                            UIHelper.setBleConnectionStatus(EvenOsApi.Sides.RIGHT, BluetoothHelper.ConnectionStatus.INITIALIZED);
                        }
                    });

                    // Handle successful connection
                    connectionManager.addResponseListener(api.onBlePairedSuccess(), (data, side) -> {
                        UIHelper.appendLog(TAG, "Connection successful for " + side);
                        UIHelper.setBleConnectionStatus(side, BluetoothHelper.ConnectionStatus.CONNECTED);
                        connectionTimeoutHandler.removeCallbacks(connectionTimeoutRunnable);
                    });

                    // Add response listener for all commands
                    connectionManager.addResponseListener(api.onAllResponses(), (data, side) -> {
                        android.util.Log.d("MainActivity", "Received response, creating log entry...");
                        if (data instanceof byte[]) {
                            byte[] response = (byte[]) data;
                            StringBuilder hexString = new StringBuilder();
                            for (byte b : response) {
                                hexString.append(String.format("%02X ", b));
                            }
                            android.util.Log.d("MainActivity", "Sending Response log with hex data");
                            UIHelper.appendLog("Response", "Response from " + side + ": " + hexString.toString().trim());
                        } else {
                            android.util.Log.d("MainActivity", "Sending Response log with object data");
                            UIHelper.appendLog("Response", "Response from " + side + ": " + data);
                        }
                    });

                    // Start connection process
                    UIHelper.appendLog(TAG, "Starting connection process...");
                    connectionManager.connect();
                    
                    // Start connection timeout
                    UIHelper.appendLog(TAG, "Starting connection timeout timer (" + CONNECTION_TIMEOUT_MS + "ms)");
                    connectionTimeoutHandler.postDelayed(connectionTimeoutRunnable, CONNECTION_TIMEOUT_MS);
                }
            });
        } catch (Exception e) {
            handleUncaughtException(Thread.currentThread(), e);
        }
    }

    private void handleUncaughtException(Thread thread, Throwable throwable) {
        // Get the stack trace
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        String stackTrace = sw.toString();

        // Log the error
        Log.e(TAG, "Uncaught exception: " + throwable.getMessage(), throwable);
        UIHelper.appendLog(TAG, "ERROR: " + throwable.getMessage());
        UIHelper.appendLog(TAG, "Stack trace: " + stackTrace);

        // Show error dialog
        runOnUiThread(() -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Application Error")
                   .setMessage("An error occurred: " + throwable.getMessage() + "\n\nWould you like to restart the app?")
                   .setPositiveButton("Restart", (dialog, which) -> {
                       // Restart the app
                       Intent intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
                       intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                       startActivity(intent);
                       finish();
                   })
                   .setNegativeButton("Close", (dialog, which) -> {
                       // Close the app
                       finish();
                   })
                   .setCancelable(false)
                   .show();
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
    private void sendInitialize() {
        UIHelper.appendLog(TAG, "Sending initialization command...");
        if (connectionManager == null) {
            UIHelper.appendLog(TAG, "Cannot send initialize command: ConnectionManager not ready");
            return;
        }
        try {
            boolean result = api.initialize();
            UIHelper.appendLog(TAG, "Initialize command completed with result: " + result);
        } catch (Exception e) {
            UIHelper.appendLog(TAG, "Error sending initialize command: " + e.getMessage());
        }
    }

    /**
     * Sends a test text message to the glasses.
     * This is a simple command to verify communication is working.
     */
    private void sendText() {
        UIHelper.appendLog(TAG, "Sending test text message...");
        api.sendText("Hello, World!");
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