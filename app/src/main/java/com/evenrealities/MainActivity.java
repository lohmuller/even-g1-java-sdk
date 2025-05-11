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

public class MainActivity extends AppCompatActivity {
    private EvenOsApi api;
    private ConnectionManager connectionManager;
    private Handler connectionTimeoutHandler = new Handler(Looper.getMainLooper());
    private static final int CONNECTION_TIMEOUT_MS = 5000; // 5 seconds
    private boolean isConnectionError = false;

    private final Runnable connectionTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            if (connectionManager != null && !isConnectionError) {
                UIHelper.appendLog("BT", "Connection timeout, retrying...");
                isConnectionError = true;
                connectionManager.destroy();
                connectionManager.connect();
                
                // Update status to show timeout error
                UIHelper.updateBluetoothStatus(EvenOsApi.Sides.LEFT, "Bonded (Timeout Error)");
                UIHelper.updateBluetoothStatus(EvenOsApi.Sides.RIGHT, "Bonded (Timeout Error)");
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UIHelper.setupUI(this);

        // Definir os botões dinamicamente
        UIHelper.addButtonRow(this,
            UIHelper.createButton(this, "Init", v -> onSendInitialize()),
            UIHelper.createButton(this, "SendText", v -> onSendText()),
            UIHelper.createButton(this, "ClearLog", v -> UIHelper.clearLog())
        );

        BluetoothHelper.init(this, new BluetoothHelper.BluetoothCallback() {
            @Override
            public void onLog(String tag, String message) {
                UIHelper.appendLog(tag, message);
            }

            @Override
            public void onStatusUpdate(EvenOsApi.Sides side, String status) {
                UIHelper.updateBluetoothStatus(side, status);
            }

            @Override
            public void onDevicesBonded(BluetoothDevice left, BluetoothDevice right) {
                UIHelper.appendLog("FLOW", "Devices bonded. Connecting...");
                api = new EvenOs_1_5_0();
                connectionManager = new ConnectionManager(MainActivity.this, left, right);
                isConnectionError = false;

                // Set up connection state listeners
                connectionManager.getLeftConnection().setConnectionStateListener(state -> {
                    UIHelper.appendLog("BT", "Left connection state: " + state);
                    if (state == Connection.ConnectionState.DISCONNECTED) {
                        UIHelper.updateBluetoothStatus(EvenOsApi.Sides.LEFT, "Bonded (Disconnected)");
                    } else if (state == Connection.ConnectionState.CONNECTING) {
                        UIHelper.updateBluetoothStatus(EvenOsApi.Sides.LEFT, "Bonded (Connecting...)");
                    } else if (state == Connection.ConnectionState.CONNECTED) {
                        UIHelper.updateBluetoothStatus(EvenOsApi.Sides.LEFT, "Bonded (Connected)");
                    }
                });

                connectionManager.getRightConnection().setConnectionStateListener(state -> {
                    UIHelper.appendLog("BT", "Right connection state: " + state);
                    if (state == Connection.ConnectionState.DISCONNECTED) {
                        UIHelper.updateBluetoothStatus(EvenOsApi.Sides.RIGHT, "Bonded (Disconnected)");
                    } else if (state == Connection.ConnectionState.CONNECTING) {
                        UIHelper.updateBluetoothStatus(EvenOsApi.Sides.RIGHT, "Bonded (Connecting...)");
                    } else if (state == Connection.ConnectionState.CONNECTED) {
                        UIHelper.updateBluetoothStatus(EvenOsApi.Sides.RIGHT, "Bonded (Connected)");
                    }
                });

                connectionManager.addResponseListener(api.onBlePairedSuccess(), (data, side) -> {
                    UIHelper.appendLog("BT", "Bonded: " + side);
                    UIHelper.updateBluetoothStatus(side, "Bonded (Connected)");
                    connectionTimeoutHandler.removeCallbacks(connectionTimeoutRunnable);
                });

                // Start connection
                connectionManager.connect();
                
                // Start the connection timeout
                connectionTimeoutHandler.postDelayed(connectionTimeoutRunnable, CONNECTION_TIMEOUT_MS);
            }
        });
    }

    private void onSendInitialize() {
        sendCommand("initialize", api.initialize());
    }

    private void onSendText() {
        sendCommand("sendText", api.sendText("Hello, World!"));
    }

    private void sendCommand(String name, EvenOsCommand command) {
        if (connectionManager == null) {
            UIHelper.appendLog("CMD", "ConnectionManager not ready");
            return;
        }
        try {
            Object result = connectionManager.sendAndWait(command, 1000);
            UIHelper.appendLog("CMD", "Command " + name + " result: " + result);
        } catch (Exception e) {
            UIHelper.appendLog("CMD", "Error sending command: " + e.getMessage());
        }
    }
}