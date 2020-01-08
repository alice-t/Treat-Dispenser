package com.example.smartdog.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.preference.PreferenceManager;

import com.example.smartdog.R;
import com.example.smartdog.ble.BleDevicesScanner;
import com.example.smartdog.ble.BleManager;
import com.example.smartdog.ble.BleUtils;
import com.example.smartdog.ble.BluetoothDeviceData;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;

public class DiscoveryActivity extends ListActivity implements BleManager.BleManagerListener
{
    private static final String TAG = DiscoveryActivity.class.getName();
    private static final int CHECK_BLUETOOTH_SETUP = 1;
    private static final String UUID_SERVICE = "6e400001-b5a3-f393-e0a9-e50e24dcca9e";
    private static final String genericAttributeService = "00001801-0000-1000-8000-00805F9B34FB";
    private static final String serviceChangedCharacteristic = "00002A05-0000-1000-8000-00805F9B34FB";
    private final static long minDelayToUpdateUI = 200;

    private static final int activityRequestCode_ConnectedActivity = 3;

    private BleManager bleManager;
    private BleDevicesScanner scanner;
    private long lastUpdateMillis;
    private AlertDialog alertDialog;
    private ArrayList<BluetoothDeviceData> scannedDevices;
    private ArrayList<BluetoothDeviceData> btDeviceData = new ArrayList<BluetoothDeviceData>();
    private DeviceDataListAdapter deviceDataListAdapter;
    private Context context;
    private ConnectionTimeout connectionTimeout;
    private Handler handler;
    private static boolean active = false;
    private boolean tryingToConnect = false;

