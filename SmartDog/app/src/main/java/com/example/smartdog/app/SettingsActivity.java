package com.example.smartdog.app;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.example.smartdog.R;

public class SettingsActivity extends AppCompatActivity
{
    private static final String TAG = SettingsActivity.class.getName();

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.settings, new SettingsFragment())
                .commit();
    }
}