package com.example.iot;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class PickSensor extends AppCompatActivity {
    public static final String KEY_NAME = "NAME";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.picktype);

        Button button1, button2;


        button1 =  findViewById(R.id.button1);
        button1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.putExtra(KEY_NAME,"Thermal Sensor");
                setResult(RESULT_OK, intent);
                finish();

            }
        });

        button2 =  findViewById(R.id.button2);
        button2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.putExtra(KEY_NAME,"UV Sensor");
                setResult(RESULT_OK, intent);
                finish();

            }
        });
    }
}

