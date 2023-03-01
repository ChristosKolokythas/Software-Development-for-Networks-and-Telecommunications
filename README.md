# Android ApplicationsAndroid Applications
#### 1. User application

This is the application installed on the mobile phone of the user who wants to be informed in case a risk is detected. By opening it, the user connects to the MQTT server and receives audiovisual notifications of any risks.

**Home Screen**
On opening the application the user is presented with the main screen of the application in which the following options are available:
An Enable/Disable button which immediately stops sending measurements to the server.
The 3 dots which contain 2 options:
I. Go to Settings.
II. Exit the application. To exit the application, a pop-up window appears which asks for the user's confirmation to permanently exit the application.

**Settings menu**
In the Settings menu we have the following options:
Edit IP Address: in this field we enter the IP for communication with the Edge Server.
Port: In this field we enter the Port to communicate with the Edge Server.
Manual Location: this switch is used to switch how to get location between using GPS and the 4 default locations.In case manual location is selected there are 2 more Settings.
Choose File:Selects between the 2 files given by the pronunciation.
TimeSpace:Selects the time space for which the measurement vectors are sent.

**Connection Check**:
Every 10 seconds the application checks via ConnectivityManager if it is connected to the internet. In case it detects absence of connection it displays a corresponding notification.

 **location**
As mentioned before, the location can be acquired in two ways, one automatic and one manual.
When the automatic mode is selected, the FusedLocationProviderClient service is used to obtain the actual location of the user's device.
If manual location is selected the application reads the XML files using parse of the pronunciation and stores the vectors they contain in corresponding tables, from which it derives the data it sends to the MQTT Server.

**Communication with the Server**
The user application communicates with the MQTT Server in order to receive notifications and send data via two corresponding topics.
To receive a distress notification from the server it subscribes to the "notifications" topic.  From this topic it receives a json file derives the level of the hazard as well as the distance from the point where the hazard occurred.
To send data to the server the application subscribes to the topic "android3". In this topic it sends a json file containing the location vector and the deviceID. (The deviceID is set as 3 to know that it is the android device).
#####  2. IoT Sensor Application2. IoT Sensor Application.
This is the application that contains the simulations of the four sensors required by the pronunciation. This application has many similarities with the user application in the way the settings menu works and the way the location is obtained through GPS. For this reason, only the points where the applications differ from each other will be analyzed below.

**Home Screen**
When opening the application we find the smoke and gas sensors pre-installed. At the bottom right of the screen is a floating button which allows us to install the other heat and UV sensors.
The sensor tabs are displayed as a list via RecyclerView. Each tab contains the sensor name an abstract icon an on/off switch as well as a slider.
The on/off switch activates and deactivates the sensor respectively regardless of its presence in the list.
The slider of each sensor is set to move within the limits of the values given by the pronunciation. This way the possibility of sending an invalid value is avoided.

**Settings**
In the settings of the iot application we find the following extra functionalities:
In case of manual location selection the user is asked to choose between four default locations.
Device ID. This option accepts an integer number in order to make it clear to the server which iot device it is.

**Communication with the Server**
The application publishes to a topic whose name consists of the string ¨iot¨ + device_id(where device_id = 1, 2, 3 etc). In this topic it sends a json file containing the coordinates, the battery level, the device ID and the data from the sensors (name, number and value).

**Use of the applications**
Both apps can be used very easily either via emulator or on a physical device by installing the apk file and accepting the necessary licenses. The minimum API recommended for the apps is API 24.

#Edge Server

For Calculator: 
does distance calculation:

-> **AlertSignal**: has 4 parameters and 2 cases

Point 2 to not exist (Iot2=null) 

**distance**: gives the distance of the Android to point iot1 and returns it.

All 2 points exist (Iot1,Iot2) distance: gives the distance of the Android to the 2 points (using the average distance of the points) and returns it.

