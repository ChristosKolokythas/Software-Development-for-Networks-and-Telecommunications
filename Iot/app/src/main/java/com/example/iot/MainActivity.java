package com.example.iot;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    //String to get the manual position option
    String manual_position;

    // Constant variable to store RecyclerView state
    private final String KEY_RECYCLER_STATE = "recycler_state";

    // Request code for location permission
    int LOCATION_REQUEST_CODE = 1001;

    // Variables to store connection information for MQTT broker
    String IP, connectCredentials, Port;

    // Variables to store device information for MQTT topic
    String clientId, device_id, TOPIC;

    // MQTT client object
    MqttAndroidClient client;

    // Parcelable object to store RecyclerView state
    Parcelable mListState;

    // Boolean variable to check if GPS is enabled
    boolean gps;

    // Handler object for scheduling location updates
    Handler handler = new Handler();

    // Runnable object for location updates
    Runnable runnable;

    // Delay for location updates
    int delay = 1000;

    // FusedLocationProviderClient object for getting location updates
    FusedLocationProviderClient fusedLocationProviderClient;

    // BatteryManager object for getting battery information
    BatteryManager batterycheck;

    // SharedPreference listener object to detect changes in preferences
    private SharedPreferences.OnSharedPreferenceChangeListener listener;

    // LinearLayoutManager and RecyclerView objects for displaying sensors
    LinearLayoutManager layoutManager;
    RecyclerView recyclerView;

    // FloatingActionButton and TextView objects for manual location entry
    FloatingActionButton floatingActionButton;
    TextView locationTextView;

    // LocationRequest object for location updates
    LocationRequest locationRequest;

    // ArrayList to store Sensor objects
    ArrayList<Sensor> SArrayList = new ArrayList<>();

    // Adapter object for RecyclerView
    Adapter SAdapter;

    ActivityResultLauncher<Intent> startForResult = registerForActivityResult(
            // Register an ActivityResultLauncher to handle the result of the activity started for result
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if (result != null && result.getResultCode() == RESULT_OK) {
                        if (result.getData() != null && result.getData().getStringExtra(PickSensor.KEY_NAME) != null) {
                            // Check the sensor type returned by PickSensor activity and add it to the list
                            switch (result.getData().getStringExtra(PickSensor.KEY_NAME)) {
                                case "Thermal Sensor":
                                    addSensor(4);
                                    SAdapter.notifyItemInserted(SArrayList.size() - 1);
                                    break;
                                case "UV Sensor":
                                    addSensor(3);
                                    SAdapter.notifyItemInserted(SArrayList.size() - 1);
                                    break;
                            }
                        }
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Get the shared preferences and register a listener for changes
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener);

        // Add two default sensors to the list
        addSensor(1);
        addSensor(2);

        // Get the GPS preference and device ID from shared preferences
        gps = sharedPreferences.getBoolean("manual", false);
        device_id = sharedPreferences.getString("edit_text_preference_1", "");
        TOPIC = "iot" + device_id;

        // Check if location permission is granted, and start the location service if needed
        if (ContextCompat.checkSelfPermission(getApplicationContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST_CODE);
        } else {
            if (!gps) {
                startLocationService();
            }
        }
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            if (!gps) {
                startLocationService();
            }
        }

        // Get the fused location provider client and battery manager
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        batterycheck = (BatteryManager) getSystemService(BATTERY_SERVICE);

        // Get the IP and port preferences from shared preferences and connect to the broker
        IP = sharedPreferences.getString("edit_ip", "");
        Port = sharedPreferences.getString("edit_port", "");
        connectCredentials = IP + ":" + Port;
        ConnectToBroker();

        // Define the shared preference change listener
        listener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @SuppressLint({"MissingPermission"})
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                if (key.equals("manual")) {
                    // If the GPS preference has changed, update the GPS status and start/stop the location service accordingly
                    gps = sharedPreferences.getBoolean("manual", false);
                    Log.d("demo", "changed " + gps);
                    if (gps) {
                        manual_position = sharedPreferences.getString("list_preference_1", "");
                        getManualPos(manual_position);
                        stopLocationService();
                    } else {
                        startLocationService();
                    }
                } else if (key.equals("edit_ip")) {
                    // If the IP preference has changed, disconnect from the broker and connect with the new credentials
                    IP = sharedPreferences.getString("edit_ip", "");
                    DisconnectFromBroker();
                    connectCredentials = IP + ":" + Port;
                    ConnectToBroker();
                } else if (key.equals("edit_port")) {
                    Port = sharedPreferences.getString("edit_port", "");
                    DisconnectFromBroker();
                    connectCredentials = IP + ":" + Port;
                    ConnectToBroker();
                    connectCredentials = IP + ":" + Port;
                    Log.d("demo", "changed " + Port);
                    Log.d("demo", "changed " + connectCredentials);
                } else if (key.equals("list_preference_1")) {
                    manual_position = sharedPreferences.getString("list_preference_1", "");
                    getManualPos(manual_position);
                } else if (key.equals("edit_text_preference_1")) {
                    device_id = sharedPreferences.getString("edit_text_preference_1", "");
                }
            }
        };