    private String getDeviceType(BluetoothDevice device)
    {
        int deviceType = device.getType();
        if(deviceType == BluetoothDevice.DEVICE_TYPE_CLASSIC)
            return "Classic";
        else if(deviceType == BluetoothDevice.DEVICE_TYPE_LE)
            return "BLE";
        else if(deviceType == BluetoothDevice.DEVICE_TYPE_DUAL)
            return "Dual";
        else
            return "Unknown";
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        Log.v(TAG, "onCreate");
        context = this;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_discovery);
        ListView lv = findViewById(android.R.id.list);
        deviceDataListAdapter = new DeviceDataListAdapter(btDeviceData);
        lv.setAdapter(deviceDataListAdapter);
        bleManager = BleManager.getInstance(this);
        Intent intent = new Intent(this, BluetoothSetupActivity.class);
        startActivityForResult(intent, CHECK_BLUETOOTH_SETUP);
    }

    @Override
    public void onStart() {
        super.onStart();
        active = true;
    }

    @Override
    public void onPause() {
        Log.v(TAG, "onPause");
        // Stop scanning
        if (scanner != null && scanner.isScanning()) {
            stopScanning();
        }
        super.onPause();
    }

    @Override
    public void onResume() {
        Log.v(TAG, "onResume");
        super.onResume();
        btDeviceData.clear();
        deviceDataListAdapter.notifyDataSetChanged();
        // Set listener
        bleManager.setBleListener(this);
        BluetoothDevice btDevice = bleManager.getConnectedDevice();
        if (btDevice != null)
        {
            Log.v(TAG, "DEVICE NOT NULL");
            int connectionStatus = 0;
            try
            {
                connectionStatus = BleUtils.getConnectionStatus(this, btDevice);
            } catch (Exception e)
            {
                Log.v(TAG, e.toString());
            }
            switch (connectionStatus)
            {
                case 0:
                    Log.v(TAG, "connectionStatus = STATE_DISCONNECTED");
                    connect(btDevice.getAddress());
                    break;
                case 1:
                    Log.v(TAG, "connectionStatus = STATE_CONNECTING");
                    break;
                case 2:
                    Log.v(TAG, "connectionStatus = STATE_CONNECTED");
                    connect(btDevice.getAddress());
                    break;
                case 3:
                    Log.v(TAG, "connectionStatus = STATE_DISCONNECTING");
                    break;
            }
        }
        else
        {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
            boolean autoConnect = sp.getBoolean("auto_connect", true);
            if (autoConnect)
            {
                Log.v(TAG, "Bluetooth device null - try connecting to last used device");
                String mac_address = sp.getString("last_connected_address", null);
                if (!(mac_address != null && connect(mac_address)))
                {
                    Log.v(TAG, "Default connection not made");
                    autostartScan();
                }
            }
            else
                autostartScan();
        }
        deviceDataListAdapter.notifyDataSetChanged();
    }

    @Override
    public void onStop() {
        Log.v(TAG, "onStop");
        if (handler != null)
            handler.removeCallbacks(connectionTimeout);
        if (alertDialog != null) {
            alertDialog.cancel();
            alertDialog = null;
        }
        active = false;
        super.onStop();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.v(TAG, "onActivityResult requestCode=" + requestCode);
        if (requestCode == CHECK_BLUETOOTH_SETUP) {
            String result = data.getStringExtra("result");
            if(resultCode == Activity.RESULT_OK){
                Log.v(TAG, result);
                if (result.equals("Location permission denied")
                        || result.equals("BLE not available")
                        || result.equals("Bluetooth permission denied")
                        || result.equals("Location service disabled"))
                {
                    Intent returnIntent = new Intent(this, MainActivity.class);
                    returnIntent.putExtra("activity", "DiscoveryActivity");
                    returnIntent.putExtra("result", result);
                    startActivity(returnIntent);
                }
            }
            if (resultCode == Activity.RESULT_CANCELED)
            {
                Log.v(TAG, "onActivityResult: RESULT_CANCELED");
                Intent returnIntent = new Intent(this, MainActivity.class);
                returnIntent.putExtra("activity", "DiscoveryActivity");
                startActivity(returnIntent);
            }
        }
    }

    private void autostartScan() {
        if (BleUtils.getBleStatus(this) == BleUtils.STATUS_BLE_ENABLED) {
            // If was connected, disconnect
            bleManager.disconnect();
            // Force restart scanning
            if (scannedDevices != null) {      // Fixed a weird bug when resuming the app (this was null on very rare occasions even if it should not be)
                scannedDevices.clear();
            }
            if (btDeviceData != null)
            {
                btDeviceData.clear();
            }
            startScan();
        }
    }

    private void startScan() {
        Log.v(TAG, "startScan");
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        final boolean onlySmartDogDevices = sp.getBoolean("show_only_smart_dog_devices", false);
        // Stop current scanning (if needed)
        stopScanning();

        // Configure scanning
        BluetoothAdapter bluetoothAdapter = BleUtils.getBluetoothAdapter(getApplicationContext());
        if (BleUtils.getBleStatus(this) != BleUtils.STATUS_BLE_ENABLED) {
            Log.w(TAG, "startScan: BluetoothAdapter not initialized or unspecified address.");
        } else {
            scanner = new BleDevicesScanner(bluetoothAdapter, null, (device, rssi, scanRecord) -> {
                if (!onlySmartDogDevices || (device.getName() != null && device.getName().startsWith("SDOG-")))
                {
                    BluetoothDeviceData previouslyScannedDeviceData = null;
                    if (scannedDevices == null) {
                        scannedDevices = new ArrayList<>();
                    }

                    // Check device not previously found
                    for (BluetoothDeviceData deviceData : scannedDevices) {
                        if (deviceData.device.getAddress().equals(device.getAddress())) {
                            previouslyScannedDeviceData = deviceData;
                            break;
                        }
                    }

                    BluetoothDeviceData deviceData;
                    if (previouslyScannedDeviceData == null) {
                        // Add to scannedDevices
                        deviceData = new BluetoothDeviceData();
                        deviceData.device = device;
                        deviceData.rssi = rssi;
                        deviceData.scanRecord = scanRecord;
                        btDeviceData.add(deviceData);
                        decodeScanRecords(deviceData);
                        scannedDevices.add(deviceData);
                    }
                    // Update device data
                    long currentMillis = SystemClock.uptimeMillis();
                    // Don't update when not a new device has been found and time from last update is short to avoid updating UI so fast it becomes unresponsive
                    if (previouslyScannedDeviceData == null || currentMillis - lastUpdateMillis > minDelayToUpdateUI) {
                        lastUpdateMillis = currentMillis;

                        runOnUiThread(() -> deviceDataListAdapter.notifyDataSetChanged());
                    }
                }
            });

            scanner.start();
        }
        deviceDataListAdapter.notifyDataSetChanged();
    }

    private void stopScanning() {
        // Stop scanning
        if (scanner != null) {
            scanner.stop();
            scanner = null;
        }
        deviceDataListAdapter.notifyDataSetChanged();
    }

    // endregion

    private void decodeScanRecords(BluetoothDeviceData deviceData) {
        // based on http://stackoverflow.com/questions/24003777/read-advertisement-packet-in-android
        final byte[] scanRecord = deviceData.scanRecord;

        ArrayList<UUID> uuids = new ArrayList<>();
        byte[] advertisedData = Arrays.copyOf(scanRecord, scanRecord.length);
        int offset = 0;
        deviceData.type = BluetoothDeviceData.type_Unknown;

        // Check if is an iBeacon ( 0x02, 0x0x1, a flag byte, 0x1A, 0xFF, manufacturer (2bytes), 0x02, 0x15)
        final boolean isBeacon = advertisedData[0] == 0x02 && advertisedData[1] == 0x01 && advertisedData[3] == 0x1A && advertisedData[4] == (byte) 0xFF && advertisedData[7] == 0x02 && advertisedData[8] == 0x15;

        // Check if is an URIBeacon
        final byte[] kUriBeaconPrefix = {0x03, 0x03, (byte) 0xD8, (byte) 0xFE};
        final boolean isUriBeacon = Arrays.equals(Arrays.copyOf(scanRecord, kUriBeaconPrefix.length), kUriBeaconPrefix) && advertisedData[5] == 0x16 && advertisedData[6] == kUriBeaconPrefix[2] && advertisedData[7] == kUriBeaconPrefix[3];

        if (isBeacon) {
            deviceData.type = BluetoothDeviceData.type_Beacon;

            // Read uuid
            offset = 9;
            UUID uuid = BleUtils.getUuidFromByteArrayBigEndian(Arrays.copyOfRange(scanRecord, offset, offset + 16));
            uuids.add(uuid);
            offset += 16;

            // Skip major minor
            offset += 2 * 2;   // major, minor

            // Read txpower
            deviceData.txPower = (int) advertisedData[offset++];
        } else if (isUriBeacon) {
            deviceData.type = BluetoothDeviceData.type_UriBeacon;

            // Read txpower
            deviceData.txPower = (int) advertisedData[9];
        } else {
            // Read standard advertising packet
            while (offset < advertisedData.length - 2) {
                // Length
                int len = advertisedData[offset++];
                if (len == 0) break;

                // Type
                int type = advertisedData[offset++];
                if (type == 0) break;

                // Data
                switch (type) {
                    case 0x02:          // Partial list of 16-bit UUIDs
                    case 0x03: {        // Complete list of 16-bit UUIDs
                        while (len > 1) {
                            int uuid16 = advertisedData[offset++] & 0xFF;
                            uuid16 |= (advertisedData[offset++] << 8);
                            len -= 2;
                            uuids.add(UUID.fromString(String.format("%08x-0000-1000-8000-00805f9b34fb", uuid16)));
                        }
                        break;
                    }
                    case 0x06:          // Partial list of 128-bit UUIDs
                    case 0x07: {        // Complete list of 128-bit UUIDs
                        while (len >= 16) {
                            try {
                                // Wrap the advertised bits and order them.
                                UUID uuid = BleUtils.getUuidFromByteArraLittleEndian(Arrays.copyOfRange(advertisedData, offset, offset + 16));
                                uuids.add(uuid);

                            } catch (IndexOutOfBoundsException e) {
                                Log.e(TAG, "BlueToothDeviceFilter.parseUUID: " + e.toString());
                            } finally {
                                // Move the offset to read the next uuid.
                                offset += 16;
                                len -= 16;
                            }
                        }
                        break;
                    }
                    case 0x09: {
                        byte[] nameBytes = new byte[len - 1];
                        for (int i = 0; i < len - 1; i++) {
                            nameBytes[i] = advertisedData[offset++];
                        }

                        String name = null;
                        name = new String(nameBytes, StandardCharsets.UTF_8);
                        deviceData.advertisedName = name;
                        break;
                    }
                    case 0x0A: {        // TX Power
                        deviceData.txPower = (int) advertisedData[offset++];
                        break;
                    }
                    default: {
                        offset += (len - 1);
                        break;
                    }
                }
            }
            boolean isUart = false;
            for (UUID uuid : uuids) {
                if (uuid.toString().equalsIgnoreCase(UUID_SERVICE)) {
                    isUart = true;
                    break;
                }
            }
            if (isUart) {
                deviceData.type = BluetoothDeviceData.type_Uart;
            }
        }
        deviceData.uuids = uuids;
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {

        Log.v(TAG, "onListItemClick: position" + position + ", id=" + id);
        stopScanning();
        BluetoothDeviceData selectedDeviceData = btDeviceData.get(position);
        BluetoothDevice device = selectedDeviceData.device;
        bleManager.setBleListener(this);
        if (selectedDeviceData.type == BluetoothDeviceData.type_Uart)      // if is uart, show all the available activities
            connect(device.getAddress());
    }

    private boolean connect(String btAddress) {
        boolean isConnecting = bleManager.connect(this, btAddress);
        if (isConnecting)
        {
            tryingToConnect = true;
            Log.v(TAG, "connect.isConnecting");
            showConnectionStatusDialog(true);

            // new stuff
            if (handler == null)
                handler = new Handler();
            if (connectionTimeout == null)
                connectionTimeout = new ConnectionTimeout(context);
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
            String timeoutStr = sp.getString("auto_connect_timeout", String.valueOf(SettingsFragment.defaultTimeout));
            if (timeoutStr != null) {
                int timeout = Integer.parseInt(timeoutStr);
                handler.postDelayed(connectionTimeout, timeout * 1000);
            }
            else
            {
                Log.e(TAG, "connect: timeoutStr is null");
            }
        }
        return isConnecting;
    }

    private void launchComponentActivity() {
        // Enable generic attribute service
        final BluetoothGattService genericAttributeService = bleManager.getGattService(this.genericAttributeService);
        if (genericAttributeService != null) {
            Log.d(TAG, "genericAttributeService found. Check if serviceChangedCharacteristic exists");

            final UUID characteristicUuid = UUID.fromString(serviceChangedCharacteristic);
            final BluetoothGattCharacteristic dataCharacteristic = genericAttributeService.getCharacteristic(characteristicUuid);
            if (dataCharacteristic != null) {
                Log.d(TAG, "serviceChangedCharacteristic exists. Enable indication");
                bleManager.enableIndication(genericAttributeService, serviceChangedCharacteristic, true);
            } else {
                Log.d(TAG, "Skip enable indications for serviceChangedCharacteristic. Characteristic not found");
            }
        } else {
            Log.d(TAG, "Skip enable indications for serviceChangedCharacteristic. genericAttributeService not found");
        }

        // Clear alert dialog
        showConnectionStatusDialog(false);
        // Launch activity
        Intent intent = new Intent(this, DeviceControlActivity.class);
        startActivityForResult(intent, activityRequestCode_ConnectedActivity);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)  {
        if (!tryingToConnect && keyCode == KeyEvent.KEYCODE_BACK ) {
            Log.v(TAG, "Key down: " + tryingToConnect);
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("activity","DiscoveryActivity");
            startActivity(intent);
        }

        return super.onKeyDown(keyCode, event);
    }

    private void showConnectionStatusDialog(boolean show) {
        if (alertDialog != null) {
            alertDialog.cancel();
        }
        if (show) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(R.string.scan_connecting);
            builder.setNegativeButton("Cancel", (dialog, which) -> {
                bleManager.disconnect();
                autostartScan();
                alertDialog.cancel();
                handler.removeCallbacks(connectionTimeout);
                tryingToConnect = false;
            });

            // Show dialog
            alertDialog = builder.create();
            alertDialog.setCanceledOnTouchOutside(false);
            alertDialog.setOnKeyListener((arg0, keyCode, event) -> {
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    bleManager.disconnect();
                    alertDialog.cancel();
                }
                return true;
            });
            alertDialog.show();
            alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor( getResources().getColor(R.color.colorSecondary));
        }
    }

    // region BleManagerListener
    @Override
    public void onConnected() {
    }

    @Override
    public void onConnecting() {
        Log.d(TAG, "BleManagerListener.onConnecting");
    }

    @Override
    public void onDisconnected() {
        Log.d(TAG, "BleManagerListener.onDisconnected");
        // Clear alert dialog
        showConnectionStatusDialog(false);
    }

    @Override
    public void onServicesDiscovered() {
        Log.d(TAG, "BleManagerListener.onServicesDiscovered");
        tryingToConnect = false;
        handler.removeCallbacks(connectionTimeout);
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = sp.edit();
        editor.putString("last_connected_address", bleManager.getConnectedDevice().getAddress());
        editor.apply();
        launchComponentActivity();
    }

    @Override
    public void onDataAvailable(BluetoothGattCharacteristic characteristic) {
        Log.d(TAG, "BleManagerListener.onDataAvailable(BluetoothGattCharacteristic)");
    }

    @Override
    public void onDataAvailable(BluetoothGattDescriptor descriptor) {
        Log.d(TAG, "BleManagerListener.onDataAvailable(BluetoothGattDescriptor)");
    }

    @Override
    public void onReadRemoteRssi(int i) {
        Log.d(TAG, "BleManagerListener.onReadRemoteRssi");
    }
    // endregion

    class DeviceDataListAdapter extends BaseAdapter
    {
        private ArrayList<BluetoothDeviceData> btDeviceData;

        DeviceDataListAdapter(ArrayList<BluetoothDeviceData> btDeviceData)
        {
            this.btDeviceData = DiscoveryActivity.this.btDeviceData;
        }

        @Override
        public int getCount( )
        {
            return DiscoveryActivity.this.btDeviceData.size();
        }

        @Override
        public Object getItem( int position )
        {
            return DiscoveryActivity.this.btDeviceData.get(position);
        }

        @Override
        public long getItemId( int position )
        {
            return -1;
        }

        @Override
        public View getView( int position, View convertView, ViewGroup parent )
        {
            LayoutInflater inflater = getLayoutInflater();
            View row = inflater.inflate(R.layout.device, parent, false);
            TextView name, macAddress, deviceType;
            BluetoothDeviceData devData = (BluetoothDeviceData)getItem(position);
            BluetoothDevice dev = devData.device;
            name = row.findViewById(R.id.name);
            macAddress = row.findViewById(R.id.mac_address);
            deviceType = row.findViewById(R.id.device_type);
            name.setText(dev.getName());
            macAddress.setText(dev.getAddress());
            deviceType.setText(getDeviceType(dev));
            return (row);
        }
    }

    class ConnectionTimeout implements Runnable {
        private Context context;
        ConnectionTimeout(Context context) {
            this.context = context;
        }
        public void run() {
            Log.d(TAG, "ConnectionTimeout");
            tryingToConnect = false;
            if (alertDialog != null)
            {
                alertDialog.cancel();
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setMessage(R.string.unable_to_connect);
            builder.setPositiveButton("OK", (dialog, which) -> {
                bleManager.disconnect();
                alertDialog.cancel();
                finish();
            });

            // Show dialog
            alertDialog = builder.create();
            alertDialog.setCanceledOnTouchOutside(false);
            alertDialog.setOnKeyListener((arg0, keyCode, event) -> {
                if (keyCode == KeyEvent.KEYCODE_BACK)
                {
                    bleManager.disconnect();
                    alertDialog.cancel();
                }
                return true;
            });
            if (active)
            {
                alertDialog.show();
                alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(getResources().getColor(R.color.colorSecondary));
            }
        }
    }
}
