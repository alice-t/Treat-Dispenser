package com.example.smartdog.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.example.smartdog.R;
import com.example.smartdog.ble.BleUtils;

public class BluetoothSetupActivity extends Activity
{
    private static final String TAG = BluetoothSetupActivity.class.getName();
    private static final int REQUEST_ENABLE_BLUETOOTH = 1;
    private static final int REQUEST_COARSE_LOCATION = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth_setup);
        checkBluetoothAvailability();

    }

    private void checkBluetoothAvailability() {
        Log.v(TAG, "checkBluetoothAvailability");
        Intent returnIntent = new Intent();
        // Check Bluetooth HW status
        boolean bluetoothOK = true;

        final int bleStatus = BleUtils.getBleStatus(getBaseContext());
        switch (bleStatus) {
            case BleUtils.STATUS_BLE_ENABLED:
                checkLocationPermission();
                break;
            case BleUtils.STATUS_BLUETOOTH_DISABLED:
                Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(intent, REQUEST_ENABLE_BLUETOOTH);
                break;
            case BleUtils.STATUS_BLE_NOT_AVAILABLE:
                returnIntent.putExtra("result", "BLE not available");
                bluetoothOK = false;
                break;
            case BleUtils.STATUS_BLUETOOTH_NOT_AVAILABLE:
                returnIntent.putExtra("result", "Bluetooth not available");
                bluetoothOK = false;
                break;
            default:
                returnIntent.putExtra("result", "default: " + bleStatus);
                bluetoothOK = false;
                break;
        }
        if (!bluetoothOK) {
            setResult(Activity.RESULT_OK, returnIntent);
            finish();
        }
    }

    private void checkLocationPermission()
    {
        Log.v(TAG, "checkLocationPermission");
        Intent returnIntent = new Intent();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED)
            {
                Log.v(TAG, "checkLocationPermission: Requesting permission...");
                final AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
                builder.setTitle("This app needs location access");
                builder.setMessage("Please grant location access so this app can scan for for Smart Dog devices");
                builder.setPositiveButton(android.R.string.ok, null);
                builder.setOnDismissListener(dialog -> requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_COARSE_LOCATION));
                AlertDialog alertDialog = builder.create();
                alertDialog.show();
                alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor( ContextCompat.getColor(getBaseContext(), R.color.colorSecondary));
            }
            else
            {
                if (!isLocationEnabled())
                {
                    Log.v (TAG, "LocationEnabled=false");
                    returnIntent.putExtra("result", "Location service disabled");
                    final AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
                    builder.setTitle("This app needs the location service enabled");
                    builder.setMessage("Please enable the location service so this app can scan for Smart Dog devices");
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.setOnDismissListener(dialog -> {setResult(Activity.RESULT_OK, returnIntent); finish();});
                    AlertDialog alertDialog = builder.create();
                    alertDialog.show();
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor( ContextCompat.getColor(getBaseContext(), R.color.colorSecondary));
                }
                else
                {
                    returnIntent.putExtra("result", "Location permission already granted");
                    setResult(Activity.RESULT_OK, returnIntent);
                    finish();
                }
            }
        }
        else
        {
            returnIntent.putExtra("result", "Location permission not required");
            setResult(Activity.RESULT_OK, returnIntent);
            finish();
        }
    }

    public boolean isLocationEnabled()
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // This is new method provided in API 28
            LocationManager lm = (LocationManager) getBaseContext().getSystemService(Context.LOCATION_SERVICE);
            return lm.isLocationEnabled();
        } else {
            // Deprecated in API 28
            int mode = Settings.Secure.getInt(getBaseContext().getContentResolver(),
                    Settings.Secure.LOCATION_MODE,
                    Settings.Secure.LOCATION_MODE_OFF);
            return  (mode != Settings.Secure.LOCATION_MODE_OFF);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults)
    {
        Log.v(TAG, "onRequestPermissionsResult");
        Intent returnIntent = new Intent();
        if (requestCode == REQUEST_COARSE_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                returnIntent.putExtra("result", "Location permission granted");
            }
            else
            {
                returnIntent.putExtra("result", "Location permission denied");
            }
            setResult(Activity.RESULT_OK, returnIntent);
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.v(TAG, "onActivityResult");
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BLUETOOTH) {
            if (resultCode == Activity.RESULT_OK) {
                // Bluetooth enabled, check location access permission
                checkLocationPermission();
            } else if (resultCode == Activity.RESULT_CANCELED) {
                Log.v(TAG, "onActivityResult: RESULT_CANCELED");
                Intent returnIntent = new Intent();
                returnIntent.putExtra("result", "Bluetooth permission denied");
                setResult(Activity.RESULT_OK, returnIntent);
                finish();
            }
        }
    }
}