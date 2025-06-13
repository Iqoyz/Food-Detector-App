package com.android.service;

import android.content.Context;
import android.util.Log;

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MqttDefaultFilePersistence;
import org.json.JSONObject;

import java.util.UUID;

public class MqttClient {
    private static final String TAG = "MQTT";
    private static final String MQTT_BROKER_URL = "tcp://broker.hivemq.com:1883";
    private static final String TOPIC_IMAGE = "project/images";
    private static final String TOPIC_PREDICTIONS = "project/predictions";

    private boolean isConnected = false;
    private MqttAsyncClient mqttAsyncClient;

    private final Context context;
    private final MqttConnectionListener connectionListener;

    public interface MqttConnectionListener {
        void onConnected();

        void onConnectionFailed(String error);
    }

    public MqttClient(Context context, MqttCallback callback, MqttConnectionListener listener) {
        this.context = context;
        this.connectionListener = listener;
        try {
            String persistenceDir = context.getFilesDir().getAbsolutePath();
            mqttAsyncClient = new MqttAsyncClient(MQTT_BROKER_URL, UUID.randomUUID().toString(),
                    new MqttDefaultFilePersistence(persistenceDir));
            mqttAsyncClient.setCallback(callback);

            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);

            mqttAsyncClient.connect(options, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.d(TAG, "Connected to MQTT broker");
                    isConnected = true;

                    if (connectionListener != null) {
                        connectionListener.onConnected();
                    }

                    subscribeToPredictions();
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.e(TAG, "Failed to connect to MQTT broker: " + exception.getMessage());
                    isConnected = false;

                    if (connectionListener != null) {
                        connectionListener.onConnectionFailed(exception.getMessage());
                    }
                }
            });

        } catch (MqttException e) {
            e.printStackTrace();
            if (connectionListener != null) {
                connectionListener.onConnectionFailed(e.getMessage());
            }
        }
    }

    public boolean isConnected() {
        return isConnected;
    }

    public void publishJson(String topic, String jsonPayload) {
        if (mqttAsyncClient != null && isConnected) {
            try {
                MqttMessage message = new MqttMessage(jsonPayload.getBytes("UTF-8"));
                mqttAsyncClient.publish(topic, message);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            Log.e(TAG, "MQTT client not connected, cannot publish message.");
        }
    }



    public void publishImage(byte[] imageBytes) {
        try {
            if (mqttAsyncClient != null && mqttAsyncClient.isConnected()) {
                String clientId = mqttAsyncClient.getClientId();
                String base64Image = android.util.Base64.encodeToString(imageBytes, android.util.Base64.DEFAULT);

                JSONObject payload = new JSONObject();
                payload.put("client_id", clientId);
                payload.put("image_data", base64Image);

                MqttMessage message = new MqttMessage(payload.toString().getBytes("UTF-8"));
                mqttAsyncClient.publish(TOPIC_IMAGE, message);
            } else {
                Log.e(TAG, "Cannot publish, MQTT client not connected");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }



    private void subscribeToPredictions() {
        try {
            mqttAsyncClient.subscribe(TOPIC_PREDICTIONS, 0);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public String getClientId() {
        if (mqttAsyncClient != null) {
            return mqttAsyncClient.getClientId();
        }
        return null;
    }

    public void disconnect() {
        try {
            if (mqttAsyncClient != null) {
                mqttAsyncClient.disconnect();
            }
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }
}
