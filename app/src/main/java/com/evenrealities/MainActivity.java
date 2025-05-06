package com.evenrealities;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.HashMap;
import java.util.List;
import com.evenrealities.even_g1_sdk.connection.ConnectionManager;
import com.evenrealities.even_g1_sdk.api.EvenOsApi;
import com.evenrealities.even_g1_sdk.api.EvenOs_1_5_0;
import android.util.Log;
import java.util.function.BiConsumer;

public class MainActivity extends Activity {
    private static final int REQUEST_PERMISSIONS = 1;
    private TextView logTextView;
    private LinearLayout devicesLayout;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bleScanner;
    private Handler handler = new Handler();
    private HashMap<String, BluetoothDevice> foundDevices = new HashMap<>();
    private Button retryButton = null;
    private LinearLayout root;
    private BluetoothDevice leftDevice = null;
    private BluetoothDevice rightDevice = null;
    private ConnectionManager connectionManager = null;
    
    private static final String TAG = "EVEN_G1_ConnectionManager";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        Button scanButton = new Button(this);
        scanButton.setText("Scan for BLE devices");
        root.addView(scanButton);
        logTextView = new TextView(this);
        ScrollView logScroll = new ScrollView(this);
        logScroll.addView(logTextView);
        root.addView(logScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        devicesLayout = new LinearLayout(this);
        devicesLayout.setOrientation(LinearLayout.VERTICAL);
        root.addView(devicesLayout);
        setContentView(root);
        scanButton.setOnClickListener(v -> startScan());

        appendLog("App started");

        //@TODO change this to a retry logic
        logConnectedDevices();
        checkPermissions();
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[] {
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN
                }, REQUEST_PERMISSIONS);
                return;
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[] {
                        Manifest.permission.ACCESS_FINE_LOCATION
                }, REQUEST_PERMISSIONS);
                return;
            }
        }
        initBluetooth();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            boolean granted = true;
            StringBuilder denied = new StringBuilder();
            for (int i = 0; i < grantResults.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    granted = false;
                    denied.append(permissions[i]).append("\n");
                }
            }
            if (granted) {
                appendLog("Permissions granted");
                removeRetryPermissionsButton();
                initBluetooth();
            } else {
                appendLog("Permissions denied:\n" + denied.toString());
                showRetryPermissionsButton();
                if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.BLUETOOTH_SCAN)) {
                    appendLog("Permissão negada permanentemente. Vá nas configurações do app para ativar.");
                    showOpenSettingsButton();
                }
            }
        }
    }

    private void initBluetooth() {
        BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = manager.getAdapter();
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            appendLog("Bluetooth is not enabled!");
            return;
        }
        bleScanner = bluetoothAdapter.getBluetoothLeScanner();
        appendLog("Bluetooth ready");
    }

    private void logConnectedDevices() {
        BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        boolean anyConnected = false;
        if (manager != null) {
            for (BluetoothDevice device : manager.getConnectedDevices(BluetoothProfile.GATT)) {
                String name = device.getName() != null ? device.getName() : "(no name)";
                appendLog("CONNECTED: " + name + " [" + device.getAddress() + "]");
                anyConnected = true;
            }
        }
        if (!anyConnected) {
            appendLog("Nenhum dispositivo BLE está conectado no momento.");
        }
        // Logar dispositivos pareados
        if (adapter != null) {
            boolean anyPaired = false;
            BluetoothDevice leftDevice = null;
            BluetoothDevice rightDevice = null;
            for (BluetoothDevice device : adapter.getBondedDevices()) {
                String name = device.getName() != null ? device.getName() : "(no name)";
                if (name.startsWith("Even G1_81_L_")) {
                    appendLog("PAIRED: " + name + " [" + device.getAddress() + "]");
                    anyPaired = true;
                    leftDevice = device;
                } else if (name.startsWith("Even G1_81_R_")) {
                    appendLog("PAIRED: " + name + " [" + device.getAddress() + "]");
                    anyPaired = true;
                    rightDevice = device;
                }
            }

            if (leftDevice != null && rightDevice != null) {
                connectionManager = new ConnectionManager(getApplicationContext(), leftDevice, rightDevice);
                EvenOsApi evenOsApi = new EvenOs_1_5_0();
                connectionManager.addResponseListener(evenOsApi.onBlePairedSuccess(), (data, side) -> {
                    appendLog("BLE paired successfully "+side.name());
                });
                connectionManager.addResponseListener(evenOsApi.onCaseBattery(), (data, side) -> {
                    appendLog("Case battery: "+data);
                });
                connectionManager.addResponseListener(evenOsApi.onCaseClosed(), (data, side) -> {
                    appendLog("Case closed "+side.name());
                });
                connectionManager.addResponseListener(evenOsApi.onCaseCharging(), (data, side) -> {
                    appendLog("Case charging "+side.name());
                });
                connectionManager.addResponseListener(evenOsApi.onCaseOpen(), (data, side) -> {
                    appendLog("Case open "+side.name());
                });
                connectionManager.addResponseListener(evenOsApi.onGlassesBattery(), (data, side) -> {
                    appendLog("Glasses battery: "+data);
                });
                
            }
            if (!anyPaired) {
                appendLog("No paired devices found");
            }
        }
    }

    private void startScan() {
        if (bleScanner == null) {
            appendLog("BLE scanner not available");
            return;
        }
        appendLog("Starting BLE scan...");
        foundDevices.clear();
        devicesLayout.removeAllViews();
        bleScanner.startScan(scanCallback);
        handler.postDelayed(() -> {
            bleScanner.stopScan(scanCallback);
            appendLog("Scan finished");
            appendLog("Total devices found: " + foundDevices.size());
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            List<BluetoothDevice> connectedDevices = manager != null ? manager.getConnectedDevices(BluetoothProfile.GATT) : new java.util.ArrayList<>();
            if (adapter != null) {
                for (BluetoothDevice device : foundDevices.values()) {
                    boolean paired = adapter.getBondedDevices().contains(device);
                    boolean connected = connectedDevices.contains(device);
                    String name = device.getName() != null ? device.getName() : "(no name)";
                    appendLog(name + " [" + device.getAddress() + "] is " + (paired ? "PAIRED" : "NOT paired") + (connected ? ", CONNECTED" : ""));
                }
            }
        }, 5000);
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            String address = device.getAddress();
            if (!foundDevices.containsKey(address)) {
                foundDevices.put(address, device);
                String name = device.getName() != null ? device.getName() : "(no name)";
                BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
                boolean paired = adapter != null && adapter.getBondedDevices().contains(device);
                List<BluetoothDevice> connectedDevices = manager != null ? manager.getConnectedDevices(BluetoothProfile.GATT) : new java.util.ArrayList<>();
                boolean connected = connectedDevices.contains(device);
                appendLog("Found: " + name + " [" + address + "]");
                Button btn = new Button(MainActivity.this);
                btn.setText(name + "\n" + address + (paired ? "\n(PAIRED)" : "") + (connected ? "\n(CONNECTED)" : ""));

                // Seleção automática pelo nome
                if (name.startsWith("EVEN_G1_81_L_") && leftDevice == null) {
                    leftDevice = device;
                    appendLog("Selecionado automaticamente como LEFT: " + name + " [" + address + "]");
                } else if (name.startsWith("EVEN_G1_81_R_") && rightDevice == null) {
                    rightDevice = device;
                    appendLog("Selecionado automaticamente como RIGHT: " + name + " [" + address + "]");
                }

                btn.setOnClickListener(v -> {
                    appendLog("Manual: " + name + " [" + address + "]");
                });
                devicesLayout.addView(btn);
            }
        }
        @Override
        public void onScanFailed(int errorCode) {
            appendLog("Scan failed: " + errorCode);
        }
    };

    private void appendLog(String message) {
        Log.d(TAG, message);
        runOnUiThread(() -> {
            logTextView.append(message + "\n");
        });
    }

    private void showRetryPermissionsButton() {
        runOnUiThread(() -> {
            if (retryButton == null) {
                retryButton = new Button(this);
                retryButton.setText("Try permissions again");
                retryButton.setOnClickListener(v -> {
                    removeRetryPermissionsButton();
                    checkPermissions();
                });
                root.addView(retryButton);
            }
        });
    }

    private void removeRetryPermissionsButton() {
        runOnUiThread(() -> {
            if (retryButton != null) {
                root.removeView(retryButton);
                retryButton = null;
            }
        });
    }

    private void showOpenSettingsButton() {
        runOnUiThread(() -> {
            Button settingsButton = new Button(this);
            settingsButton.setText("Abrir configurações do app");
            settingsButton.setOnClickListener(v -> {
                Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            });
            root.addView(settingsButton);
        });
    }
} 