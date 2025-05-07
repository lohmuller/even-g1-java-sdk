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

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.evenrealities.even_g1_sdk.api.*;
import com.evenrealities.even_g1_sdk.connection.ConnectionManager;

import java.util.*;

public class MainActivity extends Activity {
    private static final int REQUEST_PERMISSIONS = 1;
    private static final String TAG = "EVEN_G1_Debug";

    private TextView logTextView;
    private LinearLayout devicesLayout, root;
    private Button retryButton;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bleScanner;
    private final Handler handler = new Handler();

    private final HashMap<String, BluetoothDevice> foundDevices = new HashMap<>();
    private BluetoothDevice leftDevice, rightDevice;
    private ConnectionManager connectionManager;

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

        Button scanButton = new Button(this);
        scanButton.setText("Scan for BLE devices");
        scanButton.setOnClickListener(v -> startScan());
        root.addView(scanButton);

        logTextView = new TextView(this);
        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(logTextView);
        root.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        devicesLayout = new LinearLayout(this);
        devicesLayout.setOrientation(LinearLayout.VERTICAL);
        root.addView(devicesLayout);

        setContentView(root);
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT) ||
                !hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
                ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN
                }, REQUEST_PERMISSIONS);
                return;
            }
        } else if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            ActivityCompat.requestPermissions(this, new String[]{
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
        Set<BluetoothDevice> bonded = bluetoothAdapter.getBondedDevices();
        for (BluetoothDevice device : bonded) {
            String name = device.getName();
            if (name != null) {
                if (name.startsWith("Even G1_81_L_")) leftDevice = device;
                if (name.startsWith("Even G1_81_R_")) rightDevice = device;
            }
        }
        if (leftDevice != null && rightDevice != null) {
            appendLog("Success! Both devices paired (L and R), Starting connection...");
            connectDevices(leftDevice, rightDevice);
        } else {
            appendLog("Pair both L and R devices before connecting.");
        }
    }

    private void connectDevices(BluetoothDevice left, BluetoothDevice right) {
        appendLog("Connecting to devices...");
        connectionManager = new ConnectionManager(this, left, right);
        EvenOsApi api = new EvenOs_1_5_0();

        connectionManager.addResponseListener(api.onBlePairedSuccess(), (data, side) -> appendLog("OnResponse: Paired: " + side));
        connectionManager.addResponseListener(api.onCaseBattery(), (data, side) -> appendLog("OnResponse: Case battery: " + side + " " + data));
        connectionManager.addResponseListener(api.onGlassesBattery(), (data, side) -> appendLog("OnResponse: Glasses battery: " + side + " " + data));

        connectionManager.connect();

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
                    }
                } else {
                    appendLog("Waiting for connection to initialize...");
                    handler.postDelayed(this, 1000);
                }
            }
        }, 1000);
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
            appendLog("Pairing with: " + device.getName());
            device.createBond();
        } else {
            appendLog("Already paired: " + device.getName());
        }
    }

    private void appendLog(String message) {
        Log.d(TAG, message);
        runOnUiThread(() -> logTextView.append(message + "\n"));
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
}
