package com.cloudalbum.publisher.android.data.repository;

import android.content.Context;
import android.content.SharedPreferences;

import com.cloudalbum.publisher.android.platform.DeviceIdentityProvider;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Set;

public class DeviceSessionRepository {
    private static final String PREFS = "cloud_album_device";
    private static final String DEFAULT_BASE_URL = "http://192.168.9.28:8080";
    private static final String KEY_SERVER_BASE_URL = "server_base_url";
    private static final String KEY_DEVICE_NAME = "device_name";
    private static final String KEY_DEVICE_ID = "device_id";
    private static final String KEY_DEVICE_ACCESS_TOKEN = "device_access_token";
    private static final String KEY_DISABLED_DISTRIBUTION_IDS = "disabled_distribution_ids";
    private static final String KEY_DISABLED_MEDIA_IDENTITIES = "disabled_media_identities";
    private static final String KEY_PLAYBACK_ROTATION_MODE = "playback_rotation_mode";
    private static final String KEY_PLAYBACK_MUTED = "playback_muted";
    private static final String KEY_MEDIA_CACHE_ENABLED = "media_cache_enabled";
    private static final String KEY_MEDIA_CACHE_LIMIT_MB = "media_cache_limit_mb";
    private static final String KEY_BRIGHTNESS_ENABLED = "brightness_enabled";
    private static final String KEY_BRIGHTNESS_START_HOUR = "brightness_start_hour";
    private static final String KEY_BRIGHTNESS_END_HOUR = "brightness_end_hour";
    private static final String KEY_BRIGHTNESS_DIM_LEVEL = "brightness_dim_level";

    public static final int DEFAULT_MEDIA_CACHE_LIMIT_MB = 2048;

    public static final String PLAYBACK_ROTATION_AUTO = "auto";
    public static final String PLAYBACK_ROTATION_0 = "0";
    public static final String PLAYBACK_ROTATION_90 = "90";
    public static final String PLAYBACK_ROTATION_180 = "180";
    public static final String PLAYBACK_ROTATION_270 = "270";

    private final SharedPreferences preferences;
    private final DeviceIdentityProvider deviceIdentityProvider;
    private final Context appContext;

    public DeviceSessionRepository(Context context) {
        this.appContext = context.getApplicationContext();
        this.preferences = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.deviceIdentityProvider = new DeviceIdentityProvider(appContext);
    }

    public String getServerBaseUrl() {
        return sanitizeBaseUrl(preferences.getString(KEY_SERVER_BASE_URL, DEFAULT_BASE_URL));
    }

    public void saveServerBaseUrl(String value) {
        preferences.edit().putString(KEY_SERVER_BASE_URL, sanitizeBaseUrl(value)).apply();
    }

    public String getDeviceName() {
        String value = preferences.getString(KEY_DEVICE_NAME, "");
        if (value != null && !value.trim().isEmpty()) {
            return value;
        }
        return getDefaultDeviceName();
    }

    public void saveDeviceName(String value) {
        String next = value == null ? "" : value.trim();
        if (next.isEmpty()) {
            next = getDefaultDeviceName();
        }
        preferences.edit().putString(KEY_DEVICE_NAME, next).apply();
    }

    public String getDeviceUid() {
        return deviceIdentityProvider.getOrCreateDeviceUid(appContext);
    }

    public String getDeviceType() {
        return deviceIdentityProvider.getDeviceType();
    }

    public String getDeviceModel() {
        return deviceIdentityProvider.getDeviceModel();
    }

    public long getDeviceId() {
        return preferences.getLong(KEY_DEVICE_ID, 0L);
    }

    public void saveDeviceId(long deviceId) {
        preferences.edit().putLong(KEY_DEVICE_ID, deviceId).apply();
    }

    public String getDeviceAccessToken() {
        String token = preferences.getString(KEY_DEVICE_ACCESS_TOKEN, "");
        return token == null ? "" : token;
    }

    public void saveDeviceAccessToken(String token) {
        preferences.edit().putString(KEY_DEVICE_ACCESS_TOKEN, token == null ? "" : token).apply();
    }

    public boolean isActivated() {
        return !getDeviceAccessToken().isEmpty();
    }

    public void clearDeviceSession() {
        preferences.edit().remove(KEY_DEVICE_ACCESS_TOKEN).remove(KEY_DEVICE_ID).apply();
    }

    public Set<String> getDisabledDistributionIds() {
        return new HashSet<String>(preferences.getStringSet(KEY_DISABLED_DISTRIBUTION_IDS, new HashSet<String>()));
    }

    public void saveDisabledDistributionIds(Set<String> values) {
        preferences.edit().putStringSet(KEY_DISABLED_DISTRIBUTION_IDS, copyStringSet(values)).apply();
    }

    public Set<String> getDisabledMediaIdentities() {
        return new HashSet<String>(preferences.getStringSet(KEY_DISABLED_MEDIA_IDENTITIES, new HashSet<String>()));
    }

    public void saveDisabledMediaIdentities(Set<String> values) {
        preferences.edit().putStringSet(KEY_DISABLED_MEDIA_IDENTITIES, copyStringSet(values)).apply();
    }

