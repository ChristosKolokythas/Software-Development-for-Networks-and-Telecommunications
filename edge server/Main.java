package org.example;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

public class Main {
    public static void main(String[] args) {

        String broker = "ws://broker.emqx.io:8083";
        String topic1 = "iot1";
        String topic2 = "iot2";
        String topic3 = "android3";
        String clientId = "subscribe_client";

        MemoryPersistence persistence=new MemoryPersistence();


        try {
            MqttClient Client = new MqttClient(broker, clientId, persistence);
            MqttConnectOptions connOpts = new MqttConnectOptions();
            connOpts.setCleanSession(true);
            Client.setCallback( new MyCallback() );
            System.out.println("Connecting to broker: "+broker);
            Client.connect(connOpts);
            System.out.println("Connected");
              Client.subscribe(topic1, 0);
              Client.subscribe(topic2, 0);
             Client.subscribe(topic3, 0);


        } catch(MqttException me) {
                System.out.println("reason "+me.getReasonCode());
                System.out.println("msg "+me.getMessage());
                System.out.println("loc "+me.getLocalizedMessage());
                System.out.println("cause "+me.getCause());
                System.out.println("excep "+me);
                me.printStackTrace();
            }

        }





    }
