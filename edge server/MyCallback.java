package org.example;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.OutputStreamWriter;
import java.io.BufferedReader;
import java.io.InputStreamReader;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;


public class MyCallback implements MqttCallback {

    public boolean smoke_flag,gas_flag,temp_flag,rad_flag,and_flag=false,iot1_flag=false,iot2_flag=false;
    String risk,id;
    int temp=0;
    double UV=0,gas=0,smoke=0,and_lon,and_lat,iot_lon,iot2_lon,iot_lat,iot2_lat;
    Calculator.Point android_point,iot_point,iot2_point;
    MqttClient Client;
    String broker = "ws://broker.emqx.io:8083";
    String topic3 = "notifications";
    String clientId = "publisher";
    MemoryPersistence persistence;
    MqttMessage message_to_android;

    @Override
    public void connectionLost(Throwable throwable) {System.out.println("Connection to broker lost");
    }

    @Override
    public void messageArrived(String Topic, MqttMessage message) throws Exception {
        persistence = new MemoryPersistence();
        try {
            Client = new MqttClient(broker, clientId, persistence);
            MqttConnectOptions connOpts = new MqttConnectOptions();
            connOpts.setCleanSession(true);
            System.out.println("Connecting to broker: " + broker);
            Client.connect(connOpts);
            System.out.println("Connected");
        } catch (MqttException me) {
            System.out.println("reason " + me.getReasonCode());
            System.out.println("msg " + me.getMessage());
            System.out.println("loc " + me.getLocalizedMessage());
            System.out.println("cause " + me.getCause());
            System.out.println("excep " + me);
            me.printStackTrace();
        }

        String data = new String(message.getPayload());
        try{
            URL restApiUrl= new URL("http://localhost:3000/api/devices");
            HttpURLConnection connection = (HttpURLConnection) restApiUrl.openConnection();
            connection.setRequestMethod("PUT");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);
            connection.setDoInput(true);
            OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream());
            writer.write(data);
            writer.flush();
            writer.close();
            if (connection.getResponseCode() == 200) {
                // Get the response body as a string
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String inputLine;
                StringBuilder response = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

            } else {
                // Print an error message
                System.out.println("Error: " + connection.getResponseCode() + " " + connection.getResponseMessage());
            }
            connection.disconnect();
        }
        catch(Exception e){
            e.printStackTrace();
        }

        JsonParser parser = new JsonParser();

        JsonElement jsonElement = parser.parseString(data);

        
        JsonObject jsonObject = jsonElement.getAsJsonObject();
        id = jsonObject.get("DeviceID").getAsString();
        System.out.println(id);
        if (id.equals("3")) {
            and_flag = true;
            and_lat = jsonObject.get("Latitude").getAsDouble();
            and_lon = jsonObject.get("Longitude").getAsDouble();
            System.out.println(and_lat + " " + and_lon);
            android_point = new Calculator.Point(and_lat, and_lon);

        } else {

            if (jsonObject.has("data")) {
                JsonArray dataArray = jsonObject.getAsJsonArray("data");
                for (JsonElement element : dataArray) {
                    JsonObject dataObject = element.getAsJsonObject();
                    String sensorType = dataObject.get("Sensor Type").getAsString();
                    switch (sensorType) {
                        case "Smoke Sensor" -> smoke = dataObject.get("Sensor Value").getAsDouble();
                        case "Gas Sensor" -> gas = dataObject.get("Sensor Value").getAsDouble();
                        case "UV Sensor" -> UV = dataObject.get("Sensor Value").getAsDouble();
                        case "Thermal Sensor" -> temp = dataObject.get("Sensor Value").getAsInt();
                    }
                }

                smoke_flag = smoke > 0.14;

                gas_flag = gas > 1.0065; 

                temp_flag = temp > 50;

                rad_flag = UV > 6;

                if ((gas_flag) && (smoke_flag)) {
                    risk = "high";
                } else if (((!gas_flag) && (!smoke_flag)) && ((temp_flag) && (rad_flag))) {
                    risk = "medium";
                } else if ((gas_flag)) {
                    risk = "high";
                } else if ((gas_flag) && (temp_flag) && (rad_flag)) {
                    risk = "high";
                } else {
                    risk = "low";

                }

                if ((risk.equals("high")) || (risk.equals("medium"))) {
                    if (id.equals("1")) {
                        iot1_flag = true;
                    } else {
                        iot2_flag = true;
                    }
                } else {
                    if (id.equals("1")) {
                        iot1_flag = false;
                    } else {
                        iot2_flag = false;
                    }
                }

                if (((risk.equals("high")) || (risk.equals("medium"))) && (and_flag)) {


                    if (id.equals("1")) {

                        iot_lat = jsonObject.get("Latitude").getAsDouble();
                        iot_lon = jsonObject.get("Longitude").getAsDouble();
                        iot_point = new Calculator.Point(iot_lat, iot_lon);
                        if (!iot2_flag) {
                            JsonObject content = new JsonObject();
                            content.addProperty("dangerLevel", risk);
                            content.addProperty("distanceFromIotCenter", Calculator.AlertSignal(android_point, iot_point, null));
                            message_to_android = new MqttMessage(content.toString().getBytes());
                            Client.publish(topic3, message_to_android);
                        } else {
                            JsonObject content = new JsonObject();
                            content.addProperty("dangerLevel", risk);
                            content.addProperty("distanceFromIotCenter", Calculator.AlertSignal(android_point, iot_point, iot2_point));
                            message_to_android = new MqttMessage(content.toString().getBytes());
                            Client.publish(topic3, message_to_android);

                        }
                    } else if (id.equals("2")) {

                        iot2_lat = jsonObject.get("Latitude").getAsDouble();
                        iot2_lon = jsonObject.get("Longitude").getAsDouble();
                        iot2_point = new Calculator.Point(iot2_lat, iot2_lon);
                        if (!iot1_flag) {
                            JsonObject content = new JsonObject();
                            content.addProperty("dangerLevel", risk);
                            content.addProperty("distanceFromIotCenter", Calculator.AlertSignal(android_point, iot2_point, null));
                            message_to_android = new MqttMessage(content.toString().getBytes());
                            Client.publish(topic3, message_to_android);
                        } else {
                            JsonObject content = new JsonObject();
                            content.addProperty("dangerLevel", risk);
                            content.addProperty("distanceFromIotCenter", Calculator.AlertSignal(android_point, iot_point, iot2_point));
                            message_to_android = new MqttMessage(content.toString().getBytes());
                            Client.publish(topic3, message_to_android);

                        }
                    }
                }
            }
        }
    }



    @Override
    public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {

    }
}
