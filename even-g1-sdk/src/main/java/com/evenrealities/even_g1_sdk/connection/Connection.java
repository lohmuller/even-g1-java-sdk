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

    private static final String TAG = "BLE";
    

    /**
     * Internal callback for the BluetoothGatt
     */
    private final BluetoothGattCallback internalCallback = new BluetoothGattCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.requestMtu(mtu);
            } else {
                Connection.this.isInitialized = false;
            }
        }


        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                gatt.discoverServices(); 
            } else {
                Log.e(TAG, "Error while negotiating MTU (status=" + status + ")");
                gatt.disconnect();
                gatt.close();
            }

        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                new Handler(Looper.getMainLooper()).post(() -> init());               
            }
        }

        @Override   
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if (rxChar != null && characteristic.getUuid().equals(rxChar.getUuid())) {
                byte[] data = characteristic.getValue();
                if (rxDataListener != null) {
                    rxDataListener.onDataReceived(data);
                }
            }
        }
    };


    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public Connection(@NonNull Context context, @NonNull BluetoothDevice device, @NonNull ConnectionConfig config) {
        this.context = context.getApplicationContext();
        this.device = device;
        this.gatt = device.connectGatt(context, false, internalCallback);
        this.clientCharacteristicConfigUuid = config.clientCharacteristicConfigUuid;
        this.uartServiceUuid = config.uartServiceUuid;
        this.uartTxCharUuid = config.uartTxCharUuid;
        this.uartRxCharUuid = config.uartRxCharUuid;
        this.mtu = config.mtu;
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
        if (device == null || context == null) {
            Log.w(TAG, "Not possible to reconnect: context or device are null");
            return;
        }

        if (gatt != null) {
            gatt.close();
        }
        gatt = device.connectGatt(context, false, internalCallback);
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public void disconnect() {
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
        if (txChar == null || gatt == null) return false;
        txChar.setValue(data);
        return gatt.writeCharacteristic(txChar);
    }

    /**
     * Set the listener for the RX data, so the app can receive the data from the glasses
     * @param listener
     */
    public void setOnRxDataListener(OnRxDataListener listener) {
        this.rxDataListener = listener;
    }

    /**
     * Enable the RX notification (response from the glasses)
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private void enableRxNotification() {
        boolean notificationSet = gatt.setCharacteristicNotification(rxChar, true);
        if (!notificationSet) {
            throw new BleInitializationException("Failed to set notification for RX characteristic");
        }

        BluetoothGattDescriptor descriptor = rxChar.getDescriptor(clientCharacteristicConfigUuid);
        if (descriptor == null) {
            throw new BleInitializationException("Failed to get descriptor for RX characteristic");
        }
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        gatt.writeDescriptor(descriptor);
    }

    /**
     * Listener for the RX data
     */
    public interface OnRxDataListener {
        void onDataReceived(byte[] data);
    }
}