package com.example.iot;

public class Sensor {

    private String sensor_type;
    private int sensor_image;
    private boolean communicate;
    private float value;

    public Sensor(String sensor_type, int sensor_image) {
        this.sensor_type = sensor_type;
        this.sensor_image = sensor_image;
        this.communicate = false;
        this.value = 0;
    }

    // Getter and Setter
    public String getSensor_type() {
        return sensor_type;
    }

    public void setSensor_type(String course_name) {
        this.sensor_type = course_name;
    }

    public int getSensor_image() {
        return sensor_image;
    }

    public void setSensor_image(int sensor_image) {
        this.sensor_image = sensor_image;
    }

    public boolean getCommunicate(){return communicate;}

    public void setCommunicate(boolean flag){ this.communicate = flag; }

    public float getValue(){return value;}

    public void setValue(float value){this.value = value; }

}