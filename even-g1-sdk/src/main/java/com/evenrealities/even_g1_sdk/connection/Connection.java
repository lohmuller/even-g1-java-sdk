/**
 * Connection is a generic BLE (Bluetooth Low Energy) manager for handling GATT-based communication
 * with a Bluetooth device, such as smart glasses.
 *
 * It abstracts the initialization, MTU negotiation, service discovery, and data transmission
 * via TX and RX characteristics. It also provides listener support for receiving data asynchronously.
 *
 * The class ensures the connection is properly initialized before usage, and automatically handles
 * reconnection, characteristic notification setup, and safe teardown.
 *
 * This structure allows external components (like ConnectionManager or G1Manager) to interface
 * with the BLE device in a simplified and robust way.
 */

package com.evenrealities.even_g1_sdk.connection;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;

import java.util.UUID;
import java.util.Arrays;

import com.evenrealities.even_g1_sdk.connection.ConnectionConfig;
import com.evenrealities.even_g1_sdk.exception.BleInitializationException;

public class Connection {

    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic txChar;
    private BluetoothGattCharacteristic rxChar;
    private OnRxDataListener rxDataListener;
    private boolean isInitialized = false;

    private final Context context;
    private final BluetoothDevice device;
    private final UUID uartServiceUuid;
    private final UUID uartTxCharUuid;
    private final UUID uartRxCharUuid;
    private final UUID clientCharacteristicConfigUuid;
    private final int mtu;

    private static final String TAG = "EVEN_G1_Connection";
    

    /**
     * Internal callback for the BluetoothGatt
     */
    private final BluetoothGattCallback internalCallback = new BluetoothGattCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            //@TODO: Add a listener for the connection state change (Disconnect (error?), Connect, etc)
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "onConnectionStateChange: Connected, requesting MTU " + mtu);
                gatt.requestMtu(mtu);
            } else {
                Log.w(TAG, "onConnectionStateChange: Disconnected");
                Connection.this.isInitialized = false;
            }
        }


        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "onMtuChanged: Success, discovering services");
                gatt.discoverServices(); 
            } else {
                Log.e(TAG, "onMtuChanged: Error negotiating MTU (status=" + status + ")");
                gatt.disconnect();
                gatt.close();
            }

        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "onServicesDiscovered: Success, initializing");
                new Handler(Looper.getMainLooper()).post(() -> init());               
            } else {
                Log.e(TAG, "onServicesDiscovered: Failed");
            }
        }

        @Override   
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            Log.d(TAG, "onCharacteristicChanged: uuid=" + characteristic.getUuid());
            if (rxChar != null && characteristic.getUuid().equals(rxChar.getUuid())) {
                byte[] data = characteristic.getValue();
                Log.d(TAG, "onCharacteristicChanged: Data received: " + Arrays.toString(data));
                if (rxDataListener != null) {
                    rxDataListener.onDataReceived(data);
                }
            }
        }
    };


    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public Connection(@NonNull Context context, @NonNull BluetoothDevice device, @NonNull ConnectionConfig config) {
        Log.i(TAG, "Connection: Initializing BLE connection");
        this.context = context.getApplicationContext();
        this.device = device;
        this.clientCharacteristicConfigUuid = config.clientCharacteristicConfigUuid;
        this.uartServiceUuid = config.uartServiceUuid;
        this.uartTxCharUuid = config.uartTxCharUuid;
        this.uartRxCharUuid = config.uartRxCharUuid;
        this.mtu = config.mtu;
        Log.i(TAG, "Connection: BLE connection initialized");
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public void connect() {
        Log.i(TAG, "connect: Starting BLE connection");
        if (device == null) {
            Log.e(TAG, "connect: Device is null!");
            return;
        }
        Log.d(TAG, "connect: Device name=" + device.getName() + ", address=" + device.getAddress());
        
        if (gatt != null) {
            Log.d(TAG, "connect: Closing existing GATT connection");
            gatt.close();
        }
        
        try {
            Log.d(TAG, "connect: Calling connectGatt");
            this.gatt = device.connectGatt(context, false, internalCallback);
            Log.i(TAG, "connect: connectGatt called successfully");
        } catch (Exception e) {
            Log.e(TAG, "connect: Error connecting to GATT", e);
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public boolean isConnected() {
        if (this.context == null || this.gatt == null) return false;

        BluetoothManager manager = (BluetoothManager) this.context.getSystemService(Context.BLUETOOTH_SERVICE);
        int state = manager.getConnectionState(this.gatt.getDevice(), BluetoothProfile.GATT);
        return state == BluetoothProfile.STATE_CONNECTED;
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public boolean isInitialized() {
        return isInitialized && isConnected();
    }

    /**
     * Initialize the connection
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private void init() {

        BluetoothGattService uartService = gatt.getService(uartServiceUuid);
        if (uartService == null) {
            throw new BleInitializationException("UART service not found (UUID: " + uartServiceUuid + ")");
        }

        this.txChar = uartService.getCharacteristic(uartTxCharUuid);
        this.rxChar = uartService.getCharacteristic(uartRxCharUuid);

        if (this.txChar == null) {
            throw new BleInitializationException("TX characteristic not found (UUID: " + uartTxCharUuid + ")");
        }

        if (this.rxChar == null) {
            throw new BleInitializationException("RX characteristic not found (UUID: " + uartRxCharUuid + ")");
        }
        
        enableRxNotification();
        isInitialized = true;
        Log.d(TAG, "Initialization completed successfully");
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public void reconnect() {
        Log.i(TAG, "reconnect: Reconnecting BLE");
        if (device == null || context == null) {
            Log.w(TAG, "reconnect: Device or context is null");
            return;
        }

        if (gatt != null) {
            gatt.disconnect();
        }
        this.connect();
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public void disconnect() {
        Log.i(TAG, "disconnect: Disconnecting BLE");
        if (gatt != null) {
            gatt.disconnect();
            gatt.close();
            gatt = null;
            txChar = null;
            rxChar = null;
            isInitialized = false;
        }
    } 

    /**
     * Send data to the glasses
     * @param data
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public boolean send(byte[] data) {
        if (txChar == null || gatt == null) {
            Log.w(TAG, "send: TX characteristic or GATT is null, cannot send");
            return false;
        }
        txChar.setValue(data);
        boolean result = gatt.writeCharacteristic(txChar);
        Log.d(TAG, "send: Data sent: " + Arrays.toString(data) + ", result=" + result);
        return result;
    }

    /**
     * Set the listener for the RX data, so the app can receive the data from the glasses
     * @param listener
     */
    public void setOnRxDataListener(OnRxDataListener listener) {
        Log.i(TAG, "setOnRxDataListener: Listener set");
        this.rxDataListener = listener;
    }

    /**
     * Enable the RX notification (response from the glasses)
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private boolean enableRxNotification() {
        if (gatt == null || rxChar == null) {
            Log.e(TAG, "enableRxNotification: GATT or RX characteristic is null");
            return false;
        }
        boolean notificationSet = gatt.setCharacteristicNotification(rxChar, true);
        BluetoothGattDescriptor descriptor = rxChar.getDescriptor(clientCharacteristicConfigUuid);
        boolean descriptorWritten = false;
        if (descriptor != null) {
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            descriptorWritten = gatt.writeDescriptor(descriptor);
        }
        Log.d(TAG, "enableRxNotification: notificationSet=" + notificationSet + ", descriptorWritten=" + descriptorWritten);
        return notificationSet && descriptorWritten;
    }

    /**
     * Listener for the RX data
     */
    public interface OnRxDataListener {
        void onDataReceived(byte[] data);
    }
}