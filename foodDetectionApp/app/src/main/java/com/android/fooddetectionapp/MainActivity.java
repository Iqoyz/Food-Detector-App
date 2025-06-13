package com.android.fooddetectionapp;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.android.fooddetectionapp.databinding.ActivityMainBinding;
import com.android.service.MqttClient;

import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;


public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private static final int CAMERA_PERMISSION_CODE = 101;

    private String latestPhotoPath;
    private ImageView capturedImage;
    private TextView descriptionText;
    private EditText alternativeInput;
    private Button confirmButton;
    private DrawBoundingBoxView drawBoundingBoxView;

    private LinearLayout predictionContainer;

    private ActivityResultLauncher<Intent> galleryLauncher;

    private ActivityResultLauncher<Intent> cameraLauncher;

    private float[] boundingBoxCoordinates;

    private int imageId;

    private MqttClient mqttClient;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);

        // Initialize ImageView
        capturedImage = findViewById(R.id.capturedImage);
        descriptionText = findViewById(R.id.descriptionText);

        alternativeInput = findViewById(R.id.alternativeInput);
        confirmButton = findViewById(R.id.confirmButton);

        predictionContainer = findViewById(R.id.predictionContainer);
        registerActivityLauncher();
        // Check Camera Permissions
        checkPermissions();

        // FAB Click Listener to Open Camera
        binding.fabCamera.setOnClickListener(view -> openCamera());
        binding.fabGallery.setOnClickListener(view -> openGallery());

        // Set submit button action
        confirmButton.setOnClickListener(v -> {
            sendResultToServer();
        });

        drawBoundingBoxView = findViewById(R.id.drawBoundingBoxView);

        mqttClient = new MqttClient(this, new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                runOnUiThread(() -> descriptionText.setText("MQTT connection lost"));
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                String payload = new String(message.getPayload());
                Log.d("Payload", payload);
                try {
                    JSONObject json = new JSONObject(payload);
                    String receivedClientId = json.optString("client_id");
                    String prediction = json.optString("prediction");

                    // Compare to this client's own ID
                    String myClientId = mqttClient.getClientId();

                    if (receivedClientId.equals(myClientId)) {
                        runOnUiThread(() -> handlePredictionResponse(prediction));
                    } else {
                        Log.d("mainActivity", "Ignored prediction for client: " + receivedClientId);
                    }

                } catch (JSONException e) {
                    e.printStackTrace();
                    Log.e("mainActivity", "Failed to parse prediction JSON: " + payload);
                }
            }


            @Override
            public void deliveryComplete(org.eclipse.paho.client.mqttv3.IMqttDeliveryToken token) {
            }
        }, new MqttClient.MqttConnectionListener() {
            @Override
            public void onConnected() {
                runOnUiThread(() -> {
                    binding.fabCamera.setEnabled(true);
                    binding.fabGallery.setEnabled(true);
                    descriptionText.append("\nServer(MQTT) connected.");
                });
            }

            @Override
            public void onConnectionFailed(String error) {
                runOnUiThread(() -> descriptionText.setText("MQTT connection failed: " + error));
            }
        });


    }

    private void registerActivityLauncher() {
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri selectedImageUri = result.getData().getData();
                        if (selectedImageUri != null) {
                            capturedImage.setImageURI(selectedImageUri);
                            capturedImage.setVisibility(View.VISIBLE);
                            descriptionText.setText("Selected Image from Gallery");
                            descriptionText.setVisibility(View.VISIBLE);

                            saveGalleryImageUriToPreference(selectedImageUri.toString());
                            latestPhotoPath = selectedImageUri.toString();

                            try {
                                Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), selectedImageUri);
                                sendImageForPrediction(bitmap);
                            } catch (IOException e) {
                                descriptionText.setText("Error loading image");
                            }
                        }
                    }
                }
        );

        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        loadLastCapturedImage();
                        descriptionText.setText("Image taken from Camera");
                        descriptionText.setVisibility(View.VISIBLE);
                    }
                }
        );


    }

    private void checkPermissions() {
        List<String> permissionsToRequest = new ArrayList<>();

        // 🔹 Check Camera Permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.CAMERA);
        }

        // 🔹 Check Storage Permission (API 32 and below)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {  // Android 32 and below
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        }
        // 🔹 Check Storage Permission (API 33+)
        else { // Android 33 and above
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES);
            }
        }

        // 🔹 Request Permissions if Needed
        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    permissionsToRequest.toArray(new String[0]), CAMERA_PERMISSION_CODE);
        }
    }


    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*");
        galleryLauncher.launch(intent);
    }


    private void openCamera() {

        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);  // ✅ Use the correct intent

        File photoFile = null;
        try {
            photoFile = createImageFile();
        } catch (IOException ex) {
            Log.d("DEBUG", "Failed to create image file");
            ex.printStackTrace();
        }

        if (photoFile != null) {
            Uri photoURI = FileProvider.getUriForFile(this, "com.android.fooddetectionapp.fileprovider", photoFile);
            cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
            cameraLauncher.launch(cameraIntent);
        }
    }


    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(imageFileName, ".jpg", storageDir);
        saveCameraImagePathToPreference(image.getAbsolutePath());
        latestPhotoPath = image.getAbsolutePath();
        return image;
    }

    private void saveCameraImagePathToPreference(String imageUri) {
        SharedPreferences prefs = getSharedPreferences("ImagePrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("capturedImagePath", imageUri);
        editor.apply();

        Log.d("DEBUG", "Saved Image Path: " + imageUri);
    }

    private void saveGalleryImageUriToPreference(String imageUri) {
        SharedPreferences prefs = getSharedPreferences("ImagePrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("galleryImageUri", imageUri);
        editor.apply();
        Log.d("DEBUG", "Saved Gallery Image URI: " + imageUri);
    }

    private Uri getGalleryImageUriFromPreference() {
        SharedPreferences prefs = getSharedPreferences("ImagePrefs", MODE_PRIVATE);
        String uriString = prefs.getString("galleryImageUri", null);
        return uriString != null ? Uri.parse(uriString) : null;
    }


    private void loadLastCapturedImage() {
        SharedPreferences prefs = getSharedPreferences("ImagePrefs", MODE_PRIVATE);
        String imagePath = prefs.getString("capturedImagePath", null);
        Uri galleryImageUri = getGalleryImageUriFromPreference();

        if (galleryImageUri != null && galleryImageUri.toString().equals(latestPhotoPath)) {
            // Load gallery image
            capturedImage.setImageURI(galleryImageUri);
            capturedImage.setVisibility(View.VISIBLE);
            descriptionText.setText("Sending Image for Prediction...");
            descriptionText.setVisibility(View.VISIBLE);

            // Convert URI to Bitmap
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), galleryImageUri);
                sendImageForPrediction(bitmap);
            } catch (IOException e) {
                descriptionText.setText("Error loading image");
            }
        } else if (imagePath != null && imagePath.equals(latestPhotoPath)) {
            // Load camera image
            File imgFile = new File(imagePath);
            if (imgFile.exists()) {
                Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
                capturedImage.setImageBitmap(bitmap);
                capturedImage.setVisibility(View.VISIBLE);
                descriptionText.setVisibility(View.VISIBLE);
                descriptionText.setText("Sending Image for Prediction...");

                // Send Image for Prediction
                sendImageForPrediction(bitmap);
            } else {
                Log.d("DEBUG", "Camera image file not found: " + imagePath);
            }
        } else {
            Log.d("DEBUG", "No image found in SharedPreferences.");
        }
    }


    private void sendImageForPrediction(Bitmap bitmap) {
        if (mqttClient == null || !mqttClient.isConnected()) {
            runOnUiThread(() -> descriptionText.setText("MQTT not connected yet. Please wait..."));
            return;
        }

        // Convert Bitmap to ByteArray
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, byteArrayOutputStream);
        byte[] imageBytes = byteArrayOutputStream.toByteArray();

        // Publish image over MQTT
        mqttClient.publishImage(imageBytes);

        // Optionally show status on UI
        runOnUiThread(() -> descriptionText.setText("Image sent over MQTT, waiting for predictions..."));
    }

    private void handlePredictionResponse(String response) {
        try {
            JSONObject jsonResponse = new JSONObject(response);
            Log.d("mainActivity", jsonResponse.toString());

            if (jsonResponse.has("predictions")) {
                predictionContainer.removeAllViews();
                JSONArray predictions = jsonResponse.getJSONArray("predictions");

                if (predictions.length() > 0) {
                    for (int i = 0; i < predictions.length(); i++) {
                        JSONObject prediction = predictions.getJSONObject(i);
                        String label = prediction.getString("predicted_label");
                        double confidence = prediction.getDouble("confidence");
                        imageId = prediction.getInt("img_id");

                        TextView predictedLabelView = new TextView(MainActivity.this);
                        predictedLabelView.setText(label + " (" + String.format("%.2f", confidence * 100) + "%)");
                        predictionContainer.addView(predictedLabelView);
                    }
                    alternativeInput.setVisibility(View.VISIBLE);
                    confirmButton.setVisibility(View.VISIBLE);
                    descriptionText.setText("Prediction Result");
                }else {
                    // Handle no predictions found
                    descriptionText.setText("No predictions found. :(");
                    alternativeInput.setVisibility(View.VISIBLE);
                    confirmButton.setVisibility(View.VISIBLE);
                }


            } else if (jsonResponse.has("status")) {
                String status = jsonResponse.getString("status");
                if ("success".equals(status)) {
                    descriptionText.setText("[Server ]Confirmed label successfully saved!");
                } else {
                    descriptionText.setText("Received status: " + status);
                }
            } else {
                descriptionText.setText("Unknown response received.");
            }

        } catch (JSONException e) {
            descriptionText.setText("Error parsing MQTT response.");
            e.printStackTrace();
        }
    }


    private void sendResultToServer() {
        List<String> selectedLabels = new ArrayList<>();
        List<float[]> boundingBoxes = new ArrayList<>();

        String manualInput = alternativeInput.getText().toString().trim();
        if (!manualInput.isEmpty()) {
            selectedLabels.add(manualInput);
            setBouncingInfo();
            if (boundingBoxCoordinates == null) {
                boundingBoxCoordinates = new float[]{0, 0, 0, 0}; // Default if not drawn
            }
            boundingBoxes.add(boundingBoxCoordinates);
        }

        if (!selectedLabels.isEmpty()) {
            try {
                JSONArray labelsArray = new JSONArray();
                for (int i = 0; i < selectedLabels.size(); i++) {
                    JSONObject labelObject = new JSONObject();
                    labelObject.put("label", selectedLabels.get(i));

                    JSONArray bboxArray = new JSONArray();
                    if (i < boundingBoxes.size() && boundingBoxes.get(i) != null) {
                        for (float coord : boundingBoxes.get(i)) {
                            bboxArray.put(coord);
                        }
                    } else {
                        bboxArray.put(0).put(0).put(0).put(0);
                    }
                    labelObject.put("bounding_box", bboxArray);

                    labelsArray.put(labelObject);
                }

                JSONObject payload = new JSONObject();
                payload.put("img_id", imageId);
                payload.put("confirmed_labels", labelsArray);

                final List<String> finalSelectedLabels = new ArrayList<>(selectedLabels);

                if (mqttClient != null && mqttClient.isConnected()) {
                    mqttClient.publishJson("project/confirmed_labels", payload.toString());
                    runOnUiThread(() -> {
                        descriptionText.setText("User feedback sent over MQTT.");
                        predictionContainer.removeAllViews();

                        TextView userFeedback = new TextView(MainActivity.this);
                        userFeedback.setText("User verified: " + String.join(", ", finalSelectedLabels));
                        userFeedback.setPadding(10, 10, 10, 10);
                        TextView tv = new TextView(MainActivity.this);
                        tv.setText("Your response is sent to the server for improving the model. :)");
                        predictionContainer.addView(userFeedback);
                        predictionContainer.addView(tv);
                        alternativeInput.setText("");
                        alternativeInput.setVisibility(View.GONE);
                        confirmButton.setVisibility(View.GONE);
                        drawBoundingBoxView.resetBoundingBox();
                    });
                } else {
                    runOnUiThread(() -> descriptionText.setText("Error: MQTT not connected."));
                }

            } catch (JSONException e) {
                descriptionText.setText("Error building JSON payload.");
                e.printStackTrace();
            }
        } else {
            descriptionText.setText("No selection made.");
        }
    }


    private void setBouncingInfo() {
        RectF boundingBox = drawBoundingBoxView.getBoundingBox();
        if (boundingBox != null) {
            float x1 = boundingBox.left;
            float y1 = boundingBox.top;
            float x2 = boundingBox.right;
            float y2 = boundingBox.bottom;

            // Convert values to relative coordinates (normalized 0-1)
            x1 /= capturedImage.getWidth();
            y1 /= capturedImage.getHeight();
            x2 /= capturedImage.getWidth();
            y2 /= capturedImage.getHeight();

            // Store bounding box for manual entry
            boundingBoxCoordinates = new float[]{x1, y1, x2, y2};
            Log.d("MainActivity", "Bouncing box = " + Arrays.toString(boundingBoxCoordinates));
        }
    }

}