-> **getCentalPoint**: calculates the average distance of the 2 points 1.lanCentral calculates a new lat using the 2 lats of Iot1 and Iot2, which will be the average of the 2 lats 2.lonCentral calculates a new lon using the 2 lons of Iot1 and Iot2, which will be the average of the 2 lons 3.given a new point (let's say p) with the new values in lat and lon, which is the average distance of Iot1 and Iot2

-> **calculateDistance**: we used the one given in the task.

-> **Point**: it's helpful to not give too many coordinates so it's easier for what it gets (ie it has x, y). We do this so that getCentalPoint doesn't have 4 parameters, we put Point Iot1 in which it has 2 parameters in it (lat, lon) the respective ones i.e. (x, y).

**For MyCallback:**
->**messageArived**:First it creates a MqttClient, converts the message to a json object, sends it to the REST API by making a PUT request and depending on the value of the id variable (we have set the device id of the android device to 3 and for iot1,iot2 to 1 and 2 respectively) you execute a different piece of code.In case the id is 3 (i.e. we received a message from the android device) you create the android point with the current coordinates of the android device. Also the variable and_flag(initialized as false so that in case the server receives a message from an iot device with risk high/medium πρωτα,while it has no values for the coordinates of the android device it will not try to calculate the distance between iot and android) becomes true.
In case the id is 1 or 2 (i.e. we received a message from an iot device) if there are active sensors it stores their value in the corresponding variable and calculates the risk level.If risk is high/medium the iot_flag of the device that sent the message
takes the value true otherwise it takes the value false. If the risk is high/medium and the and_flag is true then MqttClient after calculating the distance of the iot device from the android (if the iot_flag of the other iot device is false) or the distance of the center of the 2 iot devices from the android (if the iot_flag of the other iot device is true) publishes in the appropriate topic (which is subscriber the android device) a message containing the risk level and the distance.

**For Main** :
We define the topics and the online broker we use for communication between server,iot devices and android.We create a MqttClient which subscribes to the above topics and setCallback to MyCallback.
For the server to run properly we need to run the REST api.
**REST api**
To connect the GUI to the server we created a REST api in node.js. The REST api receives a json from the server, once a message comes from a device, it writes it to the data.json file and from there the GUI gets it to process it.
To run the REST api we need node.js (v18.13.0) and the corresponding npm packet manager. In the REST api folder we run the command "npm install" to install the required packages. Then to run it we run the command "node app.js" in the folder. The REST api is configured to run on port:3000.
#ServerGui
The graphical interface was developed with javascript and specifically with the Svelte framework. The graphical interface uses the Google Maps Api to render the map.

The fetchData function is used to "fetch" the data the GUI from the REST api. Then the function good depending on the device id (we have set the device id of the android device to 3 and for iot1, iot2 to 1 and 2 respectively) calls the corresponding function ( addAndroidMarker for id 3 and addIotMarkers for 2 or 3.)

The markers of each device are displayed on the map. There is a different marker for android device and for iot devices. The icon of each iot device changes according to its risk level. Each device has its own infowindow which we can see by clicking on its marker. The infowindows are updated every second. Below the markers of each iot device there is a circle that shows us if the device is active.

When there is a danger event (high or medium) a red rectangle appears on the map, with the 2 iot devices placed at the corners, showing the danger area.
Functions:
->**fetchData:Called** every one second using setinterval().Makes a request to the api to get the json file with the data sent by each device. Then it parses the json it receives from the api. Depending on the id it calls addAndroidMarker or addIotMarkers.
->**addAndroidMarker: **takes as arguments the location (lat and lon) and to id of the android device. it creates a marker for the android device and its infowindow if it doesn't already exist. If it exists it updates the location of the marker and the contents of the infowindow.
->**addIotMarkers**: takes as arguments the location (lat and lon) of the iot device the id, the battery value and the values of its sensors(gas,smoke,temp,uv). If there is no iot marker with the same id it creates the marker, its infowindow, the circle under the marker (if needed) and creates the rectangular rectangle (if needed). If there is an iot marker with the same id, it refreshes the infowindow, changes the color, or disappears the circle as appropriate, changes the marker icon if needed, and displays or disappears the rectangular rectangle.
->**calculateRisk**: takes the sensor values as arguments and returns the risk value (1 for low risk, 2 for medium risk and 3 for high risk) as appropriate.
->**chooseIcon**: takes as argument the risk value (risk) and chooses the icon for the markers of the iot devices.
->**getCircleColor**: takes as arguments the values of the sensors and chooses the color of the circle based on whether they are all "null".
->**drawPolygon**: creates a rectangular polygon based on two points on the map.

To run the GUI, node.js (v18.13.0) and the corresponding npm package manager are needed. In the graphical interface folder we run the command "npm install" to install the required packages. Then to run the GUI in its folder we execute the command "npm run dev". The GUI is configured to run on port:8080 and we can see it in the browser at:
http://localhost:8080/


For a more complete presentation using screenshots there is also the readme.pdf file