    public String getPlaybackRotationMode() {
        String mode = preferences.getString(KEY_PLAYBACK_ROTATION_MODE, PLAYBACK_ROTATION_AUTO);
        if (PLAYBACK_ROTATION_0.equals(mode)
                || PLAYBACK_ROTATION_90.equals(mode)
                || PLAYBACK_ROTATION_180.equals(mode)
                || PLAYBACK_ROTATION_270.equals(mode)
                || PLAYBACK_ROTATION_AUTO.equals(mode)) {
            return mode;
        }
        return PLAYBACK_ROTATION_AUTO;
    }

    public void savePlaybackRotationMode(String mode) {
        preferences.edit().putString(KEY_PLAYBACK_ROTATION_MODE, normalizePlaybackRotationMode(mode)).apply();
    }

    public boolean isPlaybackMuted() {
        return preferences.getBoolean(KEY_PLAYBACK_MUTED, false);
    }

    public void savePlaybackMuted(boolean muted) {
        preferences.edit().putBoolean(KEY_PLAYBACK_MUTED, muted).apply();
    }

    public boolean isMediaCacheEnabled() {
        return preferences.getBoolean(KEY_MEDIA_CACHE_ENABLED, false);
    }

    public void saveMediaCacheEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_MEDIA_CACHE_ENABLED, enabled).apply();
    }

    public int getMediaCacheLimitMb() {
        int value = preferences.getInt(KEY_MEDIA_CACHE_LIMIT_MB, DEFAULT_MEDIA_CACHE_LIMIT_MB);
        if (value == 512 || value == 1024 || value == 2048 || value == 5120 || value == 10240) {
            return value;
        }
        return DEFAULT_MEDIA_CACHE_LIMIT_MB;
    }

    public void saveMediaCacheLimitMb(int limitMb) {
        preferences.edit().putInt(KEY_MEDIA_CACHE_LIMIT_MB, normalizeMediaCacheLimitMb(limitMb)).apply();
    }

    public boolean isBrightnessScheduleEnabled() {
        return preferences.getBoolean(KEY_BRIGHTNESS_ENABLED, false);
    }

    public void saveBrightnessScheduleEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_BRIGHTNESS_ENABLED, enabled).apply();
    }

    public int getBrightnessStartHour() {
        int hour = preferences.getInt(KEY_BRIGHTNESS_START_HOUR, 7);
        return hour >= 0 && hour <= 23 ? hour : 7;
    }

    public void saveBrightnessStartHour(int hour) {
        preferences.edit().putInt(KEY_BRIGHTNESS_START_HOUR, Math.max(0, Math.min(23, hour))).apply();
    }

    public int getBrightnessEndHour() {
        int hour = preferences.getInt(KEY_BRIGHTNESS_END_HOUR, 22);
        return hour >= 0 && hour <= 23 ? hour : 22;
    }

    public void saveBrightnessEndHour(int hour) {
        preferences.edit().putInt(KEY_BRIGHTNESS_END_HOUR, Math.max(0, Math.min(23, hour))).apply();
    }

    public int getBrightnessDimLevel() {
        int level = preferences.getInt(KEY_BRIGHTNESS_DIM_LEVEL, 15);
        return level >= 0 && level <= 100 ? level : 15;
    }

    public void saveBrightnessDimLevel(int level) {
        preferences.edit().putInt(KEY_BRIGHTNESS_DIM_LEVEL, Math.max(0, Math.min(100, level))).apply();
    }

    public String getDefaultDeviceName() {
        return getDeviceModel();
    }

    public static String sanitizeBaseUrl(String value) {
        String baseUrl = value == null ? DEFAULT_BASE_URL : value.trim();
        if (baseUrl.isEmpty()) {
            baseUrl = DEFAULT_BASE_URL;
        }
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        String candidate = baseUrl.matches("(?i)^https?://.*") ? baseUrl : "http://" + baseUrl;
        try {
            URI uri = new URI(candidate);
            String host = uri.getHost();
            if (host == null || host.trim().isEmpty()) {
                return DEFAULT_BASE_URL;
            }
            String scheme = uri.getScheme() == null ? "http" : uri.getScheme().toLowerCase();
            int port = uri.getPort() > 0 ? uri.getPort() : 8080;
            return scheme + "://" + host + ":" + port;
        } catch (URISyntaxException ignored) {
            return DEFAULT_BASE_URL;
        }
    }

    private Set<String> copyStringSet(Set<String> values) {
        return values == null ? new HashSet<String>() : new HashSet<String>(values);
    }

    private String normalizePlaybackRotationMode(String mode) {
        if (PLAYBACK_ROTATION_0.equals(mode)
                || PLAYBACK_ROTATION_90.equals(mode)
                || PLAYBACK_ROTATION_180.equals(mode)
                || PLAYBACK_ROTATION_270.equals(mode)) {
            return mode;
        }
        return PLAYBACK_ROTATION_AUTO;
    }

    private int normalizeMediaCacheLimitMb(int limitMb) {
        if (limitMb == 512 || limitMb == 1024 || limitMb == 2048 || limitMb == 5120 || limitMb == 10240) {
            return limitMb;
        }
        return DEFAULT_MEDIA_CACHE_LIMIT_MB;
    }
}
