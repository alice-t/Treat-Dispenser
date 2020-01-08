package com.example.smartdog.app;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.os.Handler;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.example.smartdog.ble.BleManager;

import java.nio.charset.Charset;
import java.util.Arrays;

public class UartInterfaceActivity extends AppCompatActivity implements BleManager.BleManagerListener {

    private final static String TAG = UartInterfaceActivity.class.getSimpleName();

    // Service Constants
    public static final String UUID_SERVICE = "6e400001-b5a3-f393-e0a9-e50e24dcca9e";
    public static final String UUID_RX = "6e400003-b5a3-f393-e0a9-e50e24dcca9e";
    public static final String UUID_TX = "6e400002-b5a3-f393-e0a9-e50e24dcca9e";
    public static final int maxCharacters = 20;

    // Data
    protected BleManager bleManager;
    protected BluetoothGattService mUartService;


    // region Send Data to UART
    protected void sendData(String text) {
        final byte[] value = text.getBytes(Charset.forName("UTF-8"));
        sendData(value);
    }

    protected void sendData(byte[] data) {
        if (mUartService != null) {
            // Split the value into chunks (UART service has a maximum number of characters that can be written )
            int len = data.length;
            for (int i = 0; i < len; i += maxCharacters) {
                final byte[] chunk = Arrays.copyOfRange(data, i, Math.min(i + maxCharacters, data.length));
                bleManager.writeService(mUartService, UUID_TX, chunk);
            }
        } else {
            Log.w(TAG, "Uart Service not discovered. Unable to send data");
        }
    }

    // endregion

    // region SendDataWithCompletionHandler
    protected interface SendDataCompletionHandler {
        void sendDataResponse(String data);
    }

    final private Handler sendDataTimeoutHandler = new Handler();
    private Runnable sendDataRunnable = null;
    private SendDataCompletionHandler sendDataCompletionHandler = null;

    // endregion

    // region BleManagerListener  (used to implement sendData with completionHandler)

    @Override
    public void onConnected() {

    }

    @Override
    public void onConnecting() {

    }

    @Override
    public void onDisconnected() {

    }

    @Override
    public void onServicesDiscovered() {
        mUartService = bleManager.getGattService(UUID_SERVICE);
    }

    protected void enableRxNotifications() {
        bleManager.enableNotification(mUartService, UUID_RX, true);
    }

    @Override
    public void onDataAvailable(BluetoothGattCharacteristic characteristic) {
        // Check if there is a pending sendDataRunnable
        if (sendDataRunnable != null) {
            if (characteristic.getService().getUuid().toString().equalsIgnoreCase(UUID_SERVICE)) {
                if (characteristic.getUuid().toString().equalsIgnoreCase(UUID_RX)) {

                    Log.d(TAG, "sendData received data");
                    sendDataTimeoutHandler.removeCallbacks(sendDataRunnable);
                    sendDataRunnable = null;

                    if (sendDataCompletionHandler != null) {
                        final byte[] bytes = characteristic.getValue();
                        final String data = new String(bytes, Charset.forName("UTF-8"));

                        final SendDataCompletionHandler dataCompletionHandler = sendDataCompletionHandler;
                        sendDataCompletionHandler = null;
                        dataCompletionHandler.sendDataResponse(data);
                    }
                }
            }
        }
    }

    @Override
    public void onDataAvailable(BluetoothGattDescriptor descriptor) {

    }

    @Override
    public void onReadRemoteRssi(int rssi) {

    }

    // endregion
}
