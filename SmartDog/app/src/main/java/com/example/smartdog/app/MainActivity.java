package com.example.smartdog.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.example.smartdog.R;

public class MainActivity extends AppCompatActivity
{

    private static final String TAG = MainActivity.class.getName();

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        Log.v(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_settings, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent intent;
        switch (item.getItemId())
        {
            case R.id.settings:
                intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
                return true;
            case R.id.about:
                intent = new Intent(this, AboutActivity.class);
                startActivity(intent);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onStart()
    {
        Log.v(TAG, "onStart");
        super.onStart();
        Intent intent = getIntent();
        String callingActivity = intent.getStringExtra("activity");
        if (callingActivity != null)
        {
            Log.v(TAG, "newStart false");
        }
        else
            Log.v(TAG, "newStart true");
        String result = intent.getStringExtra("result");
        if (result != null)
        {
            String msg = null;
            boolean allOK = false;
            switch(result) {
                case "Location permission denied":
                    msg = "Since location access has not been granted, the app is unable to scan for Smart Dog devices.";
                    break;
                case "Location service disabled":
                    msg = "Since the location service is disabled, the app is unable to scan for Smart Dog devices.";
                    break;
                case "BLE not available":
                    msg = "Bluetooth LE is not available on your device. Unable to control Smart Dog devices.";
                    break;
                case "Bluetooth permission denied":
                    msg = "Since Bluetooth has not been enabled, the app is unable to access Smart Dog devices.";
                    break;
                default:
                    allOK = true;
            }
            if (!allOK)
            {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage(msg)
                        .setPositiveButton(android.R.string.ok, null);
                AlertDialog alertDialog = builder.create();
                alertDialog.show();
                alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor( ContextCompat.getColor(getBaseContext(), R.color.colorSecondary));
            }
        }
    }

    public void onConnectButtonClick(View v)
    {
        Log.v(TAG, "findDevices");
        Intent intent = new Intent(this, DiscoveryActivity.class);
        startActivity(intent);
    }

    public void onExitButtonClick(View v)
    {
        Intent homeIntent = new Intent(Intent.ACTION_MAIN);
        homeIntent.addCategory( Intent.CATEGORY_HOME );
        homeIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(homeIntent);
        finish();
    }

    @Override
    protected void onResume() {
        Log.v(TAG, "onResume");
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        SoundManager.cleanUp();
        super.onDestroy();
    }

}
