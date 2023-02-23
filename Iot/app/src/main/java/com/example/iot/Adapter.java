package com.example.iot;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.slider.Slider;

import java.util.ArrayList;
public class Adapter extends RecyclerView.Adapter<Adapter.Viewholder> {
    ArrayList<Sensor> sensorArrayList;
    public Adapter(ArrayList<Sensor> data){
        this.sensorArrayList = data;
    }
    @NonNull
    @Override
    public Viewholder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.sensor_card, parent, false);
        Viewholder viewholder = new Viewholder(view);
        return viewholder;
    }

    @Override
    public void onBindViewHolder(@NonNull Viewholder holder, int position) {
        Sensor sensor = sensorArrayList.get(position);
        holder.sensor_type.setText(sensor.getSensor_type());
        holder.sensor_image.setImageResource(sensor.getSensor_image());
        holder.slider.setValue(sensor.getValue());
        holder.position = holder.getAdapterPosition();
        switch (sensor.getSensor_type()){
            case "Smoke Sensor":
                holder.slider.setValueFrom(0);
                holder.slider.setValueTo(0.25F);
                break;
            case "Thermal Sensor":
                holder.slider.setValueFrom(-5);
                holder.slider.setValueTo(80F);
            default:
                break;
        }
        holder.sensor = sensor;
    }

    @Override
    public int getItemCount() {
        return this.sensorArrayList.size();
    }

    public static class Viewholder extends RecyclerView.ViewHolder{
        int position;
        Slider slider;
        Sensor sensor;
        TextView sensor_type;
        ImageView sensor_image;
        View rootView;
        Switch aSwitch;
        public Viewholder(@NonNull View itemView) {
            super(itemView);
            rootView = itemView;
            sensor_type = itemView.findViewById(R.id.sensor_type);
            sensor_image = itemView.findViewById(R.id.sensor_image);
            slider = itemView.findViewById(R.id.sensor_value);
            aSwitch = itemView.findViewById(R.id.sensor_switch);

            itemView.findViewById(R.id.sensor_switch).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    sensor.setCommunicate(sensor.getCommunicate() == false);
                    Log.d("demo","switch clicked "+ sensor.getCommunicate()+" on sensor "+ position);
                }
            });


            slider.addOnChangeListener(new Slider.OnChangeListener(){
                @Override
                public void onValueChange(@NonNull Slider slider, float value, boolean fromUser) {
                    sensor.setValue(value);
                    Log.d("demo","value change to "+ sensor.getValue());
                }
            } );
        }
    }
}
