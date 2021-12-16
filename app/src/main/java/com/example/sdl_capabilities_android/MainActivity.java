package com.example.sdl_capabilities_android;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.smartdevicelink.transport.SdlBroadcastReceiver;
import com.smartdevicelink.util.DebugTool;

public class MainActivity extends AppCompatActivity {

    public static final int CONFIGS_ACTIVITY_REQUEST_CODE = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        DebugTool.enableDebugTool();
        //Intent intent = new Intent(this, ConfigActivity.class);
        //startActivityForResult(intent, CONFIGS_ACTIVITY_REQUEST_CODE);
        SdlBroadcastReceiver.queryForConnectedService(this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CONFIGS_ACTIVITY_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            Intent proxyIntent = new Intent(this, com.example.sdl_capabilities_android.SdlService.class);
            startService(proxyIntent);
        } else {
            finish();
        }
    }
}