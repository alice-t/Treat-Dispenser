package com.example.smartdog.app;

import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.PreferenceManager;

import com.example.smartdog.R;
import com.example.smartdog.ble.BleManager;
import com.example.smartdog.ble.BleUtils;
import com.example.smartdog.ble.MqttManager;
import com.xw.repo.BubbleSeekBar;

import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.util.ArrayList;

public class DeviceControlActivity extends UartInterfaceActivity implements MqttManager.MqttManagerListener {

    private final static String TAG = DeviceControlActivity.class.getSimpleName();

    // Configuration
    private final static boolean useColorsForData = true;
    private final static int infoColor = Color.parseColor("#F21625");

    private LinearLayout deviceSettingsLayout;
    private LinearLayout controlLayout;
    private RadioButton rb5;
    private RadioButton rb7;
    private RadioButton rb9;
    private RadioButton rb11;
    private boolean notifyTreatGiven;
    private boolean notifyLastTreat;
    private boolean notifyNoTreat;
    private CheckBox notifyTreatDispensedCB;
    private CheckBox notifyNoTreatCB;
    private CheckBox notifyLastTreatCB;
    private ProgressBar progressBar;
    private TextView capacityTextView;
    private TextView timeoutSeekBarTextView;
    private TextView offsetSeekBarTextView;
    private int remainingTreats;
    private int dispenserCapacity;
    private BubbleSeekBar offsetSeekBar;
    private BubbleSeekBar timeoutSeekBar;
    private BubbleSeekBar longPressDelaySeekBar;
    private int servoOffset;
    private int servoTimeout;
    private int switchLongPressDelay;
    private int dispenserVersion = 1; // currently 1 or 2 (with carousel lever switch)
    private double voltage;
    private ImageView batteryIcon;
    private SoundManager soundManager;

    private static final int activityRequestCode_ConnectedSettingsActivity = 0;

    private final Object monitor = new Object();
    private boolean responseReceived = true;
    private int txColor;

    private EditText bufferTextView;
    private TextView sentBytesTextView;
    private TextView receivedBytesTextView;

    private Handler uIRefreshTimerHandler = new Handler();
    private Runnable uIRefreshTimerRunnable = new Runnable() {
        @Override
        public void run() {
            if (isUITimerRunning) {
                updateTextDataUI();
                uIRefreshTimerHandler.postDelayed(this, 200);
            }
        }
    };
    private boolean isUITimerRunning = false;

    private volatile SpannableStringBuilder textSpanBuffer;
    private volatile ArrayList<UartDataChunk> dataBuffer;
    private volatile int sentBytes;
    private volatile int receivedBytes;

    private DeviceControlActivity.DataFragment retainedDataFragment;

    private MqttManager mqttManager;

