package com.cloudalbum.publisher.android.bootstrap;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.cloudalbum.publisher.android.data.repository.DeviceSessionRepository;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";
    private static final String ORIGINAL_HOME_PACKAGE = "com.dangbei.tvlauncher";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            DeviceSessionRepository repository = new DeviceSessionRepository(context);
            if (!repository.isBootAutoStartEnabled()) {
                return;
            }
            if (repository.isHomeLauncherEnabled()) {
                applyHomeLauncherSetting();
            }
            Intent launch = new Intent(context, LauncherActivity.class);
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(launch);
        }
    }

    private void applyHomeLauncherSetting() {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", "pm disable " + ORIGINAL_HOME_PACKAGE});
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                Log.w(TAG, "Failed to disable original launcher on boot, exit code: " + exitCode);
            }
        } catch (Exception e) {
            Log.w(TAG, "applyHomeLauncherSetting failed", e);
        }
    }
}
