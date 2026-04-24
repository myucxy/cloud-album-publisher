package com.cloudalbum.publisher.android.platform;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;

import java.util.UUID;

public class DeviceIdentityProvider {
    private static final String PREFS = "cloud_album_device";
    private static final String KEY_DEVICE_UID = "device_uid";

    private final SharedPreferences preferences;

    public DeviceIdentityProvider(Context context) {
        this.preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public String getOrCreateDeviceUid(Context context) {
        String existing = preferences.getString(KEY_DEVICE_UID, "");
        if (existing != null && !existing.trim().isEmpty()) {
            return existing;
        }

        String androidId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        String deviceUid = (androidId != null && !androidId.trim().isEmpty() && !"9774d56d682e549c".equals(androidId))
                ? "android-" + androidId.toLowerCase()
                : "android-" + UUID.randomUUID().toString().replace("-", "");
        preferences.edit().putString(KEY_DEVICE_UID, deviceUid).apply();
        return deviceUid;
    }

    public String getDeviceType() {
        return "ANDROID";
    }

    public String getDeviceModel() {
        return Build.MANUFACTURER + " " + Build.MODEL + " (Android " + Build.VERSION.RELEASE + ")";
    }
}
