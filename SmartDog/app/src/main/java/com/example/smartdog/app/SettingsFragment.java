package com.example.smartdog.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;

import androidx.preference.CheckBoxPreference;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.example.smartdog.R;

public class SettingsFragment extends PreferenceFragmentCompat
{
    private static final String TAG = SettingsFragment.class.getName();
    public static final int defaultTimeout = 15;

    public SettingsFragment() {
        // Required empty public constructor
    }


    @Override
    public void onCreatePreferences(Bundle savedInstanceState,
                                    String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);
        PreferenceManager preferenceManager = getPreferenceManager();
        SharedPreferences sharedprefs = preferenceManager.getDefaultSharedPreferences(getContext());
        SharedPreferences.Editor editor = sharedprefs.edit();
        // App audio preferences
        ListPreference sound_preference = (ListPreference) preferenceManager.findPreference("dispensedSound");
        sound_preference.setOnPreferenceChangeListener((preference, newValue) -> {
            String sound = (String) newValue;
            editor.putString("dispensedSound", sound);
            editor.apply();
            if (!sound.equals("No Sound"))
            {
                Context applicationContext = getActivity().getApplicationContext();
                if (applicationContext != null)
                {
                    SoundManager soundManager = SoundManager.getInstance(applicationContext);
                    soundManager.playSample(sound);
                }
            }
            Toast.makeText(getActivity(), newValue + " selected", Toast.LENGTH_SHORT).show();
            return true;
        });
        sound_preference = (ListPreference)  preferenceManager.findPreference("failedSound");
        sound_preference.setOnPreferenceChangeListener((preference, newValue) -> {
            String sound = (String) newValue;
            editor.putString("failedSound", sound);
            editor.apply();
            if (!sound.equals("No Sound"))
            {
                Context applicationContext = getActivity().getApplicationContext();
                if (applicationContext != null)
                {
                    SoundManager soundManager = SoundManager.getInstance(applicationContext);
                    soundManager.playSample(sound);
                }

            }
            Toast.makeText(getActivity(), newValue + " selected", Toast.LENGTH_SHORT).show();
            return true;
        });
        // Connection Preferences
        CheckBoxPreference auto_connect_startup = (CheckBoxPreference) preferenceManager.findPreference("auto_connect_startup");
        auto_connect_startup.setOnPreferenceChangeListener((preference, newValue) -> {
            String autoConnect = newValue.toString();
            if (autoConnect.equals("true"))
            {
                Toast.makeText(getActivity(), "Auto Connect on startup enabled", Toast.LENGTH_SHORT).show();
            }
            else
            {
                Toast.makeText(getActivity(), "Auto Connect on startup disabled", Toast.LENGTH_SHORT).show();
            }
            return true;
        });
        CheckBoxPreference auto_connect = (CheckBoxPreference) preferenceManager.findPreference("auto_connect");
        auto_connect.setOnPreferenceChangeListener((preference, newValue) -> {
            String autoConnect = newValue.toString();
            if (autoConnect.equals("true"))
            {
                Toast.makeText(getActivity(), "Auto Connect enabled", Toast.LENGTH_SHORT).show();
            }
            else
            {
                Toast.makeText(getActivity(), "Auto Connect disabled", Toast.LENGTH_SHORT).show();
            }
            return true;
        });
        EditTextPreference connection_timeout = (EditTextPreference) preferenceManager.findPreference("auto_connect_timeout");
        connection_timeout.setOnPreferenceChangeListener((preference, newValue) -> {
            boolean allOK = numberCheck(newValue);
            if (allOK)
            {
                Toast.makeText(getActivity(), newValue + " second timeout set", Toast.LENGTH_SHORT).show();
                return true;
            }
            else
            {
                Toast.makeText(getActivity(), getString(R.string.invalid_timeout), Toast.LENGTH_LONG).show();
                return false;
            }
        });
        CheckBoxPreference show_only_smart_dog_devices = (CheckBoxPreference) preferenceManager.findPreference("show_only_smart_dog_devices");
        show_only_smart_dog_devices.setOnPreferenceChangeListener((preference, newValue) -> {
            boolean showSmartDogOnly = (boolean) newValue;
            editor.putBoolean("show_only_smart_dog_devices", showSmartDogOnly);
            editor.apply();
            if (showSmartDogOnly)
                Toast.makeText(getActivity(), "List only Smart Dog devices enabled", Toast.LENGTH_SHORT).show();
            else
                Toast.makeText(getActivity(), "List only Smart Dog devices disabled", Toast.LENGTH_SHORT).show();
            return true;
        });
        CheckBoxPreference show_actionbar_menu = (CheckBoxPreference) preferenceManager.findPreference("show_actionbar_menu");
        show_actionbar_menu.setOnPreferenceChangeListener((preference, newValue) -> {
            boolean showActionBarMenu = (boolean) newValue;
            editor.putBoolean("show_actionbar_menu", showActionBarMenu);
            editor.apply();
            if (showActionBarMenu)
                Toast.makeText(getActivity(), "Show Actionbar Menu in Device Control Screen", Toast.LENGTH_SHORT).show();
            else
                Toast.makeText(getActivity(), "Hide Actionbar Menu in Device Control Screen", Toast.LENGTH_SHORT).show();
            return true;
        });
    }

    private boolean numberCheck(Object newValue) {
        int timeout;
        try {
            timeout = Integer.parseInt(newValue.toString());
        }
        catch (NumberFormatException e)
        {
            return false;
        }
        return timeout >= 5 && timeout <= 30;
    }
}