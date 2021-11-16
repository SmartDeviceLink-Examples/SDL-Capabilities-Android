package com.example.sdl_capabilities_android;

import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.smartdevicelink.transport.SdlBroadcastReceiver;
import com.smartdevicelink.transport.SdlRouterService;

public class SdlReceiver extends SdlBroadcastReceiver {
    @Override
    public Class<? extends SdlRouterService> defineLocalSdlRouterClass() {
        return com.example.sdl_capabilities_android.SdlRouterService.class;
    }

    @Override
    public void onSdlEnabled(Context context, Intent intent) {
        intent.setClass(context, com.example.sdl_capabilities_android.SdlService.class);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            context.startService(intent);
        } else {
            context.startForegroundService(intent);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
    }
}
