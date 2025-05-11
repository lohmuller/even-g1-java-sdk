package com.evenrealities;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.util.Log;
import android.widget.*;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.evenrealities.even_g1_sdk.api.*;
import com.evenrealities.even_g1_sdk.connection.ConnectionManager;

import java.util.*;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_PERMISSIONS = 1;
    private static final String TAG = "EVEN_G1_Debug";

    private TextView logTextView;
    private ScrollView scrollView;
    private LinearLayout devicesLayout, root;
    private Button retryButton;

    // Status panel views
    private TextView bluetoothLeftStatus;
    private TextView bluetoothRightStatus;
    private TextView caseStatus;
    private TextView glassesInCaseStatus;
    private TextView caseBatteryStatus;
    private TextView glassesBatteryStatus;
    private TextView caseChargingStatus;
    private TextView glassesChargingStatus;
    private TextView heartbeatStatus;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bleScanner;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final HashMap<String, BluetoothDevice> foundDevices = new HashMap<>();
    private BluetoothDevice leftDevice, rightDevice;
    private ConnectionManager connectionManager;
    private EvenOsApi api;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupUI();
        appendLog("App started");
        checkPermissions();
    }

    private void setupUI() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        setContentView(root);

        // Add status panel
        View statusPanel = getLayoutInflater().inflate(R.layout.status_panel, root, false);
        root.addView(statusPanel);

        // Initialize status views
        bluetoothLeftStatus = statusPanel.findViewById(R.id.bluetoothLeftStatus);
        bluetoothRightStatus = statusPanel.findViewById(R.id.bluetoothRightStatus);
        caseStatus = statusPanel.findViewById(R.id.caseStatus);
        glassesInCaseStatus = statusPanel.findViewById(R.id.glassesInCaseStatus);
        caseBatteryStatus = statusPanel.findViewById(R.id.caseBatteryStatus);
        glassesBatteryStatus = statusPanel.findViewById(R.id.glassesBatteryStatus);
        caseChargingStatus = statusPanel.findViewById(R.id.caseChargingStatus);
        glassesChargingStatus = statusPanel.findViewById(R.id.glassesChargingStatus);
        heartbeatStatus = statusPanel.findViewById(R.id.heartbeatStatus);

        // Add buttons section
        LinearLayout buttonsContainer = new LinearLayout(this);
        buttonsContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(buttonsContainer);

        // Grid of buttons (3 rows x 3 columns)
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.addView(createButton("Repair", v -> startScan())); //@TODO change to repair 
        row1.addView(createButton("Reconnect", v -> reconnectDevices()));
        row1.addView(createButton("Clear Log", v -> logTextView.setText("")));
        buttonsContainer.addView(row1);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.addView(createButton("SendCmd1", v -> sendCommand("initialize", api.initialize())));
        row2.addView(createButton("SendCmd2", v -> sendCommand("sendText", api.sendText("Hello, World!"))));
        row2.addView(createButton("SendCmd3", v -> sendCommand("getUsageInfo", api.getUsageInfo())));
        buttonsContainer.addView(row2);

        LinearLayout row3 = new LinearLayout(this);
        row3.setOrientation(LinearLayout.HORIZONTAL);
        row3.addView(createButton("SendCmd4", v -> sendCommand("getFirmwareInfo", api.getFirmwareInfo())));
        row3.addView(createButton("SendCmd5", v -> sendCommand("getBatteryInfo", api.getBatteryInfo(EvenOsApi.Sides.BOTH))));
        row3.addView(createButton("SendCmd6", v -> sendCommand("getDeviceUptime", api.getDeviceUptime())));
        buttonsContainer.addView(row3);

        // Add log section
        scrollView = new ScrollView(this);
        logTextView = new TextView(this);
        logTextView.setPadding(16, 16, 16, 16);
        scrollView.addView(logTextView);
        root.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        devicesLayout = new LinearLayout(this);
        devicesLayout.setOrientation(LinearLayout.VERTICAL);
        root.addView(devicesLayout);
    }

    private Button createButton(String text, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(4, 4, 4, 4);
        button.setLayoutParams(params);
        return button;
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT) ||
                !hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
                ActivityCompat.requestPermissions(this, new String[] {
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN
                }, REQUEST_PERMISSIONS);
                return;
            }
        } else if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            ActivityCompat.requestPermissions(this, new String[] {
                    Manifest.permission.ACCESS_FINE_LOCATION
            }, REQUEST_PERMISSIONS);
            return;
        }
        initBluetooth();
    }

    private boolean hasPermission(String permission) {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            if (Arrays.stream(grantResults).allMatch(p -> p == PackageManager.PERMISSION_GRANTED)) {
                appendLog("Permissions granted");
                removeRetryPermissionsButton();
                initBluetooth();
            } else {
                appendLog("Permissions denied");
                showRetryPermissionsButton();
                showOpenSettingsButton();
            }
        }
    }

    private void initBluetooth() {
        bluetoothAdapter = ((BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            appendLog("Bluetooth is not enabled!");
            return;
        }
        bleScanner = bluetoothAdapter.getBluetoothLeScanner();
        appendLog("Bluetooth ready");
        checkPairedDevices();
    }

    private void checkPairedDevices() {
        appendLog("Checking if the devices are already bonded...");
        Set<BluetoothDevice> bonded = bluetoothAdapter.getBondedDevices();
        for (BluetoothDevice device : bonded) {
            if (ConnectionManager.isLeftDevice(device)) {
                leftDevice = device;
                updateBluetoothStatus(EvenOsApi.Sides.LEFT, "Bonded (Not Connected)");
            } else if (ConnectionManager.isRightDevice(device)) {
                rightDevice = device;
                updateBluetoothStatus(EvenOsApi.Sides.RIGHT, "Bonded (Not Connected)");
            }
        }
        if (leftDevice != null && rightDevice != null) {
            appendLog("Success! Both devices bonded (L and R), starting connection...");
            connectDevices(leftDevice, rightDevice);
        } else {
            if (leftDevice == null) {
                updateBluetoothStatus(EvenOsApi.Sides.LEFT, "Not Bonded");
            }
            if (rightDevice == null) {
                updateBluetoothStatus(EvenOsApi.Sides.RIGHT, "Not Bonded");
            }
            appendLog("Bond both L and R devices before connecting.");
        }
    }

    private void connectDevices(BluetoothDevice left, BluetoothDevice right) {
        appendLog("Connecting to devices...");
        connectionManager = new ConnectionManager(this, left, right);
        api = new EvenOs_1_5_0();

        // Add listeners for status updates
        connectionManager.addResponseListener(api.onBlePairedSuccess(), (data, side) -> {
            appendLog("Bonded: " + side);
            updateBluetoothStatus(side, "Bonded (Connected)");
        });

        // Add connection state listener
        //connectionManager.addConnectionStateListener((connected, side) -> {
        //    String status = connected ? "Bonded (Connected)" : "Bonded (Disconnected)";
        //    updateBluetoothStatus(side, status);
        //    appendLog("Connection state changed: " + side + " - " + status);
        //});

        connectionManager.connect();

        // Update initial states
        updateBluetoothStatus(EvenOsApi.Sides.LEFT, "Bonded (Connecting...)");
        updateBluetoothStatus(EvenOsApi.Sides.RIGHT, "Bonded (Connecting...)");

        try {
            Object data = connectionManager.sendAndWait(api.initialize(), 1000);
            appendLog("Initialization got data: "+data);
        } catch (Exception e) {
            updateBluetoothStatus(EvenOsApi.Sides.LEFT, "Bonded (Error)");
            updateBluetoothStatus(EvenOsApi.Sides.RIGHT, "Bonded (Error)");
            appendLog("Error: " + e.getMessage());
        }

        /*
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (connectionManager.isInitialized()) {
                    try {
                        appendLog("Sending initialization command...");
                        connectionManager.sendCommand(api.initialize());
                        appendLog("Initialization sent");
                    } catch (Exception e) {
                        appendLog("Error: " + e.getMessage());
                        updateBluetoothStatus(EvenOsApi.Sides.LEFT, "Bonded (Error)");
                        updateBluetoothStatus(EvenOsApi.Sides.RIGHT, "Bonded (Error)");
                    }
                } else {
                    appendLog("Waiting for connection to initialize...");
                    handler.postDelayed(this, 1000);
                }
            }
        }, 1000);
        */
    }

    private void reconnectDevices() {
        if (connectionManager != null) {
            appendLog("Reconnecting...");
            connectionManager.reconnect();
        } else {
            appendLog("No connectionManager instance yet.");
        }
    }

    private void sendCommand(String commandName, EvenOsCommand command) {
        try {
            if (connectionManager == null) {
                appendLog("ConnectionManager is not ready.");
            }else {
                appendLog("Sending Command: " + commandName);    
                Object result = connectionManager.sendAndWait(command, 1000);    
                appendLog("Command sent: " + commandName + ", result: " + result);
            }
        } catch (Exception e) {
            appendLog("Error sending command: " + e.getMessage());
        }
    }

    private void startScan() {
        if (bleScanner == null) {
            appendLog("BLE scanner not available");
            return;
        }
        appendLog("Scanning BLE...");
        foundDevices.clear();
        devicesLayout.removeAllViews();
        bleScanner.startScan(scanCallback);
        handler.postDelayed(() -> bleScanner.stopScan(scanCallback), 5000);
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            if (!foundDevices.containsKey(device.getAddress())) {
                foundDevices.put(device.getAddress(), device);
                String name = device.getName() != null ? device.getName() : "(no name)";
                appendLog("Found: " + name);

                Button btn = new Button(MainActivity.this);
                btn.setText(name + "\n" + device.getAddress());
                btn.setOnClickListener(v -> startPairing(device));
                devicesLayout.addView(btn);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            appendLog("Scan failed: " + errorCode);
        }
    };

    private void startPairing(BluetoothDevice device) {
        if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
            appendLog("Bonding with: " + device.getName());
            device.createBond();
            // Update status to show bonding in progress
            if (device.getName().startsWith("Even G1_81_L_")) {
                updateBluetoothStatus(EvenOsApi.Sides.LEFT, "Bonding...");
            } else if (device.getName().startsWith("Even G1_81_R_")) {
                updateBluetoothStatus(EvenOsApi.Sides.RIGHT, "Bonding...");
            }
        } else {
            appendLog("Already bonded: " + device.getName());
        }
    }

    private void appendLog(String message) {
        Log.d(TAG, message);
        runOnUiThread(() -> {
            logTextView.append(message + "\n");
            scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
        });
    }

    private void showRetryPermissionsButton() {
        if (retryButton == null) {
            retryButton = new Button(this);
            retryButton.setText("Retry Permissions");
            retryButton.setOnClickListener(v -> {
                removeRetryPermissionsButton();
                checkPermissions();
            });
            root.addView(retryButton);
        }
    }

    private void removeRetryPermissionsButton() {
        if (retryButton != null) {
            root.removeView(retryButton);
            retryButton = null;
        }
    }

    private void showOpenSettingsButton() {
        Button btn = new Button(this);
        btn.setText("Open App Settings");
        btn.setOnClickListener(v -> {
            Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        });
        root.addView(btn);
    }

    private void updateBluetoothStatus(EvenOsApi.Sides side, String status) {
        runOnUiThread(() -> {
            if (side == EvenOsApi.Sides.LEFT) {
                bluetoothLeftStatus.setText(status);
            } else if (side == EvenOsApi.Sides.RIGHT) {
                bluetoothRightStatus.setText(status);
            }
        });
    }

    private void updateCaseBattery(int level) {
        runOnUiThread(() -> {
            caseBatteryStatus.setText(level + "%");
        });
    }

    private void updateGlassesBattery(int level) {
        runOnUiThread(() -> {
            glassesBatteryStatus.setText(level + "%");
        });
    }

    private void updateCaseCharging(boolean isCharging) {
        runOnUiThread(() -> {
            caseChargingStatus.setText(isCharging ? "Yes" : "No");
        });
    }

    private void updateCaseStatus(boolean isClosed) {
        runOnUiThread(() -> {
            caseStatus.setText(isClosed ? "Closed" : "Open");
        });
    }

    private void updateHeartbeat(int seq) {
        runOnUiThread(() -> {
            heartbeatStatus.setText(String.valueOf(seq));
        });
    }
}
