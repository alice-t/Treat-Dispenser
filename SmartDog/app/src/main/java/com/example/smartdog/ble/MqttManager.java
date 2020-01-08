package com.example.smartdog.ble;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;
import com.example.smartdog.R;
import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.android.service.MqttTraceHandler;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;


public class MqttManager implements IMqttActionListener, MqttCallback, MqttTraceHandler {
    // Log
    private final static String TAG = MqttManager.class.getSimpleName();

    // Singleton
    private static MqttManager instance = null;

    // Types
    public enum MqqtConnectionStatus {
        CONNECTING,
        CONNECTED,
        DISCONNECTING,
        DISCONNECTED,
        ERROR,
        NONE
    }

    public static int MqqtQos_AtMostOnce = 0;

    // Data
    private MqttAndroidClient mqttClient;
    private MqttManagerListener listener;
    private MqqtConnectionStatus mqqtClientStatus = MqqtConnectionStatus.NONE;
    private Context context;

    public static MqttManager getInstance(Context context) {
        if (instance == null) {
            instance = new MqttManager(context);
        }
        return instance;
    }

    public MqttManager(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public void finalize() throws Throwable {

        try {
            if (mqttClient != null) {
                mqttClient.unregisterResources();
            }
        } finally {
            super.finalize();
        }
    }

    public void setListener(MqttManagerListener listener) {
        this.listener = listener;
    }

    // region MQTT
    public void subscribe(String topic, int qos) {
        if (mqttClient != null && mqqtClientStatus == MqqtConnectionStatus.CONNECTED && topic != null) {
            try {
                Log.d(TAG, "Mqtt: subscribe to " + topic + " qos:" + qos);
                mqttClient.subscribe(topic, qos);
            } catch (MqttException e) {
                Log.e(TAG, "Mqtt:x subscribe error: ", e);
            }
        }
    }

    public void publish(String topic, String payload, int qos) {
        if (mqttClient != null && mqqtClientStatus == MqqtConnectionStatus.CONNECTED && topic != null) {
            try {
                Log.d(TAG, "Mqtt: publish " + payload + " for topic " + topic + " qos:" + qos);
                mqttClient.publish(topic, payload.getBytes(), qos, false, null, null);
            } catch (MqttException e) {
                Log.e(TAG, "Mqtt:x publish error: ", e);
            }
        }
    }

    public void disconnect() {
        if (mqttClient != null && mqqtClientStatus == MqqtConnectionStatus.CONNECTED) {
            try {
                Log.d(TAG, "Mqtt: disconnect");
                mqqtClientStatus = MqqtConnectionStatus.DISCONNECTED;      // Note: it seems that the disconnected callback is never invoked. So we fake here that the final state is disconnected
                mqttClient.disconnect(null, this);

                mqttClient.unregisterResources();
                mqttClient = null;
            } catch (MqttException e) {
                Log.e(TAG, "Mqtt:x disconnection error: ", e);
            }
        }

    }

    // endregion

    // region IMqttActionListener
    @Override
    public void onSuccess(IMqttToken iMqttToken) {
        if (mqqtClientStatus == MqqtConnectionStatus.CONNECTING) {
            Log.d(TAG, "Mqtt connect onSuccess");
            mqqtClientStatus = MqqtConnectionStatus.CONNECTED;
           // if (listener != null) listener.onMqttConnected();

            MqttSettings settings = MqttSettings.getInstance(context);
            String topic = settings.getSubscribeTopic();
            int topicQos = settings.getSubscribeQos();
            if (settings.isSubscribeEnabled() && topic != null) {
                subscribe(topic, topicQos);
            }
        } else if (mqqtClientStatus == MqqtConnectionStatus.DISCONNECTING) {
            Log.d(TAG, "Mqtt disconnect onSuccess");
            mqqtClientStatus = MqqtConnectionStatus.DISCONNECTED;
        } else {
            Log.d(TAG, "Mqtt unknown onSuccess");
        }
    }

    @Override
    public void onFailure(IMqttToken iMqttToken, Throwable throwable) {
        Log.d(TAG, "Mqtt onFailure. " + throwable);

        // Remove the auto-connect till the failure is solved
        if (mqqtClientStatus == MqqtConnectionStatus.CONNECTING) {
            MqttSettings.getInstance(context).setConnectedEnabled(false);
        }

        // Set as an error
        mqqtClientStatus = MqqtConnectionStatus.ERROR;
        String errorText = context.getString(R.string.mqtt_connection_failed)+". "+throwable.getLocalizedMessage();
        Toast.makeText(context, errorText, Toast.LENGTH_LONG).show();

    }
    // endregion

    // region MqttCallback
    @Override
    public void connectionLost(Throwable throwable) {
        Log.d(TAG, "Mqtt connectionLost. " + throwable);

        if (throwable != null) {        // if disconnected because a reason show toast. Standard disconnect will have a null throwable
            Toast.makeText(context, R.string.mqtt_connection_lost, Toast.LENGTH_LONG).show();
        }

        mqqtClientStatus = MqqtConnectionStatus.DISCONNECTED;
    }

    @Override
    public void messageArrived(String topic, MqttMessage mqttMessage) throws Exception {
        String message = new String(mqttMessage.getPayload());

        if (message.length() > 0) {      // filter cleared messages (to avoid duplicates)

            Log.d(TAG, "Mqtt messageArrived from topic: " + topic + " message: " + message + " isDuplicate: " + (mqttMessage.isDuplicate() ? "yes" : "no"));
            if (listener != null) {
                listener.onMqttMessageArrived(mqttMessage);
            }
            // Fix duplicated messages clearing the received payload and processing only non null messages
            mqttMessage.clearPayload();
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {
        Log.d(TAG, "Mqtt deliveryComplete");

    }

    // endregion

    // region MqttTraceHandler
    @Override
    public void traceDebug(String source, String message) {
        Log.d(TAG, "Mqtt traceDebug");

    }

    @Override
    public void traceError(String source, String message) {
        Log.d(TAG, "Mqtt traceError");
    }

    @Override
    public void traceException(String source, String message, Exception e) {
        Log.d(TAG, "Mqtt traceException");
    }

    // endregion


    public interface MqttManagerListener {

        void onMqttMessageArrived(MqttMessage mqttMessage);
    }
}
