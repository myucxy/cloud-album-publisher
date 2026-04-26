package com.cloudalbum.publisher.android.update;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.cloudalbum.publisher.android.BuildConfig;
import com.cloudalbum.publisher.android.data.model.AppUpdateResponse;
import com.cloudalbum.publisher.android.data.repository.CloudAlbumRepository;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AppUpdateChecker {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final String APK_MIME_TYPE = "application/vnd.android.package-archive";
    private static boolean checkedInProcess = false;

    private final AppCompatActivity activity;
    private final CloudAlbumRepository repository;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public AppUpdateChecker(AppCompatActivity activity, CloudAlbumRepository repository) {
        this.activity = activity;
        this.repository = repository;
    }

    public void checkOnce() {
        if (checkedInProcess) {
            return;
        }
        checkedInProcess = true;
        checkForUpdate(true, null);
    }

    public void checkForUpdate(final boolean showDialog, final UpdateCallback callback) {
        EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final AppUpdateResponse update = repository.checkAndroidUpdate();
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (callback != null) {
                                callback.onChecked(update);
                            }
                            if (showDialog && update != null && Boolean.TRUE.equals(update.getHasUpdate())) {
                                showUpdateDialog(update);
                            }
                        }
                    });
                } catch (final IOException error) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (callback != null) {
                                callback.onFailed(error);
                            }
                        }
                    });
                }
            }
        });
    }

    public void installUpdate(AppUpdateResponse update) {
        downloadAndInstall(update);
    }

    private void showUpdateDialog(AppUpdateResponse update) {
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(activity, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)
                .setTitle(Boolean.TRUE.equals(update.getForceUpdate()) ? "发现必须更新的客户端版本" : "发现客户端新版本")
                .setMessage(buildMessage(update))
                .setPositiveButton("下载并安装", (dialog, which) -> downloadAndInstall(update));
        if (!Boolean.TRUE.equals(update.getForceUpdate())) {
            builder.setNegativeButton("取消", null);
        }
        AlertDialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(!Boolean.TRUE.equals(update.getForceUpdate()));
        dialog.setOnShowListener(value -> dialog.setCancelable(!Boolean.TRUE.equals(update.getForceUpdate())));
        dialog.show();
        styleDialogText(dialog);
    }

    private void styleDialogText(AlertDialog dialog) {
        View windowView = dialog.getWindow() == null ? null : dialog.getWindow().getDecorView();
        if (windowView == null) {
            return;
        }
        styleTextViews(windowView);
    }

    private void styleTextViews(View view) {
        if (view instanceof TextView) {
            ((TextView) view).setTextColor(0xFF111827);
        }
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i += 1) {
                styleTextViews(group.getChildAt(i));
            }
        }
    }

    private String buildMessage(AppUpdateResponse update) {
        StringBuilder builder = new StringBuilder();
        if (notBlank(update.getLatestVersion())) {
            builder.append("最新版本：").append(update.getLatestVersion());
        }
        if (notBlank(update.getReleaseNotes())) {
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append(update.getReleaseNotes());
        }
        return builder.length() > 0 ? builder.toString() : "检测到新版本，是否下载并安装？";
    }

    private void downloadAndInstall(AppUpdateResponse update) {
        if (!canInstallPackages()) {
            openInstallPermissionSettings();
            return;
        }
        toast("开始下载更新安装包");
        EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    File apkFile = downloadApk(update);
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            toast("下载完成，正在打开安装界面");
                            openInstaller(apkFile);
                        }
                    });
                } catch (IOException error) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            toast(error.getMessage() == null ? "更新下载失败" : error.getMessage());
                        }
                    });
                }
            }
        });
    }

    private File downloadApk(AppUpdateResponse update) throws IOException {
        URL url = new URL(requireDownloadUrl(update));
        if (!"http".equalsIgnoreCase(url.getProtocol()) && !"https".equalsIgnoreCase(url.getProtocol())) {
            throw new IOException("不支持的更新下载地址");
        }

        File updatesDir = new File(activity.getExternalFilesDir(null), "updates");
        if (!updatesDir.exists() && !updatesDir.mkdirs()) {
            throw new IOException("无法创建更新下载目录");
        }

        String fileName = sanitizeApkFileName(update.getFileName(), url);
        File apkFile = new File(updatesDir, fileName);
        File tempFile = new File(updatesDir, fileName + ".part");
        deleteIfExists(apkFile);
        deleteIfExists(tempFile);

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(60000);
        connection.setInstanceFollowRedirects(false);
        int status = connection.getResponseCode();
        if (status >= 300 && status < 400) {
            throw new IOException("更新下载地址发生重定向，请使用最终下载地址");
        }
        if (status < 200 || status >= 300) {
            throw new IOException("更新下载失败：HTTP " + status);
        }

        try (InputStream input = connection.getInputStream(); FileOutputStream output = new FileOutputStream(tempFile)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        } finally {
            connection.disconnect();
        }

        verifyDownloadedApk(tempFile, update);
        if (!tempFile.renameTo(apkFile)) {
            throw new IOException("无法保存更新安装包");
        }
        return apkFile;
    }

    private void verifyDownloadedApk(File file, AppUpdateResponse update) throws IOException {
        Long expectedSize = update.getSize();
        if (expectedSize != null && expectedSize > 0 && file.length() != expectedSize) {
            deleteIfExists(file);
            throw new IOException("安装包大小校验失败");
        }
        String expectedHash = update.getSha256();
        if (notBlank(expectedHash)) {
            String actualHash = sha256(file);
            if (!expectedHash.toLowerCase(Locale.ROOT).equals(actualHash)) {
                deleteIfExists(file);
                throw new IOException("安装包 SHA-256 校验失败");
            }
        }
    }

    private void openInstaller(File apkFile) {
        try {
            Uri uri;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                uri = FileProvider.getUriForFile(activity, BuildConfig.APPLICATION_ID + ".fileprovider", apkFile);
            } else {
                uri = Uri.fromFile(apkFile);
            }
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, APK_MIME_TYPE);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(intent);
        } catch (ActivityNotFoundException error) {
            toast("未找到可安装 APK 的应用");
        } catch (IllegalArgumentException error) {
            toast("安装包路径不可用");
        }
    }

    private boolean canInstallPackages() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return true;
        }
        PackageManager packageManager = activity.getPackageManager();
        return packageManager.canRequestPackageInstalls();
    }

    private void openInstallPermissionSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
            intent.setData(Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(intent);
            toast("请允许本应用安装未知来源应用，授权后重新点击更新");
            return;
        }
        toast("请在系统设置中允许安装未知来源应用");
    }

    private String requireDownloadUrl(AppUpdateResponse update) throws IOException {
        String downloadUrl = update.getDownloadUrl();
        if (!notBlank(downloadUrl)) {
            throw new IOException("下载地址为空");
        }
        return downloadUrl.trim();
    }

    private String sanitizeApkFileName(String fileName, URL url) throws IOException {
        String candidate = notBlank(fileName) ? fileName.trim() : new File(url.getPath()).getName();
        candidate = new File(candidate).getName().replaceAll("[\\\\/:*?\"<>|]", "_");
        if (!candidate.toLowerCase(Locale.ROOT).endsWith(".apk")) {
            throw new IOException("不支持的安装包格式");
        }
        return candidate;
    }

    private String sha256(File file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException error) {
            throw new IOException("当前系统不支持 SHA-256 校验", error);
        }
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        StringBuilder builder = new StringBuilder();
        for (byte value : digest.digest()) {
            builder.append(String.format(Locale.ROOT, "%02x", value));
        }
        return builder.toString();
    }

    private void deleteIfExists(File file) {
        if (file.exists() && !file.delete()) {
            file.deleteOnExit();
        }
    }

    private void toast(String message) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
    }

    private boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public interface UpdateCallback {
        void onChecked(AppUpdateResponse update);

        void onFailed(IOException error);
    }
}
