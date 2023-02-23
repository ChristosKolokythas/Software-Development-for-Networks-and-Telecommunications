<script>
import { onMount } from 'svelte';

let map;
let iotmarkers = {};
let androidmarkers= {};
let polygons=[];
onMount(async () => {

  //Draw the map
  const googleMapsScript = document.createElement('script');
  googleMapsScript.src = `https://maps.googleapis.com/maps/api/js?key=AIzaSyCXPOGVmiQi21YXGnUmIDegztJeUfykaCc&callback=Function.prototype`;
  document.head.appendChild(googleMapsScript);
  googleMapsScript.onload = () => {
    map = new google.maps.Map(document.getElementById('map'), {
      center: { lat: 37.96822558982665, lng: 23.76653761984567  },
      zoom: 18,
    });

    //Get the data from the api every second
    setInterval(fetchData,1000)
  };
});

async function fetchData(){
  try{
    const response = await fetch('http://localhost:3000/api/devices');
    const data = await response.json();
    //Parse the Json
    const lat = parseFloat(data.Latitude);
    const lng = parseFloat(data.Longtitude);
    const id=parseInt(data.DeviceID);
    const battery = parseInt(data.Battery)|| null;
    let smoke = null;
    let gas = null;
    let temp = null;
    let uv = null;
    if (data.data) {
      for (const sensor of data.data) {
        switch (sensor['Sensor Type']) {
          case 'Smoke Sensor':
            smoke = sensor['Sensor Value'];
            break;
          case 'Gas Sensor':
            gas = sensor['Sensor Value'];
            break;
          case 'Thermal Sensor':
            temp = sensor['Sensor Value'];
            break;
          case 'UV Sensor':
            uv = sensor['Sensor Value'];
            break;
        }
      }
    }
    if(id==3){
      addAndroidMarker(lat,lng,id);
    }else{
      addIotMarkers(lat,lng,id,battery,gas,smoke,temp,uv);
    }

  }catch(error){
    console.error(error);
  }
}

function addAndroidMarker(lat, lng, id) {
  const oldMarker = androidmarkers[0];
  if(oldMarker){ //update the marker (position and infowindow) if it already exist
    const marker=oldMarker.marker;
      marker.setPosition({lat,lng});
      const infowindow1=oldMarker.infowindow;
      infowindow1.setContent( `
          <div>
            <p>Android Device: ${id}</p>
            <p>Position: (${lat}, ${lng})</p>
          </div>
        `
      );
    return marker;
  }else{ const marker = new google.maps.Marker({
	position: { lat, lng },
	map: map,
  icon: 'android.png',
  });

const infowindow = new google.maps.InfoWindow({
        content: `
          <div>
            <p>Android Device: ${id}</p>
            <p>Position: (${lat}, ${lng})</p>
          </div>
        `
      });
      marker.addListener('click',()=>{
      infowindow.open(map, marker);
    });
    androidmarkers[0] = { marker, infowindow };
    return marker;
  }
}
function addIotMarkers(lat, lng, id, battery, gas, smoke, temp, uv) {
  let risklevel;
  const oldMarker=iotmarkers[id];
  if (oldMarker) {//update iot marker infowindow, icon, circle (and position if needed)
    oldMarker.marker.setPosition({ lat, lng });
    oldMarker.infowindow.setContent(`
      <div>
        <p>Iot Device: ${id}</p>
        <p>Position: (${lat}, ${lng})</p> 
        <p>Battery: ${battery}%</p>          
        <p>Gas: ${gas}</p>
        <p>Smoke: ${smoke}</p>
        <p>Temperature: ${temp}</p>
        <p>Radiation: ${uv}</p>
      </div>
    `);
    risklevel = calculateRisk(gas, smoke, temp, uv);
    oldMarker.marker.setIcon(chooseIcon(risklevel));
    if (risklevel === 1) {
      let circleColor = getCircleColor(gas, smoke, temp, uv);
      if(oldMarker.circle!=null){//if risk is low and the iot device has a circle under it update the circle
        oldMarker.circle.setOptions({
          strokeColor: circleColor,
          fillColor: circleColor,
          center: { lat, lng },
          visible: true
        });
      }else{//if the existing iot device doesn't  have a circle create a new one
        let circle = new google.maps.Circle({
        map: map,
        radius: 10,
        strokeColor: circleColor,
        strokeOpacity: 0.6,
        strokeWeight: 2,
        fillColor: circleColor,
        fillOpacity: 0.6,
        center: { lat, lng },
        visible:true
        })
        oldMarker.circle=circle;
      }
    }else{
      if (oldMarker.circle) {
      oldMarker.circle.setVisible(false);
      }
    }
    risklevel=calculateRisk(gas,smoke,temp,uv);
    iotmarkers[id].risklevel=risklevel;
  }else {//if a marker with the same id doesn't exist create a new one
    risklevel=calculateRisk(gas, smoke, temp, uv);
    let marker = new google.maps.Marker({
      position: { lat, lng },
      map: map,
      icon: chooseIcon(risklevel),
    });
    let infowindow = new google.maps.InfoWindow({
      content: `
        <div>
          <p>Iot Device: ${id}</p>
          <p>Position: (${lat}, ${lng})</p> 
          <p>Battery: ${battery}%</p>          
          <p>Gas: ${gas}</p>
          <p>Smoke: ${smoke}</p>
          <p>Temperature: ${temp}</p>
          <p>Radiation: ${uv}</p>
        </div>
      `
    });
    let circle;
    risklevel = calculateRisk(gas, smoke, temp, uv);
    if (risklevel === 1) {//if the risk is low draw a circle under the iot device
      let circleColor = getCircleColor(gas, smoke, temp, uv);
      circle = new google.maps.Circle({
        map: map,
        radius: 10,
        strokeColor: circleColor,
        strokeOpacity: 0.6,
        strokeWeight: 2,
        fillColor: circleColor,
        fillOpacity: 0.6,
        center: { lat, lng },
        visible:true

      });
    }
    risklevel=calculateRisk(gas,smoke,temp,uv);
    iotmarkers[id] = { marker, infowindow, circle,risklevel};
    iotmarkers[id].risklevel=risklevel;
    marker.addListener('click', () => {
      infowindow.open(map, marker);
    });
    return marker;
  }
  let hasRisk = Object.values(iotmarkers).every(marker => marker.risklevel > 1);
  if(hasRisk&&Object.keys(iotmarkers).length > 1){//if there are 2 iot markers and both have high or medium risk level
    if(polygons.length>0){  //if the polygon is already drawn show it else draw it
      polygons[0].setVisible(true);
    }else{ 
        drawPolygon();
      }
    }else{
    if(polygons.length>0){//if the polygon exist and 1 of 2 iot devices has low risk set polygon to invisible
      polygons[0].setVisible(false);
   }
  }

}