    private static final int maxPacketsToPaintAsText = 500;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_control);
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean showActionbarMenu = sharedPreferences.getBoolean("show_actionbar_menu", true);
        if (showActionbarMenu)
        {
            Toolbar toolbar = findViewById(R.id.toolbar);
            setSupportActionBar(toolbar);
        }
        progressBar = findViewById(R.id.progressBar);
        offsetSeekBar = findViewById(R.id.offsetSeekBar);
        offsetSeekBar.setProgress(servoOffset);
        offsetSeekBar.setOnProgressChangedListener(new BubbleSeekBar.OnProgressChangedListener()
        {
            @Override
            public void onProgressChanged(BubbleSeekBar bubbleSeekBar, int progress, float progressFloat, boolean fromUser)
            {
                Log.v(TAG, String.format("%s %d", "ServoOffset On change", progress));
            }

            @Override
            public void getProgressOnActionUp(BubbleSeekBar bubbleSeekBar, int progress, float progressFloat)
            {
                servoOffset = progress + 100;
                uartSendData("ServoOffset=" + servoOffset);
                Log.v(TAG, String.format("%s %d %d", "ServoOffset getProgressOnActionUp", progress, servoOffset));
            }

            @Override
            public void getProgressOnFinally(BubbleSeekBar bubbleSeekBar, int progress, float progressFloat, boolean fromUser)
            {
                Log.v(TAG, String.format("%s %d", "ServoOffset getProgressOnFinally", progress));
            }
        });

        timeoutSeekBar = findViewById(R.id.timeoutSeekBar);
        timeoutSeekBar.setProgress(servoOffset);
        timeoutSeekBar.setOnProgressChangedListener(new BubbleSeekBar.OnProgressChangedListener()
        {
            @Override
            public void onProgressChanged(BubbleSeekBar bubbleSeekBar, int progress, float progressFloat, boolean fromUser)
            {
                Log.v(TAG, String.format("%s %d", "TimeoutSeekBar On change", progress));
            }

            @Override
            public void getProgressOnActionUp(BubbleSeekBar bubbleSeekBar, int progress, float progressFloat)
            {
                servoTimeout =  Math.round(progressFloat * 1000);
                uartSendData("RotTimeout=" + servoTimeout);
                Log.v(TAG, String.format("%s %d %d", "TimeoutSeekBar getProgressOnActionUp", progressFloat, servoTimeout));
            }

            @Override
            public void getProgressOnFinally(BubbleSeekBar bubbleSeekBar, int progress, float progressFloat, boolean fromUser)
            {
                Log.v(TAG, String.format("%s %f", "TimeoutSeekBar getProgressOnFinally", progressFloat));
            }
        });

        longPressDelaySeekBar = findViewById(R.id.longPressSeekBar);
        longPressDelaySeekBar.setProgress((float) switchLongPressDelay /1000);
        longPressDelaySeekBar.setOnProgressChangedListener(new BubbleSeekBar.OnProgressChangedListener()
        {
            @Override
            public void onProgressChanged(BubbleSeekBar bubbleSeekBar, int progress, float progressFloat, boolean fromUser)
            {
                Log.v(TAG, String.format("%s %f", "SwitchLongPressDelay On change", progressFloat));
            }

            @Override
            public void getProgressOnActionUp(BubbleSeekBar bubbleSeekBar, int progress, float progressFloat)
            {
                switchLongPressDelay =  Math.round(progressFloat * 1000);
                uartSendData("LongPressDelay=" + switchLongPressDelay);
                Log.v(TAG, String.format("%s %f %d", "SwitchLongPressDelay getProgressOnActionUp", progressFloat, switchLongPressDelay));
            }

            @Override
            public void getProgressOnFinally(BubbleSeekBar bubbleSeekBar, int progress, float progressFloat, boolean fromUser)
            {
                Log.v(TAG, String.format("%s %f", "SwitchLongPressDelay getProgressOnFinally", progressFloat));
            }
        });
        deviceSettingsLayout = findViewById(R.id.deviceSettingsContainer);
        rb5 = findViewById(R.id.radio_five);
        rb7 = findViewById(R.id.radio_seven);
        rb9 = findViewById(R.id.radio_nine);
        rb11 = findViewById(R.id.radio_eleven);
        notifyTreatDispensedCB = findViewById(R.id.notifyTreatDispensedCB);
        notifyTreatDispensedCB.setOnClickListener(view -> {
            if (notifyTreatDispensedCB.isChecked() && !notifyTreatGiven)
            {
                uartSendData("NotifyTreat=true");
                notifyTreatGiven = true;
            }
            else if (!notifyTreatDispensedCB.isChecked() && notifyTreatGiven)
            {
                uartSendData("NotifyTreat=false");
                notifyTreatGiven = false;
            }
        });
        notifyLastTreatCB = findViewById(R.id.notifyLastTreatCB);
        notifyLastTreatCB.setOnClickListener(view -> {
            if (notifyLastTreatCB.isChecked() && !notifyLastTreat)
            {
                uartSendData("NotifyLast=true");
                notifyLastTreat = true;
            }
            else if (!notifyLastTreatCB.isChecked() && notifyLastTreat)
            {
                uartSendData("NotifyLast=false");
                notifyLastTreat = false;
            }
        });
        notifyNoTreatCB = findViewById(R.id.notifyNoTreatCB);
        notifyNoTreatCB.setOnClickListener(view -> {
            if (notifyNoTreatCB.isChecked() && !notifyNoTreat)
            {
                uartSendData("NotifyNoTreat=true");
                notifyNoTreat = true;
            }
            else if (!notifyNoTreatCB.isChecked() && notifyNoTreat)
            {
                uartSendData("NotifyNoTreat=false");
                notifyNoTreat = false;
            }
        });
        controlLayout = findViewById(R.id.control_layout);
        //remainingTreats = dispenserCapacity;
        progressBar.setMax(dispenserCapacity);
        batteryIcon = findViewById(R.id.batteryIcon);
        capacityTextView = findViewById(R.id.capacityTextView);
        timeoutSeekBarTextView = findViewById(R.id.timeoutSeekBarText);
        offsetSeekBarTextView = findViewById(R.id.offsetSeekBarText);
        capacityTextView.setText("Remaining Treats = " + remainingTreats + "/" + dispenserCapacity);
        soundManager = SoundManager.getInstance(this);
        bleManager = BleManager.getInstance(this);
        restoreRetainedDataFragment();

        TypedValue typedValue = new TypedValue();
        Resources.Theme theme = getTheme();
        theme.resolveAttribute(R.attr.colorPrimaryDark, typedValue, true);
        txColor = typedValue.data;
        theme.resolveAttribute(R.attr.colorControlActivated, typedValue, true);

        bufferTextView = findViewById(R.id.bufferTextView);
        if (bufferTextView != null) {
            bufferTextView.setKeyListener(null);     // make it not editable
        }

        sentBytesTextView = findViewById(R.id.sentBytesTextView);
        receivedBytesTextView = findViewById(R.id.receivedBytesTextView);

        invalidateOptionsMenu();

        onServicesDiscovered();

        // Mqtt
        mqttManager = MqttManager.getInstance(this);

        //initialise Device
        new Thread(() -> {
            synchronized(monitor)
            {
                String[] requests = new String[]{"capacity", "remaining", "battery",
                        "notifyTreat", "notifyLastTreat", "notifyNoTreat", "servoOffset",
                        "longPressDelay", "version"};
                // wait for response to each request before sending next
                for (String req: requests)
                {
                    while (!responseReceived)
                    {
                        try {
                            monitor.wait(6000);
                        } catch (Exception e) {
                            Log.e(TAG, e.toString());
                        }
                    }
                    Log.v(TAG, "sent: " + req);
                    uartSendData(req);
                    responseReceived = false;
                }
            }
        }).start();
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
    public void onResume() {
        super.onResume();

        bleManager.setBleListener(this);
        mqttManager.setListener(this);
        updateUI();

        isUITimerRunning = true;
        uIRefreshTimerHandler.postDelayed(uIRefreshTimerRunnable, 0);
    }

    @Override
    public void onPause() {
        super.onPause();
        isUITimerRunning = false;
        uIRefreshTimerHandler.removeCallbacksAndMessages(uIRefreshTimerRunnable);

    }

    @Override
    public void onDestroy() {
        // Disconnect mqtt
        if (mqttManager != null) {
            mqttManager.disconnect();
        }
        // Retain data
        saveRetainedDataFragment();
        super.onDestroy();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)  {
        if (keyCode == KeyEvent.KEYCODE_BACK ) {
            bleManager.disconnect();
            Intent intent = new Intent(getApplicationContext(), MainActivity.class);
            intent.putExtra("activity","DeviceControlActivity");
            startActivity(intent);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    public void dispenseTreat(View v) {
        uartSendData("treat");
    }

    public void checkBattery(View v) {
        uartSendData("battery");
    }

    public void checkStock(View v) {
        uartSendData("remaining");
    }

    public void onExitClick(View v) {
        bleManager.disconnect();
        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
        intent.putExtra("activity","DeviceControlActivity");
        startActivity(intent);
    }

    public void onRadioButtonClicked(View view) {
        // Is the button now checked?
        boolean checked = ((RadioButton) view).isChecked();

        // Check which radio button was clicked
        switch(view.getId()) {
            case R.id.radio_five:
                if (checked && dispenserCapacity != 5)
                {
                    uartSendData("Capacity=5");
                    dispenserCapacity = 5;
                }
                break;
            case R.id.radio_seven:
                if (checked && dispenserCapacity != 7)
                {
                    uartSendData("Capacity=7");
                    dispenserCapacity = 7;
                }
                break;
            case R.id.radio_nine:
                if (checked && dispenserCapacity != 9)
                {
                    uartSendData("Capacity=9");
                    dispenserCapacity = 9;
                }
                break;
            case R.id.radio_eleven:
                if (checked && dispenserCapacity != 11)
                {
                    uartSendData("Capacity=11");
                    dispenserCapacity = 11;
                }
                break;
        }
        progressBar.setMax(dispenserCapacity);
        progressBar.setProgress(remainingTreats);
        capacityTextView.setText("Remaining Treats = " + remainingTreats + "/" + dispenserCapacity);
    }

    private void setCapacity(int capacity)
    {
        rb5.setChecked(false);
        rb7.setChecked(false);
        rb9.setChecked(false);
        rb11.setChecked(false);
        switch(capacity) {
            case 5:
                rb5.setChecked(true);
                break;
            case 7:
                rb7.setChecked(true);
                break;
            case 9:
                rb9.setChecked(true);
                break;
            case 11:
                rb11.setChecked(true);
                break;
        }
    }


    private void setAudio()
    {
        // do something else
        notifyTreatDispensedCB.setChecked(notifyTreatGiven);
        notifyLastTreatCB.setChecked(notifyLastTreat);
        notifyNoTreatCB.setChecked(notifyNoTreat);
    }

    public void onDoneBtnClick(View v) {
        deviceSettingsLayout.setVisibility(View.GONE);
        controlLayout.setVisibility(View.VISIBLE);
    }

    private void uartSendData(String data) {
        // MQTT publish to TX
        mqttManager.publish(null, data, 0);
        sendData(data);
        sentBytes += data.length();
    }

    public void onClickClear(View view) {
        textSpanBuffer.clear();
        dataBufferLastSize = 0;
        bufferTextView.setText("");

        dataBuffer.clear();
        sentBytes = 0;
        receivedBytes = 0;
        updateUI();
    }

    public void onClickReload(View view) {
        uartSendData("reload");
    }

    public  void onClickSettings(View view)
    {
        setAudio();
        controlLayout.setVisibility(View.GONE);
        deviceSettingsLayout.setVisibility(View.VISIBLE);

    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent); // new added
        if (requestCode == activityRequestCode_ConnectedSettingsActivity && resultCode == RESULT_OK) {
            finish();
        }
    }

    // endregion

    // region BleManagerListener
    @Override
    public void onDisconnected() {
        super.onDisconnected();
        Log.d(TAG, "Disconnected. Back to previous activity");
        finish();
    }

    @Override
    public void onServicesDiscovered() {
        super.onServicesDiscovered();
        enableRxNotifications();
    }

    @Override
    public synchronized void onDataAvailable(BluetoothGattCharacteristic characteristic) {
        super.onDataAvailable(characteristic);
        // UART RX
        if (characteristic.getService().getUuid().toString().equalsIgnoreCase(UUID_SERVICE)) {
            if (characteristic.getUuid().toString().equalsIgnoreCase(UUID_RX)) {
                final byte[] bytes = characteristic.getValue();
                receivedBytes += bytes.length;

                final UartDataChunk dataChunk = new UartDataChunk(bytes);
                dataBuffer.add(dataChunk);

                runOnUiThread(this::updateUI);

                // MQTT publish to RX
                final String text = BleUtils.bytesToText(bytes, false);
                mqttManager.publish(null, text, 0);
            }
        }
    }

    // endregion

    private void addTextToSpanBuffer(SpannableStringBuilder spanBuffer, String text, int color) {

        if (useColorsForData) {
            final int from = spanBuffer.length();
            spanBuffer.append(text);
            spanBuffer.setSpan(new ForegroundColorSpan(color), from, from + text.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else {
            spanBuffer.append(text);
        }
    }

    private void updateUI() {
        sentBytesTextView.setText(String.format(getString(R.string.sentbytes_format), sentBytes));
        receivedBytesTextView.setText(String.format(getString(R.string.receivedbytes_format), receivedBytes));
    }

    private int dataBufferLastSize = 0;

    private void updateTextDataUI() {

        if (dataBufferLastSize != dataBuffer.size())
        {

            final int bufferSize = dataBuffer.size();
            if (bufferSize > maxPacketsToPaintAsText)
            {
                dataBufferLastSize = bufferSize - maxPacketsToPaintAsText;
                textSpanBuffer.clear();
                addTextToSpanBuffer(textSpanBuffer, getString(R.string.uart_text_dataomitted) + "\n", infoColor);
            }

            for (int i = dataBufferLastSize; i < bufferSize; i++)
            {
                final UartDataChunk dataChunk = dataBuffer.get(i);
                final byte[] bytes = dataChunk.getData();
                final String formattedData = BleUtils.bytesToText(bytes, true);
                addTextToSpanBuffer(textSpanBuffer, formattedData + '\n', txColor);
                SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
                if (formattedData.equals("Treat Given"))
                {
                    String sound = sharedPreferences.getString("dispensedSound", "electro chirp");
                    if (sound != null && !sound.equals("No Sound"))
                    {
                        soundManager.playSample(sound);
                    }
                }
                else if (formattedData.startsWith("Treats="))
                {
                    String intStr = formattedData.substring(formattedData.lastIndexOf('=') + 1);
                    try
                    {
                        remainingTreats = Integer.parseInt(intStr);
                    } catch (NumberFormatException e)
                    {
                        Log.v(TAG, e.toString());
                    }
                    progressBar.setProgress(remainingTreats);
                    if (remainingTreats <= 3)
                        capacityTextView.setTextColor(Color.parseColor("#ff0000"));
                    else
                        capacityTextView.setTextColor(getResources().getColor(R.color.default_textcolor));

                    capacityTextView.setText("Remaining Treats = " + remainingTreats + "/" + dispenserCapacity);
                    if (remainingTreats == 0)
                        Toast.makeText(getApplicationContext(), "Treat dispenser empty!", Toast.LENGTH_LONG).show();
                    Log.v(TAG, "Treats");
                    synchronized (monitor)
                    {
                        responseReceived = true;
                        monitor.notify();
                    }
                }
                else if (formattedData.equals("No Treats"))
                {
                    String sound = sharedPreferences.getString("failedSound", "uh-oh");
                    if (sound != null && !sound.equals("No Sound"))
                    {
                        soundManager.playSample(sound);
                    }
                }
                else if (formattedData.startsWith("Capacity="))
                {
                    String intStr = formattedData.substring(formattedData.lastIndexOf('=') + 1);
                    try
                    {
                        dispenserCapacity = Integer.parseInt(intStr);
                    } catch (NumberFormatException e)
                    {
                        Log.v(TAG, e.toString());
                    }
                    setCapacity(dispenserCapacity);
                    Log.v(TAG, "Capacity");
                    synchronized (monitor)
                    {
                        responseReceived = true;
                        monitor.notify();
                    }
                    progressBar.setMax(dispenserCapacity);
                    progressBar.setProgress(remainingTreats);
                }
                else if (formattedData.startsWith("ServoOffset="))
                {
                    String intStr = formattedData.substring(formattedData.lastIndexOf('=') + 1);
                    try
                    {
                        servoOffset = Integer.parseInt(intStr);
                    } catch (NumberFormatException e)
                    {
                        Log.v(TAG, e.toString());
                    }
                    offsetSeekBar.setProgress(servoOffset - 100);
                    synchronized (monitor)
                    {
                        responseReceived = true;
                        monitor.notify();
                    }
                }
                else if (formattedData.startsWith("RotTimeout="))
                {
                    String intStr = formattedData.substring(formattedData.lastIndexOf('=') + 1);
                    try
                    {
                        servoTimeout = Integer.parseInt(intStr);
                    } catch (NumberFormatException e)
                    {
                        Log.v(TAG, e.toString());
                    }
                    timeoutSeekBar.setProgress((float) servoTimeout /1000);
                    synchronized (monitor)
                    {
                        responseReceived = true;
                        monitor.notify();
                    }
                }
                else if (formattedData.startsWith("LongPressDelay="))
                {
                    String intStr = formattedData.substring(formattedData.lastIndexOf('=') + 1);
                    try
                    {
                        switchLongPressDelay = Integer.parseInt(intStr);
                    } catch (NumberFormatException e)
                    {
                        Log.v(TAG, e.toString());
                    }
                    longPressDelaySeekBar.setProgress((float) switchLongPressDelay /1000);
                    synchronized (monitor)
                    {
                        responseReceived = true;
                        monitor.notify();
                    }
                }
                else if (formattedData.startsWith("Version="))
                {
                    String intStr = formattedData.substring(formattedData.lastIndexOf('=') + 1);
                    try
                    {
                        dispenserVersion = Integer.parseInt(intStr);
                    } catch (NumberFormatException e)
                    {
                        Log.v(TAG, e.toString());
                    }
                    if (dispenserVersion == 1)
                    {
                        timeoutSeekBarTextView.setVisibility(View.GONE);
                        timeoutSeekBar.setVisibility(View.GONE);
                        offsetSeekBarTextView.setVisibility(View.VISIBLE);
                        offsetSeekBar.setVisibility(View.VISIBLE);
                        offsetSeekBar.setProgress(servoOffset - 100);
                    }
                    else
                    {
                        offsetSeekBarTextView.setVisibility(View.GONE);
                        offsetSeekBar.setVisibility(View.GONE);
                        timeoutSeekBarTextView.setVisibility(View.VISIBLE);
                        timeoutSeekBar.setVisibility(View.VISIBLE);
                        timeoutSeekBar.setProgress((float) servoTimeout /1000);
                    }
                    synchronized (monitor)
                    {
                        responseReceived = true;
                        monitor.notify();
                    }
                }
                else if (formattedData.startsWith("Set ServoOffset")
                        || formattedData.startsWith("Set LongPressDelay")
                        || formattedData.startsWith("Set RotTimeout")
                        || formattedData.startsWith("Set Version")
                        || formattedData.startsWith("Set NotifyTreat")
                        || formattedData.startsWith("Set NotifyLast")
                        || formattedData.startsWith("Set NotifyNoTreat"))
                {
                    Log.v(TAG, "Update Acknowledged");
                    synchronized (monitor)
                    {
                        responseReceived = true;
                        monitor.notify();
                    }
                }
                else if (formattedData.startsWith("Set Capacity"))
                {
                    if (remainingTreats > dispenserCapacity)
                    {
                        remainingTreats = dispenserCapacity;
                        capacityTextView.setText("Remaining Treats = " + remainingTreats + "/" + dispenserCapacity);
                    }
                }
                else if (formattedData.startsWith("NotifyTreat="))
                {
                    String notifyTreatGivenStr = formattedData.substring(formattedData.lastIndexOf('=') + 1);
                    notifyTreatGiven = notifyTreatGivenStr.equals("1");
                    synchronized (monitor)
                    {
                        responseReceived = true;
                        monitor.notify();
                    }
                }
                else if (formattedData.startsWith("NotifyLast="))
                {
                    String notifyLastTreatStr = formattedData.substring(formattedData.lastIndexOf('=') + 1);
                    notifyLastTreat = notifyLastTreatStr.equals("1");
                    synchronized (monitor)
                    {
                        responseReceived = true;
                        monitor.notify();
                    }
                }
                else if (formattedData.startsWith("NotifyNoTreat="))
                {
                    String notifyNoTreatStr = formattedData.substring(formattedData.lastIndexOf('=') + 1);
                    notifyNoTreat = notifyNoTreatStr.equals("1");
                    synchronized (monitor)
                    {
                        responseReceived = true;
                        monitor.notify();
                    }
                }
                else if (formattedData.startsWith("Voltage="))
                {
                    String doubleStr = formattedData.substring(formattedData.lastIndexOf('=') + 1, formattedData.length() - 1);
                    try
                    {
                        voltage = Double.parseDouble(doubleStr);
                    } catch (NumberFormatException e)
                    {
                        Log.v(TAG, e.toString());
                    }
                    if (voltage > 5.1)
                    {
                        batteryIcon.setImageResource(R.drawable.battery4);
                    }
                    else if (voltage > 4.9)
                    {
                        batteryIcon.setImageResource(R.drawable.battery3);
                    }
                    else if (voltage > 4.7)
                    {
                        batteryIcon.setImageResource(R.drawable.battery2);
                    }
                    else if (voltage > 4.5)
                    {
                        batteryIcon.setImageResource(R.drawable.battery1);
                    }
                    else
                    {
                        batteryIcon.setImageResource(R.drawable.battery0);
                    }
                    Log.v(TAG, "Voltage");
                    synchronized (monitor)
                    {
                        responseReceived = true;
                        monitor.notify();
                    }
                }
                dataChunk.setProcessed(true);
            }
            dataBufferLastSize = dataBuffer.size();
            bufferTextView.setText(textSpanBuffer);
            bufferTextView.setSelection(0, textSpanBuffer.length());        // to automatically scroll to the end
        }
    }

    // region DataFragment
    public static class DataFragment extends Fragment
    {
        private SpannableStringBuilder mTextSpanBuffer;
        private ArrayList<UartDataChunk> mDataBuffer;
        private int mSentBytes;
        private int mReceivedBytes;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setRetainInstance(true);
        }
    }

    private void restoreRetainedDataFragment() {
        // find the retained fragment
        FragmentManager fm = getSupportFragmentManager();
        retainedDataFragment = (DeviceControlActivity.DataFragment) fm.findFragmentByTag(TAG);

        if (retainedDataFragment == null) {
            // Create
            retainedDataFragment = new DeviceControlActivity.DataFragment();
            fm.beginTransaction().add(retainedDataFragment, TAG).commit();

            dataBuffer = new ArrayList<>();
            textSpanBuffer = new SpannableStringBuilder();
        } else {
            // Restore status
            textSpanBuffer = retainedDataFragment.mTextSpanBuffer;
            dataBuffer = retainedDataFragment.mDataBuffer;
            sentBytes = retainedDataFragment.mSentBytes;
            receivedBytes = retainedDataFragment.mReceivedBytes;
        }
    }

    private void saveRetainedDataFragment() {
        retainedDataFragment.mTextSpanBuffer = textSpanBuffer;
        retainedDataFragment.mDataBuffer = dataBuffer;
        retainedDataFragment.mSentBytes = sentBytes;
        retainedDataFragment.mReceivedBytes = receivedBytes;
    }
    // endregion

    // region MqttManagerListener

    @Override
    public void onMqttMessageArrived(MqttMessage mqttMessage) {
        final String message = new String(mqttMessage.getPayload());

        runOnUiThread(() -> {
            uartSendData(message);       // Don't republish to mqtt something received from mqtt
        });
    }

    // endregion

}

