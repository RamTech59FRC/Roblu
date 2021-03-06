package com.cpjd.roblu.utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.cpjd.roblu.sync.cloud.Service;

/**
 * Starts this app's service when the Android device boots up
 *
 * @since 3.0.0
 * @author Will Davies
 */
public class BootBroadcastReceiver extends BroadcastReceiver {
    static final String ACTION = "android.intent.action.BOOT_COMPLETED";
    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            // BOOT_COMPLETED” start Service
            if (intent.getAction().equals(ACTION)) {
                Intent serviceIntent = new Intent(context, Service.class);
                context.startService(serviceIntent);
            }
        } catch(Exception e) {
            Log.d("RBS", "Failed to start service.");
        }
    }


}