//Calculate the risk based on the sensor values of the iot device
function calculateRisk(gas, smoke, temp, uv){
  let risk;
  let gas_flag= (gas > 1.0065 );

  let smoke_flag= (smoke > 0.0915 );

  let temp_flag= (temp > 50 );

  let rad_flag= (uv > 6);

  if (((gas_flag)&&(smoke_flag))){
      return 3;
  } else if (((!gas_flag)&&(!smoke_flag))&&((temp_flag)&&(rad_flag))) {
      return 2;
  } else if ((gas_flag)) {
      return 3;
  } else if ((gas_flag)&& (smoke_flag) &&(temp_flag)&& (rad_flag)) {
      return 3;
  }
  else{
    return 1;
  }

}

//Choose the iot device icon based on the danger level
function chooseIcon(risk){
  let icon;
  if(risk==2){
    icon='yellow-warning.png';
  }else if(risk==3){
    icon='red-warning.png';
  }else if(risk==1){
    icon='iot-marker.png'
  }
  return icon;

}

//Set the circle color based on if the iot device is off
function getCircleColor(gas, smoke, temp, uv){
  let circleColor;
  let status;
  if(gas==null && smoke==null && temp==null && uv==null){
    circleColor='#ff0000';
  }else{
    circleColor='#66ff33';
  }
  return circleColor;
}

//Draw the polygon with the iot markers on opposite corners
function drawPolygon() {
  const polygonCoords = Object.values(iotmarkers).map(m => m.marker.getPosition());
  const bounds= new google.maps.LatLngBounds();
  bounds.extend(polygonCoords[0]);
  bounds.extend(polygonCoords[1]);
    const polygon = new google.maps.Polygon({
      paths: [
        { lat: bounds.getNorthEast().lat(), lng: bounds.getNorthEast().lng() },
        { lat: bounds.getSouthWest().lat(), lng: bounds.getNorthEast().lng() },
        { lat: bounds.getSouthWest().lat(), lng: bounds.getSouthWest().lng() },
        { lat: bounds.getNorthEast().lat(), lng: bounds.getSouthWest().lng() },
      ],
      strokeColor: "#FF0000",
      strokeOpacity: 0.8,
      strokeWeight: 2,
      fillColor: "#FF0000",
      fillOpacity: 0.35,
      map: map,
      visible:true
    });
  polygons.push(polygon);
}
</script>

<main>
	<div id="map" style="width:100%;height:1000px;"></div>
</main>





