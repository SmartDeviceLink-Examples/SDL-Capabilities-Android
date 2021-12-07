package com.example.sdl_capabilities_android;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputLayout;
import com.smartdevicelink.util.DebugTool;

public class ConfigActivity  extends AppCompatActivity {

    private EditText portEditText;
    private TextInputLayout portTextInputLayout;
    private TextInputLayout ipTextInputLayout;
    private EditText ipEditText;
    private SharedPreferences sharedPref;
    private Button startButton;

    private final String IP_SHARED_PREF_KEY = "ipAddress";
    private final String PORT_SHARED_PREF_KEY = "port";



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_config);
        DebugTool.enableDebugTool();

        sharedPref = PreferenceManager.getDefaultSharedPreferences(this);

        ipTextInputLayout = findViewById(R.id.ipTextInputLayout);
        ipEditText = findViewById(R.id.ipEditText);
        portTextInputLayout = findViewById(R.id.portTextInputLayout);
        portEditText = findViewById(R.id.portEditText);
        startButton = findViewById(R.id.startButton);

        // Setup ipEditText
        final String ipAddress = sharedPref.getString(IP_SHARED_PREF_KEY, Config.CORE_IP);
        ipEditText.setText(ipAddress);


        // Setup portEditText
        int port = sharedPref.getInt(PORT_SHARED_PREF_KEY, Config.CORE_PORT);
        portEditText.setText(String.valueOf(port));
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Config.CORE_IP = ipEditText.getText().toString();
                sharedPref.edit().putString(IP_SHARED_PREF_KEY, ipEditText.getText().toString()).apply();
                Config.CORE_PORT = Integer.valueOf(portEditText.getText().toString());
                sharedPref.edit().putInt(PORT_SHARED_PREF_KEY, Integer.valueOf(portEditText.getText().toString())).apply();
                setResult(Activity.RESULT_OK);
                finish();
            }
        });
    }
}
