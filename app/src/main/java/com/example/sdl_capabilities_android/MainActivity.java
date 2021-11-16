package com.example.sdl_capabilities_android;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

       // SdlReceiver.queryForConnectedService(this);
        Intent proxyIntent = new Intent(this, com.example.sdl_capabilities_android.SdlService.class);
        startService(proxyIntent);
    }
}