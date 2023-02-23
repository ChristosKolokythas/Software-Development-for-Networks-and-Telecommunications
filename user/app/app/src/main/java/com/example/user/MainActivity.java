package com.example.user;

import android.Manifest;
import android.app.ActivityManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Timer;
import java.util.TimerTask;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class MainActivity extends AppCompatActivity {
    // Variables for handling location services
    private static final int LOCATION_REQUEST_CODE = 1001;
    private boolean gps = true;
    private String TImespace;
    private int timespaceint = 10;
    private FusedLocationProviderClient fusedLocationProviderClient;

    // Variables for handling network connectivity

    private static final int NETWORK_CHECK_INTERVAL_MS = 10000;

    // Variables for handling permissions
    private static final int READ_EXTERNAL_STORAGE_PERMISSION_CODE = 999;

    // Variables for handling MQTT connection
    private MqttAndroidClient client;
    private static final String NOTIFICATIONSTOPIC = "notifications";
    private static final String MESSAGETOPIC = "android3";
    private static final int QOS = 0;
    private String clientId;
    private long lastNotificationTime = 0;
    private boolean sendnotsnend = true;
    private String IP, Port, connectCredentials;

    // Variables for handling preferences
    private SharedPreferences.OnSharedPreferenceChangeListener listener;
    private int manual_timer;
    private String file_pref;

    // Other variables
    private static final String TAG = "debug";
    private static String device_id;
    private int delay = 1000;
    private Runnable runnable;
    private Handler handler = new Handler();
    private boolean flag;
    private String[] xArray1, yArray1, timeArray1;
    private String[] xArray2, yArray2, timeArray2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        Button myButton = findViewById(R.id.button);
        gps = sharedPreferences.getBoolean("manual", false);
        myButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendnotsnend = !sendnotsnend; // toggle the value of the boolean variable
                if (sendnotsnend) {
                    myButton.setText("Disable"); // set button text to reflect state
                } else {
                    myButton.setText("Enable");
                }
            }
        });
        Timer timer = new Timer();

        // Define the TimerTask that will check network connectivity periodically
        TimerTask checkConnectivityTask = new TimerTask() {
            @Override
            public void run() {
                // Get the system's connectivity manager
                ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

                // Check if the device is connected to the internet
                if (connectivityManager.getActiveNetworkInfo() != null && connectivityManager.getActiveNetworkInfo().isConnected()) {
                    // Device is connected to the internet
                } else {
                    // Device is not connected to the internet, display a warning message to the user
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "No internet connection available", Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        };

        // Schedule the TimerTask to run every 10 seconds
        timer.schedule(checkConnectivityTask, 0, 10000);

        device_id ="3"; // set device_id to "3"

        // check if the app has permission to access the device's location
        if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // if the permission has not been granted, request it
            ActivityCompat.requestPermissions(MainActivity.this, new String[] {
                    Manifest.permission.ACCESS_FINE_LOCATION
            }, LOCATION_REQUEST_CODE);
        } else {
            // if the permission has been granted, check if the GPS service is enabled
            if (!gps) {
                startLocationService(); // start the GPS service
            }
        }

        // check if the app has permission to access the device's location
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            // if the permission has been granted, check if the GPS service is enabled
            if (!gps) {
                startLocationService(); // start the GPS service
            }
        }

        // check if the app has permission to read external storage
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
            // if the permission has not been granted, request it
            requestPermissions(new String[] {
                    Manifest.permission.READ_EXTERNAL_STORAGE
            }, READ_EXTERNAL_STORAGE_PERMISSION_CODE);
        }

        // retrieve the IP address and port number from shared preferences
        IP = sharedPreferences.getString("edit_ip", "");
        Port = sharedPreferences.getString("edit_port", "");
        connectCredentials = IP + ":" + Port;
        connectToBroker(); // connect to the broker using the IP address and port number

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this); // initialize the fusedLocationProviderClient

        file_pref = sharedPreferences.getString("list_preference_1",""); // retrieve the file preference from shared preferences
        TImespace = sharedPreferences.getString("time_space", ""); // retrieve the time space from shared preferences

        parseXmlFiles(); // parse XML files

        lastNotificationTime = System.currentTimeMillis(); // set the last notification time to the current system time
        manual_timer = 0; // initialize the manual timer to 0

        listener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            // create a new shared preference change listener
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                if (key.equals("manual")) {
                    gps = sharedPreferences.getBoolean("manual", true); // retrieve the GPS mode from shared preferences
                    if (gps) {
                        stopLocationService(); // stop the GPS service
                    } else {
                        startLocationService(); // start the GPS service
                    }
                } else if (key.equals("edit_ip")) {
                    IP = sharedPreferences.getString("edit_ip", ""); // retrieve the new IP address from shared preferences
                    DisconnectFromBroker(); // disconnect from the broker
                    connectCredentials = IP + ":" + Port;
                    connectToBroker(); // connect to the broker using the new IP address
                    Log.d("debug", "changed " + IP); // log the new IP address
                    Log.d("debug", "changed " + connectCredentials); // log the new connect credentials
                } else if (key.equals("edit_port")) {
                    Port = sharedPreferences.getString("edit_port", ""); // retrieve the new port number from shared preferences
                    DisconnectFromBroker(); // disconnect from the broker
                    connectCredentials = IP + ":" + Port;
                    connectToBroker(); // connect to the broker using the new port number
                    Log.d("debug", "changed " + Port); // log the new port number
                } else if (key.equals("list_preference_1")) {
                    file_pref = sharedPreferences.getString("list_preference_1", "");
                    Log.d("debug", "file " + file_pref);
                } else if (key.equals("time_space")) {
                    TImespace = sharedPreferences.getString("time_space", "10");
                }

            }

        };
        if (!TextUtils.isEmpty(TImespace)) {
            timespaceint = Integer.valueOf(TImespace);
        }
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener);

    }

    private boolean isLocationServiceRunning() {
        // This method checks if the location service is currently running in the background.
        ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager != null) {
            for (ActivityManager.RunningServiceInfo service :
                    activityManager.getRunningServices(Integer.MAX_VALUE)) {
                if (com.example.user.LocationService.class.getName().equals(service.service.getClassName()))
                    if (service.foreground) {
                        return true;
                    }
            }
        }
        return false;
    }

    private void startLocationService() {
        // This method starts the location service in the background if it is not already running.
        if (!isLocationServiceRunning()) {
            Intent intent = new Intent(getApplicationContext(), com.example.user.LocationService.class);
            intent.setAction("startLocationService");
            startService(intent);
            Toast.makeText(this, "location service started", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopLocationService() {
        // This method stops the location service if it is currently running.
        if (isLocationServiceRunning()) {
            Intent intent = new Intent(getApplicationContext(), com.example.user.LocationService.class);
            intent.setAction("stopLocationService");
            startService(intent);
            Toast.makeText(this, "Location service stopped", Toast.LENGTH_SHORT).show();
        }
    }

    public void connectToBroker() {
        // This method connects to the MQTT broker to receive notifications.
        clientId = MqttClient.generateClientId();
        client = new MqttAndroidClient(this.getApplicationContext(),
                connectCredentials,
                clientId);
        try {
            IMqttToken token = client.connect();
            token.setActionCallback(new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    subscribeToNotificationsTopic();
                    setNotificationCallback();
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    handleConnectionFailure();
                }
            });
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    // This method subscribes the client to the notifications topic and logs the success or failure of the subscription
    private void subscribeToNotificationsTopic() {
        try {
            IMqttToken token = client.subscribe(NOTIFICATIONSTOPIC, QOS);
            token.setActionCallback(new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.d("debug", "Subscribed to " + NOTIFICATIONSTOPIC);
                }
                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.d("debug", "Failed to subscribe to " + NOTIFICATIONSTOPIC);
                }
            });
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    // This method sets the callback for notifications and handles connection loss, message arrival, and delivery completion
    private void setNotificationCallback() {
        client.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                Log.d("debug", "Connection lost");
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                // Handle incoming notification messages
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastNotificationTime > 6000) {
                    handleNotification(message);
                    lastNotificationTime = currentTime;
                }
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                Log.d("debug", "Delivery complete");
            }
        });
    }

    // This method handles incoming notification messages by parsing the JSON and displaying a danger message
    private void handleNotification(MqttMessage message) throws JSONException {
        String jsonString = new String(message.getPayload());
        JSONObject obj = new JSONObject(jsonString);
        String dangerLevel = obj.getString("dangerLevel");
        String distance = obj.getString("distanceFromIotCenter");
        dangerMessage(dangerLevel, distance);
        Log.d("debug", "Received notification with danger level " + dangerLevel + " and distance " + distance);
    }

    // This method handles connection failures and displays a toast message to the user
    private void handleConnectionFailure() {
        Log.d("debug", "Connection failure");
        Toast.makeText(MainActivity.this, "Not connected", Toast.LENGTH_LONG).show();
    }

    // This method displays a danger message to the user based on the danger level and distance provided
    public void dangerMessage(String danger, String distance) {
        String message = "";
        switch (danger) {
            case "high":
                message = "High danger risk " + distance + " meters from your location";
                break;
            case "medium":
                message = "Medium danger risk " + distance + " meters from your location";
                break;
            default:
                // Do nothing
                return;
        }
        // Show the dialog
        new AlertDialog.Builder(MainActivity.this)
                .setTitle("DANGER CLOSE TO YOU")
                .setMessage(message)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // Do nothing
                    }
                })
                .show();
        // Play the appropriate sound
        if (danger.equals("high")) {
            MediaPlayer.create(this, R.raw.highdanger).start();
        } else if (danger.equals("medium")) {
            MediaPlayer.create(this, R.raw.middanger).start();
        }
    }
    // This method is used in order to disconect from broker
    public void DisconnectFromBroker() {
        try {
            client.unregisterResources();
            client.close();
            client.disconnect();
            client.setCallback(null);
            client = null;
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.action_bar_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    // This method handles the user's selection from the menu bar options
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if ((item.getItemId()) == R.id.i_set) {
// If the "Settings" option is selected, start the Settings activity
            Intent intent = new Intent(this, Settings.class);
            startActivity(intent);
        } else if ((item.getItemId()) == R.id.xtp) {
// If the "Close" option is selected, show a confirmation dialog
            new AlertDialog.Builder(MainActivity.this)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setTitle("Closing")
                    .setMessage("Are you sure you want to close the App?")
                    .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // If the user confirms, stop the location service and close the app
                            stopLocationService();
                            finish();
                        }
                    })
                    .setNegativeButton("No", null)
                    .show();
        }
        return super.onOptionsItemSelected(item);
    }

    // This method parses two XML files and extracts information from them
    public void parseXmlFiles() {
        // First XML file
        try {
            // Get the input stream for the first XML file
            InputStream is1 = getResources().openRawResource(R.raw.android_1);
            // Create a document builder factory and document builder for the first XML file
            DocumentBuilderFactory factory1 = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder1 = factory1.newDocumentBuilder();
            // Parse the first XML file and get the node list of "timestep" elements
            Document xmlDoc1 = builder1.parse(is1);
            NodeList nodeList1 = xmlDoc1.getElementsByTagName("timestep");
            // Create arrays to store the x, y, and time values for each timestep element in the first XML file
            xArray1 = new String[nodeList1.getLength()];
            yArray1 = new String[nodeList1.getLength()];
            timeArray1 = new String[nodeList1.getLength()];
            // Loop through the node list and extract the x, y, and time values for each timestep element in the first XML file
            for (int i = 0; i < nodeList1.getLength(); i++) {
                Element element1 = (Element) nodeList1.item(i);
                String x1 = element1.getElementsByTagName("vehicle").item(0).getAttributes().getNamedItem("x").getNodeValue();
                String y1 = element1.getElementsByTagName("vehicle").item(0).getAttributes().getNamedItem("y").getNodeValue();
                String time1 = element1.getAttributes().getNamedItem("time").getNodeValue();
                xArray1[i] = x1;
                yArray1[i] = y1;
                timeArray1[i] = time1;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Second XML file
        try {
            // Get the input stream for the second XML file
            InputStream is2 = getResources().openRawResource(R.raw.android_2);
            // Create a document builder factory and document builder for the second XML file
            DocumentBuilderFactory factory2 = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder2 = factory2.newDocumentBuilder();
            // Parse the second XML file and get the node list of "timestep" elements
            Document xmlDoc2 = builder2.parse(is2);
            NodeList nodeList2 = xmlDoc2.getElementsByTagName("timestep");
            // Create arrays to store the x, y, and time values for each timestep element in the second XML file
            xArray2 = new String[nodeList2.getLength()];
            yArray2 = new String[nodeList2.getLength()];
            timeArray2 = new String[nodeList2.getLength()];
            // Loop through the node list and extract the x, y, and time values for each timestep element in the first XML file
            for (int i = 0; i < nodeList2.getLength(); i++) {
                Element element2 = (Element) nodeList2.item(i);
                String x2 = element2.getElementsByTagName("vehicle").item(0).getAttributes().getNamedItem("x").getNodeValue();
                String y2 = element2.getElementsByTagName("vehicle").item(0).getAttributes().getNamedItem("y").getNodeValue();
                String time2 = element2.getAttributes().getNamedItem("time").getNodeValue();
                xArray2[i] = x2;
                yArray2[i] = y2;
                timeArray2[i] = time2;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void publishData() {
        // Get default shared preferences
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        try {
            // Create a JSON object to hold data
            JSONObject dataObject = new JSONObject();
            // Add device ID to the data object
            dataObject.put("DeviceID", device_id);

            // If GPS is not available
            if (!gps) {
                // Add latitude and longitude from shared preferences to the data object
                dataObject.put("Latitude", sharedPreferences.getString("Latitude", ""));
                dataObject.put("Longitude", sharedPreferences.getString("Longitude", ""));
            } else {
                // If GPS is available
                switch (file_pref) {
                    // Depending on the file preference, add corresponding latitude and longitude from arrays to the data object
                    case "1":
                        dataObject.put("Latitude", yArray1[manual_timer]);
                        dataObject.put("Longitude", xArray1[manual_timer]);
                        Log.d("debug", "psi ksupna " + xArray1[manual_timer]);
                        break;
                    default:
                        dataObject.put("Latitude", yArray2[manual_timer]);
                        dataObject.put("Longitude", xArray2[manual_timer]);
                        Log.d("debug", "psi ksupna  33333" + xArray2[manual_timer]);
                        break;
                }
            }

            // Create a new MQTT message with the JSON object as payload
            MqttMessage message = new MqttMessage();
            message.setPayload(dataObject.toString().getBytes(StandardCharsets.UTF_8));

            // Convert the message payload to a string and log it for debugging
            String msg = new String(message.getPayload());
            Log.d("MESSAGE KSUPNA", "Message Arrived: " + msg);

            try {
                // Publish the message to the MQTT broker
                Log.d("debug", "Publishing message to topic: " + MESSAGETOPIC);
                client.publish(MESSAGETOPIC, message);
            } catch (MqttException e) {
                e.printStackTrace();
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onResume() {
        // Schedule the publishing of data using a handler with a delay
        handler.postDelayed(runnable = new Runnable() {
            public void run() {
                // Repeat this code with the given delay
                handler.postDelayed(runnable, delay);
                // If GPS is not available
                if (!gps) {
                    Log.d("debug", "auto");
                    // Publish data
                    publishData();
                } else {
                    // If GPS is available and data should be sent
                    if (sendnotsnend) {
                        // Get shared preferences and editor
                        SharedPreferences sharedPreferences = androidx.preference.PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        Log.d("debug", "manual " + file_pref);

                        // If manual timer is less than timespaceint
                        if (manual_timer < timespaceint) {
                            // Publish data, increment timer and save it to shared preferences
                            publishData();
                            manual_timer++;
                            editor.putInt("timer_1", manual_timer);
                            editor.apply();
                            Log.d("debug", "PSIT " + manual_timer);

                        } else {
                            Log.d("debug", "EOF");
                        }
                    }
                }
            }
        }, delay);
        super.onResume();
    }

    @Override
    protected void onPause() {
        // Remove the handler callbacks when activity is paused
        handler.removeCallbacks(runnable);
        super.onPause();
    }
}