package com.example.smartdog.app;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import com.example.smartdog.R;

public class AboutActivity extends Activity
{
    private static final String TAG = AboutActivity.class.getName();

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);
    }

    public void onDismissButtonClick(View v)
    {
        Log.v(TAG, "onDismissButtonClick");
        finish();
    }

}
