package com.cloudalbum.publisher.android.bootstrap;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.cloudalbum.publisher.android.data.repository.DeviceSessionRepository;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            DeviceSessionRepository repository = new DeviceSessionRepository(context);
            if (!repository.isBootAutoStartEnabled()) {
                return;
            }
            Intent launch = new Intent(context, LauncherActivity.class);
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(launch);
        }
    }
}