// Register a listener to be notified when shared preferences are changed
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener);

// Find and set a click listener for a floating action button
        floatingActionButton = findViewById(R.id.fab);
        floatingActionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Create an intent to start the PickSensor activity
                Intent intent = new Intent(MainActivity.this, PickSensor.class);
                // Start the activity and wait for a result
                startForResult.launch(intent);
            }
        });

// Find the RecyclerView and set its layout manager
        recyclerView = findViewById(R.id.rec_view);
        layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);

// Create an adapter for the RecyclerView and set it
        SAdapter = new Adapter(SArrayList);
        recyclerView.setAdapter(SAdapter);
    }

    // Inflate the menu resource file into a Menu object
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.action_bar_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    // Check if the location service is running
    private boolean isLocationServiceRunning() {
        // Get the ActivityManager service
        ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager != null) {
            // Check all running services for the location service
            for (ActivityManager.RunningServiceInfo service :
                    activityManager.getRunningServices(Integer.MAX_VALUE)) {
                if (com.example.iot.LocationService.class.getName().equals(service.service.getClassName()))
                    // If the service is running in the foreground, return true
                    if (service.foreground) {
                        return true;
                    }
            }
        }
        // If the service is not running, return false
        return false;
    }

    // Start the location service if it is not already running
    private void startLocationService() {
        if (!isLocationServiceRunning()) {
            // Create an intent to start the location service
            Intent intent = new Intent(getApplicationContext(), com.example.iot.LocationService.class);
            intent.setAction("startLocationService");
            // Start the service and display a toast message
            startService(intent);
            Toast.makeText(this, "location service started", Toast.LENGTH_SHORT).show();
        }
    }

    // Stop the location service if it is running
    private void stopLocationService() {
        if (isLocationServiceRunning()) {
            // Create an intent to stop the location service
            Intent intent = new Intent(getApplicationContext(), com.example.iot.LocationService.class);
            intent.setAction("stopLocationService");
            // Stop the service and display a toast message
            startService(intent);
            Toast.makeText(this, "Location service stopped", Toast.LENGTH_SHORT).show();
        }
    }

    // Get the manual position selected by the user and store it in SharedPreferences
    public void getManualPos(String manual_position) {
        SharedPreferences sharedPreferences = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        switch (manual_position) {
            case "1":
                editor.putString("Longitude", "23.76630586399502");
                editor.putString("Latitude", "37.96809452684323");
                editor.apply();
                break;
            case "2":
                editor.putString("Longitude", "23.766603589104385");
                editor.putString("Latitude", "37.96799937191987");
                editor.apply();
                break;
            case "3":
                editor.putString("Longitude", "23.767174897611685");
                editor.putString("Latitude", "37.967779456380754");
                editor.apply();
                break;
            default:
                editor.putString("Longitude", "23.76626294807113");
                editor.putString("Latitude", "37.96790421900921");
                editor.apply();
                break;
        }
        // Get the stored coordinates from SharedPreferences and log them
        String s1 = sharedPreferences.getString("Longitude", "");
        String s2 = sharedPreferences.getString("Latitude", "");
        Log.d("demo", "manual X and Y " + s1 + " " + s2);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        // Handles click events for menu items
        switch (item.getItemId()) {
            case R.id.i_set:
                // Creates an intent to start the Settings activity
                Intent intent = new Intent(this, Settings.class);
                startActivity(intent);
                break;
            case R.id.xtp:
                // Displays a confirmation dialog when the user tries to exit the app
                new AlertDialog.Builder(MainActivity.this)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setTitle("Sensors App")
                        .setMessage("Are you sure you want to close this App?")
                        // Closes the location service and the activity if the user confirms
                        .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                stopLocationService();
                                finish();
                            }

                        })
                        .setNegativeButton("No", null)
                        .show();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    public void addSensor(int i) {
        // Adds a new Sensor object to the SArrayList based on the integer passed in
        if (i == 1) {
            this.SArrayList.add(new Sensor("Smoke Sensor", R.drawable.smoke_sensor));
        } else if (i == 2) {
            this.SArrayList.add(new Sensor("Gas Sensor", R.drawable.gas_sensor));
        } else if (i == 3) {
            this.SArrayList.add(new Sensor("UV Sensor", R.drawable.uv_sensor));
        } else if (i == 4) {
            this.SArrayList.add(new Sensor("Thermal Sensor", R.drawable.thermal_sensor));
        }
        return;
    }

    public void onBackPressed() {
        // Displays a confirmation dialog when the user tries to exit the app
        new AlertDialog.Builder(MainActivity.this)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle("Sensors App")
                .setMessage("Are you sure you want to close this App?")
                // Closes the location service and the activity if the user confirms
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        stopLocationService();
                        finish();
                    }

                })
                .setNegativeButton("No", null)
                .show();
    }

    public boolean ConnectToBroker() {
        // Connects to an MQTT broker using the client ID and URL
        clientId = MqttClient.generateClientId();
        client = new MqttAndroidClient(this.getApplicationContext(),
                "tcp://broker.emqx.io.:1883",
                clientId);
        //"tcp://broker.hivemq.com:1883"
        //
        try {
            IMqttToken token = client.connect();
            token.setActionCallback(new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    // Logs a message if the connection is successful
                    Log.d("demo", "connected");
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    // Something went wrong e.g. connection timeout or firewall problems
                    Log.d("demo", "not connected");
                    Toast.makeText(MainActivity.this, "not connected", Toast.LENGTH_LONG).show();
                }
            });
        } catch (MqttException e) {
            e.printStackTrace();
        }
        return true;
    }

    public void DisconnectFromBroker() {
        try {
            // Unregister all the resources that were allocated by the MQTT client
            client.unregisterResources();
            // Close the connection to the MQTT broker
            client.close();
            // Disconnect from the MQTT broker
            client.disconnect();
            // Set the callback to null
            client.setCallback(null);
            // Set the client object to null
            client = null;
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void publishdata() throws JSONException {
        // Get the shared preferences object
        SharedPreferences sharedPreferences = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this);

        // Create the main JSON object
        JSONObject mainObject = createMainObject(sharedPreferences);
        // Create the JSON array that will hold the sensor data
        JSONArray dataArray = createDataArray();

        // Add the sensor data to the main object, if there is any
        if (dataArray.length() != 0) {
            mainObject.put("data", dataArray);
        }

        // Create the MQTT message with the main JSON object as the payload
        MqttMessage message = new MqttMessage();
        message.setPayload(mainObject.toString().getBytes(StandardCharsets.UTF_8));

        // Convert the message payload to a string and log it
        String msg = new String(message.getPayload());
        Log.d("MESSAGE KSUPNA", "Message Arrived: " + msg);

        // Publish the message to the MQTT broker
        try {
            client.publish(TOPIC, message);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private JSONObject createMainObject(SharedPreferences sharedPreferences) {
        // Create the main JSON object that will hold the device information
        JSONObject mainObject = new JSONObject();
        try {
            // Add the device's latitude, longitude, battery level, and ID to the main object
            mainObject.put("Latitude", sharedPreferences.getString("Latitude", ""));
            mainObject.put("Longitude", sharedPreferences.getString("Longitude", ""));
            mainObject.put("Battery", getBatteryCapacity());
            mainObject.put("DeviceID", device_id);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return mainObject;
    }

    private JSONArray createDataArray() {
        // Create the JSON array that will hold the sensor data
        JSONArray dataArray = new JSONArray();
        // Loop through all the sensors in the list
        for (int i = 0; i < SArrayList.size(); i++) {
            Sensor sensor = SArrayList.get(i);
            // Check if the sensor is communicating
            if (sensor.getCommunicate()) {
                // Create a JSON object for the sensor data and add it to the data array
                JSONObject dataObject = createDataObject(sensor, i);
                dataArray.put(dataObject);
            }
        }
        return dataArray;
    }

    private JSONObject createDataObject(Sensor sensor, int index) {
        // Create a JSON object that will hold the sensor data
        JSONObject dataObject = new JSONObject();
        try {
            // Add the sensor type, sensor number, and sensor value to the data object
            dataObject.put("Sensor Type", sensor.getSensor_type());
            dataObject.put("Sensor Number", index);
            dataObject.put("Sensor Value", sensor.getValue());
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return dataObject;
    }

    // Method to get the battery capacity of the device
    private int getBatteryCapacity() {
        BatteryManager batterycheck = (BatteryManager) getSystemService(BATTERY_SERVICE);
        return (int) batterycheck.getLongProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
    }

    @Override
    protected void onResume() {
// Set up a handler to run the publishdata() method periodically
        handler.postDelayed(runnable = new Runnable() {
            public void run() {
                handler.postDelayed(runnable, delay);
                try {
                    publishdata(); // Call the publishdata() method
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }
        }, delay);
// Restore the list state if it exists
        if (mListState != null) {
            layoutManager.onRestoreInstanceState(mListState);
        }
        super.onResume();
    }

    @Override
    protected void onPause() {
// Stop the handler when the activity is not visible
        handler.removeCallbacks(runnable);
        super.onPause();
    }

    // Save the list state in the Bundle when the activity is destroyed
    protected void onSaveInstanceState(Bundle state) {
        mListState = layoutManager.onSaveInstanceState();
        state.putParcelable(KEY_RECYCLER_STATE, mListState);
        super.onSaveInstanceState(state);
    }

    // Restore the list state from the Bundle when the activity is recreated
    protected void onRestoreInstanceState(Bundle state) {
        super.onRestoreInstanceState(state);
        if (state != null)
            mListState = state.getParcelable(KEY_RECYCLER_STATE);
    }
}