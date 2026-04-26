package com.cloudalbum.publisher.android.player;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.cloudalbum.publisher.android.data.model.DevicePullResponse;
import com.cloudalbum.publisher.android.data.repository.DeviceSessionRepository;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MediaCacheManager {
    private static final String TAG = "MediaCacheManager";

    public interface Listener {
        void onCacheChanged();
    }

    private final Context appContext;
    private final DeviceSessionRepository sessionRepository;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final File cacheDir;
    private final Set<String> downloadingKeys = new HashSet<String>();
    private final Map<String, String> remoteUrlByLocalUrl = new ConcurrentHashMap<String, String>();
    private Listener listener;
    private volatile int pendingDownloads = 0;
    private volatile int failedDownloads = 0;

    public MediaCacheManager(Context context, DeviceSessionRepository sessionRepository) {
        this.appContext = context.getApplicationContext();
        this.sessionRepository = sessionRepository;
        File externalDir = appContext.getExternalFilesDir("media-cache");
        this.cacheDir = externalDir == null ? new File(appContext.getFilesDir(), "media-cache") : externalDir;
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public DevicePullResponse applyLocalCache(DevicePullResponse response) {
        if (response == null || response.getDistributions() == null) {
            return response;
        }
        if (!sessionRepository.isMediaCacheEnabled()) {
            return response;
        }
        Set<String> activeKeys = new HashSet<String>();
        for (DevicePullResponse.DistributionItem distribution : response.getDistributions()) {
            if (distribution == null || distribution.getMediaList() == null) {
                continue;
            }
            for (DevicePullResponse.MediaItem media : distribution.getMediaList()) {
                if (!isCacheable(media)) {
                    continue;
                }
                String remoteUrl = media.getUrl();
                String key = buildCacheKey(media);
                activeKeys.add(key);
                File localFile = new File(cacheDir, key);
                if (localFile.exists() && localFile.length() > 0) {
                    String localUrl = Uri.fromFile(localFile).toString();
                    remoteUrlByLocalUrl.put(localUrl, remoteUrl);
                    media.setUrl(localUrl);
                } else {
                    enqueueDownload(remoteUrl, localFile);
                }
            }
        }
        pruneCache(activeKeys);
        notifyChanged();
        return response;
    }

    public long getCacheBytes() {
        return directorySize(cacheDir);
    }

    public int getCacheFileCount() {
        File[] files = cacheDir.listFiles();
        if (files == null) return 0;
        int count = 0;
        for (File file : files) {
            if (isCacheFile(file)) count += 1;
        }
        return count;
    }

    public int getPendingDownloads() {
        return pendingDownloads;
    }

    public int getFailedDownloads() {
        return failedDownloads;
    }

    public boolean isLocalCacheUrl(String url) {
        return url != null && url.startsWith("file:") && remoteUrlByLocalUrl.containsKey(url);
    }

    public boolean isLocalCacheAvailable(String url) {
        if (!isLocalCacheUrl(url)) {
            return false;
        }
        File file = new File(Uri.parse(url).getPath());
        return file.exists() && file.length() > 0;
    }

    public String getRemoteUrlForLocalUrl(String url) {
        return url == null ? null : remoteUrlByLocalUrl.get(url);
    }

    public void invalidateLocalUrl(String url) {
        if (url == null) {
            return;
        }
        remoteUrlByLocalUrl.remove(url);
        if (!url.startsWith("file:")) {
            return;
        }
        try {
            File file = new File(Uri.parse(url).getPath());
            if (file.exists() && file.isFile()) {
                file.delete();
            }
        } catch (Exception e) {
            Log.w(TAG, "Invalidate cache failed: " + url, e);
        }
        notifyChanged();
    }

    public void clearCache() {
        deleteChildren(cacheDir);
        remoteUrlByLocalUrl.clear();
        failedDownloads = 0;
        notifyChanged();
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    private boolean isCacheable(DevicePullResponse.MediaItem media) {
        if (media == null || media.getUrl() == null || media.getUrl().trim().isEmpty()) {
            return false;
        }
        if (media.getUrl().startsWith("file:")) {
            return false;
        }
        String type = media.getMediaType() == null ? "" : media.getMediaType().trim().toUpperCase();
        return "IMAGE".equals(type);
    }

    private void enqueueDownload(final String remoteUrl, final File localFile) {
        if (localFile.exists() && localFile.length() > 0) {
            return;
        }
        synchronized (downloadingKeys) {
            if (downloadingKeys.contains(localFile.getName())) {
                return;
            }
            downloadingKeys.add(localFile.getName());
        }
        pendingDownloads += 1;
        notifyChanged();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                boolean success = false;
                try {
                    download(remoteUrl, localFile);
                    success = true;
                } catch (Exception e) {
                    failedDownloads += 1;
                    Log.w(TAG, "Download cache failed: " + localFile.getName() + " from " + remoteUrl, e);
                } finally {
                    synchronized (downloadingKeys) {
                        downloadingKeys.remove(localFile.getName());
                    }
                    pendingDownloads = Math.max(0, pendingDownloads - 1);
                    notifyChanged();
                }
            }
        });
    }

    private void download(String remoteUrl, File localFile) throws IOException {
        File tempFile = new File(localFile.getParentFile(), localFile.getName() + ".download");
        HttpURLConnection connection = null;
        InputStream inputStream = null;
        FileOutputStream outputStream = null;
        try {
            connection = (HttpURLConnection) new URL(remoteUrl).openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            String token = sessionRepository.getDeviceAccessToken();
            if (token != null && !token.trim().isEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer " + token);
            }
            connection.connect();
            if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300) {
                throw new IOException("HTTP " + connection.getResponseCode());
            }
            inputStream = connection.getInputStream();
            outputStream = new FileOutputStream(tempFile);
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            outputStream.flush();
            if (localFile.exists()) {
                localFile.delete();
            }
            if (!tempFile.renameTo(localFile)) {
                throw new IOException("rename failed");
            }
            localFile.setLastModified(System.currentTimeMillis());
            pruneCache(Collections.singleton(localFile.getName()));
        } finally {
            if (outputStream != null) outputStream.close();
            if (inputStream != null) inputStream.close();
            if (connection != null) connection.disconnect();
            if (tempFile.exists()) tempFile.delete();
        }
    }

    private void pruneCache(Set<String> activeKeys) {
        long limitBytes = sessionRepository.getMediaCacheLimitMb() * 1024L * 1024L;
        File[] files = cacheDir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isFile() && file.getName().endsWith(".download")) {
                file.delete();
            }
        }
        long total = getCacheBytes();
        if (total <= limitBytes) {
            return;
        }
        List<File> inactive = new ArrayList<File>();
        List<File> active = new ArrayList<File>();
        files = cacheDir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (!isCacheFile(file)) continue;
            if (activeKeys.contains(file.getName())) active.add(file); else inactive.add(file);
        }
        total = deleteFifoUntilUnderLimit(inactive, total, limitBytes);
        if (total > limitBytes) {
            deleteFifoUntilUnderLimit(active, total, limitBytes);
        }
    }

    private long deleteFifoUntilUnderLimit(List<File> files, long total, long limitBytes) {
        Collections.sort(files, new Comparator<File>() {
            @Override
            public int compare(File left, File right) {
                return Long.compare(left.lastModified(), right.lastModified());
            }
        });
        for (File file : files) {
            if (total <= limitBytes) break;
            synchronized (downloadingKeys) {
                if (downloadingKeys.contains(file.getName())) {
                    continue;
                }
            }
            long length = file.length();
            if (file.delete()) {
                total -= length;
            }
        }
        return total;
    }

    private long directorySize(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return 0L;
        long total = 0L;
        for (File file : files) {
            if (isCacheFile(file)) total += file.length();
        }
        return total;
    }

    private boolean isCacheFile(File file) {
        return file != null && file.isFile() && !file.getName().endsWith(".download");
    }

    private void deleteChildren(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) deleteChildren(file);
            file.delete();
        }
    }

    private String buildCacheKey(DevicePullResponse.MediaItem media) {
        String identity = media.getId() != null ? "id-" + media.getId() : media.getUrl();
        String suffix = resolveSuffix(media);
        return sha1(identity + "|" + media.getUrl()) + suffix;
    }

    private String resolveSuffix(DevicePullResponse.MediaItem media) {
        String fileName = media.getFileName();
        if (fileName != null) {
            int dot = fileName.lastIndexOf('.');
            if (dot >= 0 && dot < fileName.length() - 1) {
                return sanitizeSuffix(fileName.substring(dot));
            }
        }
        String type = media.getMediaType() == null ? "" : media.getMediaType().trim().toUpperCase();
        return "VIDEO".equals(type) ? ".mp4" : ".jpg";
    }

    private String sanitizeSuffix(String suffix) {
        return suffix.replaceAll("[^A-Za-z0-9.]", "").toLowerCase();
    }

    private String sha1(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] bytes = digest.digest(value.getBytes("UTF-8"));
            StringBuilder builder = new StringBuilder();
            for (byte b : bytes) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception ignored) {
            return String.valueOf(Math.abs(value.hashCode()));
        }
    }

    private void notifyChanged() {
        if (listener != null) {
            listener.onCacheChanged();
        }
    }

}
