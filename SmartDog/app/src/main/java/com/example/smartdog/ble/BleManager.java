package com.example.smartdog.ble;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import java.util.List;
import java.util.UUID;

public class BleManager implements BleGattExecutor.BleExecutorListener {

    private final static String TAG = BleManager.class.getSimpleName();
    private static BleManager instance = null;
    private final BleGattExecutor executor = BleGattExecutor.createExecutor(this);
    private BluetoothAdapter btAdapter;
    private BluetoothGatt btGatt;

    private BluetoothDevice btDevice;
    private String deviceAddress;

    private BleManagerListener bleListener;

    public static BleManager getInstance(Context context) {
        if (instance == null) {
            instance = new BleManager(context);
        }
        return instance;
    }

    public BluetoothDevice getConnectedDevice() {
        return btDevice;
    }

    public void setBleListener(BleManagerListener listener) {
        bleListener = listener;
    }

    private BleManager(Context context) {
        // Init Adapter
        if (btAdapter == null) {
            btAdapter = BleUtils.getBluetoothAdapter(context);
        }

        if (btAdapter == null || !btAdapter.isEnabled()) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
        }
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     * @return Return true if the connection is initiated successfully.
     * The connection result is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public boolean connect(Context context, String address) {
        if (btAdapter == null || address == null) {
            Log.w(TAG, "connect: BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Get preferences
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        final boolean reuseExistingConnection = sharedPreferences.getBoolean("pref_recycleconnection", false);

        if (reuseExistingConnection) {
            // Previously connected device.  Try to reconnect.
            if (address.equalsIgnoreCase(deviceAddress) && btGatt != null) {
                Log.d(TAG, "Trying to use an existing BluetoothGatt for connection.");
                if (btGatt.connect()) {
                    if (bleListener != null)
                        bleListener.onConnecting();
                    return true;
                } else {
                    return false;
                }
            }
        } else {
            final boolean forceCloseBeforeNewConnection = sharedPreferences.getBoolean("pref_forcecloseconnection", true);

            if (forceCloseBeforeNewConnection) {
                close();
            }
        }

        btDevice = btAdapter.getRemoteDevice(address);
        if (btDevice == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }
        Log.d(TAG, "Trying to create a new connection.");
        deviceAddress = address;
        if (bleListener != null) {
            bleListener.onConnecting();
        }

        final boolean gattAutoconnect = sharedPreferences.getBoolean("pref_gattautoconnect", true);
        btGatt = btDevice.connectGatt(context, gattAutoconnect, executor);

        return true;
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)} callback.
     */
    public void disconnect() {
        btDevice = null;

        if (btAdapter == null || btGatt == null) {
            Log.w(TAG, "disconnect: BluetoothAdapter not initialized");
            return;
        }
        btGatt.disconnect();
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are  released properly.
     */
    private void close() {
        if (btGatt != null) {
            btGatt.close();
            btGatt = null;
            deviceAddress = null;
            btDevice = null;
        }
    }

    public void writeService(BluetoothGattService service, String uuid, byte[] value) {
        if (service != null) {
            if (btAdapter == null || btGatt == null) {
                Log.w(TAG, "writeService: BluetoothAdapter not initialized");
                return;
            }
            executor.write(service, uuid, value);
            executor.execute(btGatt);
        }
    }

    public void enableNotification(BluetoothGattService service, String uuid, boolean enabled) {
        if (service != null) {

            if (btAdapter == null || btGatt == null) {
                Log.w(TAG, "enableNotification: BluetoothAdapter not initialized");
                return;
            }
            executor.enableNotification(service, uuid, enabled);
            executor.execute(btGatt);
        }
    }

    /**
     * Retrieves a list of supported GATT services on the connected device. This should be
     * invoked only after {@code BluetoothGatt#discoverServices()} completes successfully.
     *
     * @return A {@code List} of supported services.
     */
    public List<BluetoothGattService> getSupportedGattServices() {
        if (btGatt != null) {
            return btGatt.getServices();
        } else {
            return null;
        }
    }

    public void enableIndication(BluetoothGattService service, String uuid, boolean enabled) {
        if (service != null) {

            if (btAdapter == null || btGatt == null) {
                Log.w(TAG, "enableNotification: BluetoothAdapter not initialized");
                return;
            }

            executor.enableIndication(service, uuid, enabled);
            executor.execute(btGatt);
        }
    }

    public BluetoothGattService getGattService(String uuid) {
        if (btGatt != null) {
            final UUID serviceUuid = UUID.fromString(uuid);
            return btGatt.getService(serviceUuid);
        } else {
            return null;
        }
    }

    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int newState) {
        if (newState == BluetoothProfile.STATE_CONNECTED) {
            if (bleListener != null) {
                bleListener.onConnected();
            }
            // Attempts to discover services after successful connection.
            gatt.discoverServices();

        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {

            if (bleListener != null) {
                bleListener.onDisconnected();
            }
        } else if (newState == BluetoothProfile.STATE_CONNECTING) {

            if (bleListener != null) {
                bleListener.onConnecting();
            }
        }
    }

    // region BleExecutorListener
    @Override
    public void onServicesDiscovered(int status) {
        // Call listener
        if (bleListener != null)
            bleListener.onServicesDiscovered();
        if (status != BluetoothGatt.GATT_SUCCESS) {
            Log.d(TAG, "onServicesDiscovered status: " + status);
        }
    }

    @Override
    public void onCharacteristicRead(BluetoothGattCharacteristic characteristic, int status) {
        if (bleListener != null) {
            bleListener.onDataAvailable(characteristic);
        }
        if (status != BluetoothGatt.GATT_SUCCESS) {
            Log.d(TAG, "onCharacteristicRead status: " + status);
        }
    }

    @Override
    public void onCharacteristicChanged(BluetoothGattCharacteristic characteristic) {
        if (bleListener != null) {
            bleListener.onDataAvailable(characteristic);
        }
    }

    @Override
    public void onDescriptorRead(BluetoothGattDescriptor descriptor, int status) {
        if (bleListener != null) {
            bleListener.onDataAvailable(descriptor);
        }
        if (status != BluetoothGatt.GATT_SUCCESS) {
            Log.d(TAG, "onDescriptorRead status: " + status);
        }
    }

    @Override
    public void onReadRemoteRssi(int rssi, int status) {
        if (bleListener != null) {
            bleListener.onReadRemoteRssi(rssi);
        }

        if (status != BluetoothGatt.GATT_SUCCESS) {
            Log.d(TAG, "onReadRemoteRssi status: " + status);
        }

    }
    //endregion

    public interface BleManagerListener {

        void onConnected();

        void onConnecting();

        void onDisconnected();

        void onServicesDiscovered();

        void onDataAvailable(BluetoothGattCharacteristic characteristic);

        void onDataAvailable(BluetoothGattDescriptor descriptor);

        void onReadRemoteRssi(int rssi);
    }
}
