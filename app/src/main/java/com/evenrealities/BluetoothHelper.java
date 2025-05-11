// BluetoothHelper.java
package com.evenrealities;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.evenrealities.even_g1_sdk.api.EvenOsApi;
import com.evenrealities.even_g1_sdk.connection.ConnectionManager;

import java.util.*;

public class BluetoothHelper {

    
    public enum PairingStatus {
        /* -------- pairing (bond) -------- */
        NOT_BONDED,        // nunca pareado
        BONDING,           // createBond em progresso
        BONDED,             // pareado OK
        BONDING_FAILED,    // falha no bonding
        ERROR              // falha em qualquer passo
    }
    public enum ConnectionStatus {
        /* -------- conexão GATT -------- */
        DISCONNECTED,      // desconectado (mas já BONDED)
        CONNECTING,        // connectGatt() em andamento
        CONNECTED,         // GATT aberto, antes de init()
        INITIALIZED,       // serviços + notificações OK

        /* -------- erro -------- */
        ERROR              // falha em qualquer passo
    }

    public interface BluetoothCallback {
        void onLog(String tag, String message);
        void onPairingStatusUpdate(EvenOsApi.Sides side, PairingStatus status);
        void onDevicesBonded(BluetoothDevice left, BluetoothDevice right);
    }

    private static final int REQUEST_PERMISSIONS = 1;
    private static final String TAG = "BluetoothHelper";

    private static BluetoothAdapter bluetoothAdapter;
    private static BluetoothLeScanner bleScanner;
    private static BluetoothCallback callback;
    private static final Handler handler = new Handler(Looper.getMainLooper());

    private static BluetoothDevice leftDevice;
    private static BluetoothDevice rightDevice;

    private static final Map<String, BluetoothDevice> foundDevices = new HashMap<>();

    public static void init(Activity activity, BluetoothCallback cb) {
        callback = cb;
        callback.onLog(TAG, "Initializing BluetoothHelper");
        checkPermissions(activity);
    }

    private static void checkPermissions(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!hasPermission(activity, Manifest.permission.BLUETOOTH_CONNECT) ||
                !hasPermission(activity, Manifest.permission.BLUETOOTH_SCAN)) {
                ActivityCompat.requestPermissions(activity, new String[] {
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN
                }, REQUEST_PERMISSIONS);
                return;
            }
        } else if (!hasPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION)) {
            ActivityCompat.requestPermissions(activity, new String[] {
                    Manifest.permission.ACCESS_FINE_LOCATION
            }, REQUEST_PERMISSIONS);
            return;
        }
        initBluetooth(activity);
    }

    private static boolean hasPermission(Activity activity, String permission) {
        return ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED;
    }

    private static void initBluetooth(Activity activity) {
        bluetoothAdapter = ((BluetoothManager) activity.getSystemService(android.content.Context.BLUETOOTH_SERVICE)).getAdapter();
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            callback.onLog(TAG, "Bluetooth is not enabled");
            return;
        }
        bleScanner = bluetoothAdapter.getBluetoothLeScanner();
        callback.onLog(TAG, "Bluetooth initialized");
        checkPairedDevices();
    }

    private static void checkPairedDevices() {
        callback.onLog(TAG, "Checking paired devices...");
        Set<BluetoothDevice> bonded = bluetoothAdapter.getBondedDevices();
        for (BluetoothDevice device : bonded) {
            if (ConnectionManager.isLeftDevice(device)) {
                leftDevice = device;
                callback.onPairingStatusUpdate(EvenOsApi.Sides.LEFT, PairingStatus.BONDED);
            } else if (ConnectionManager.isRightDevice(device)) {
                rightDevice = device;
                callback.onPairingStatusUpdate(EvenOsApi.Sides.RIGHT, PairingStatus.BONDED);
            }
        }

        if (leftDevice != null && rightDevice != null) {
            callback.onLog(TAG, "Both devices are bonded");
            callback.onDevicesBonded(leftDevice, rightDevice);
        } else {
            if (leftDevice == null)
                callback.onPairingStatusUpdate(EvenOsApi.Sides.LEFT, PairingStatus.NOT_BONDED);
            if (rightDevice == null)
                callback.onPairingStatusUpdate(EvenOsApi.Sides.RIGHT, PairingStatus.NOT_BONDED);
            startScan();
        }
    }

    private static void startScan() {
        if (bleScanner == null) {
            callback.onLog(TAG, "BLE Scanner is null");
            return;
        }
        callback.onLog(TAG, "Starting scan...");
        foundDevices.clear();
        bleScanner.startScan(scanCallback);
        handler.postDelayed(() -> {
            bleScanner.stopScan(scanCallback);
            callback.onLog(TAG, "Scan finished");
        }, 5000);
    }

    private static final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            if (!foundDevices.containsKey(device.getAddress())) {
                foundDevices.put(device.getAddress(), device);
                String name = device.getName() != null ? device.getName() : "(no name)";
                callback.onLog(TAG, "Found: " + name);

                if (ConnectionManager.isLeftDevice(device)) {
                    callback.onPairingStatusUpdate(EvenOsApi.Sides.LEFT, PairingStatus.BONDING);
                    device.createBond();
                } else if (ConnectionManager.isRightDevice(device)) {
                    callback.onPairingStatusUpdate(EvenOsApi.Sides.RIGHT, PairingStatus.BONDING);
                    device.createBond();
                }
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            callback.onLog(TAG, "Scan failed: " + errorCode);
        }
    };
}
