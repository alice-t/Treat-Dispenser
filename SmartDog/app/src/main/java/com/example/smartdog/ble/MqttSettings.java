package com.example.smartdog.ble;

import android.content.Context;
import android.content.SharedPreferences;

public class MqttSettings {
    private final static String TAG = MqttSettings.class.getSimpleName();

    // Singleton
    private static MqttSettings mInstance = null;

    // Constants
    public final static String defaultSubscribeTopic = null;//"uart_input";

    private final static String preferences = "MqttSettings_prefs";
    private final static String preferences_subscribetopic = "subscribetopic";
    private final static String preferences_subscribeqos = "subscribeqos";
    private final static String preferences_subscribeenabled = "subscribeenabled";
    private final static String preferences_connected = "connected";

    // Data
    private Context mContext;

    public static MqttSettings getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new MqttSettings(context);
        }
        return mInstance;
    }

    public MqttSettings(Context context) {
        mContext = context.getApplicationContext();
    }

    private SharedPreferences getSharedPreferences() {
        return mContext.getSharedPreferences(preferences, Context.MODE_PRIVATE);
    }

    private SharedPreferences.Editor getSharedPreferencesEditor() {
        return mContext.getSharedPreferences(preferences, Context.MODE_PRIVATE).edit();
    }


    public void setConnectedEnabled(boolean enabled) {
        setPrefsBoolean(preferences_connected, enabled);
    }

    public boolean isSubscribeEnabled() {
        return getPrefsBoolean();
    }

    public int getSubscribeQos() {
        return getPrefsInt(MqttManager.MqqtQos_AtMostOnce);
    }


    public String getSubscribeTopic() {
        return getPrefsString();
    }

    // region Utils
    private String getPrefsString() {
        return getSharedPreferences().getString(MqttSettings.preferences_subscribetopic, MqttSettings.defaultSubscribeTopic);
    }

    private int getPrefsInt(int defaultValue) {
        return getSharedPreferences().getInt(MqttSettings.preferences_subscribeqos, defaultValue);
    }

    private boolean getPrefsBoolean() {
        return getSharedPreferences().getBoolean(MqttSettings.preferences_subscribeenabled, true);
    }


    public void setPrefsBoolean(String key, boolean value) {
        SharedPreferences.Editor editor = getSharedPreferencesEditor();
        editor.putBoolean(key, value);
        editor.apply();
    }
    // endregion

}
