package com.example.smartdog.ble;

import android.bluetooth.BluetoothDevice;

import java.util.ArrayList;
import java.util.UUID;

public class BluetoothDeviceData
{
    public BluetoothDevice device;
    public int rssi;
    public byte[] scanRecord;
    public String advertisedName;           // Advertised name
    private String cachedName;

    // Decoded scan record (update R.array.scan_devicetypes if this list is modified)
    public static final int type_Unknown = 0;
    public static final int type_Uart = 1;
    public static final int type_Beacon = 2;
    public static final int type_UriBeacon = 3;

    public int type;
    public int txPower;
    public ArrayList<UUID> uuids;

    String getName() {
        if (cachedName == null) {
            cachedName = device.getName();
            if (cachedName == null) {
                cachedName = advertisedName;      // Try to get a name (but it seems that if device.getName() is null, this is also null)
            }
        }
        return cachedName;
    }

}
