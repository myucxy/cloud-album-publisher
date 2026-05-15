package com.cloudalbum.publisher.android.setup;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.cloudalbum.publisher.android.R;
import com.cloudalbum.publisher.android.data.model.DeviceResponse;
import com.cloudalbum.publisher.android.data.repository.CloudAlbumRepository;
import com.cloudalbum.publisher.android.data.repository.DeviceSessionRepository;
import com.cloudalbum.publisher.android.player.PlayerActivity;
import com.cloudalbum.publisher.android.ui.PageRotationController;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class SetupActivity extends AppCompatActivity {
    private DeviceSessionRepository sessionRepository;
    private CloudAlbumRepository repository;
    private EditText serverBaseUrlInput;
    private EditText deviceNameInput;
    private TextView deviceUidText;
    private TextView deviceTypeText;
    private TextView deviceModelText;
    private TextView deviceIdText;
    private TextView activationStatusText;
    private TextView setupStatusText;
    private TextView setupErrorText;
    private TextView setupDetailText;
    private Button saveButton;
    private Button playerButton;
    private Button discoverButton;
    private ScrollView setupRoot;
    private PageRotationController rotationController;
    private boolean forceSetup;
    private boolean saving;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);

        sessionRepository = new DeviceSessionRepository(this);
        repository = new CloudAlbumRepository(this);
        forceSetup = getIntent().getBooleanExtra("force_setup", false);
        bindViews();
        rotationController = new PageRotationController(this, setupRoot);
        populateFields();
        updateSetupStatus(getString(R.string.setup_status_idle), null, buildDeviceSnapshot());
        setupActions();
        applyPageRotation();
    }

    @Override
    protected void onResume() {
        super.onResume();
        populateFields();
        applyPageRotation();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyPageRotation();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            applyPageRotation();
        }
    }

    private void bindViews() {
        setupRoot = findViewById(R.id.setupRoot);
        serverBaseUrlInput = findViewById(R.id.serverBaseUrlInput);
        deviceNameInput = findViewById(R.id.deviceNameInput);
        deviceUidText = findViewById(R.id.deviceUidText);
        deviceTypeText = findViewById(R.id.deviceTypeText);
        deviceModelText = findViewById(R.id.deviceModelText);
        deviceIdText = findViewById(R.id.deviceIdText);
        activationStatusText = findViewById(R.id.activationStatusText);
        setupStatusText = findViewById(R.id.setupStatusText);
        setupErrorText = findViewById(R.id.setupErrorText);
        setupDetailText = findViewById(R.id.setupDetailText);
        saveButton = findViewById(R.id.saveButton);
        playerButton = findViewById(R.id.playerButton);
        discoverButton = findViewById(R.id.discoverButton);
    }

    private void applyPageRotation() {
        if (rotationController == null || sessionRepository == null) {
            return;
        }
        rotationController.apply(sessionRepository.getPlaybackRotationMode());
    }

    private void populateFields() {
        serverBaseUrlInput.setText(sessionRepository.getServerBaseUrl());
        deviceNameInput.setText(sessionRepository.getDeviceName());
        deviceUidText.setText(getString(R.string.device_uid) + "：" + sessionRepository.getDeviceUid());
        deviceTypeText.setText(getString(R.string.device_type) + "：" + sessionRepository.getDeviceType());
        deviceModelText.setText(getString(R.string.device_model) + "：" + sessionRepository.getDeviceModel());
        deviceIdText.setText(getString(R.string.device_id) + "：" + (sessionRepository.getDeviceId() > 0 ? sessionRepository.getDeviceId() : getString(R.string.unknown_value)));
        activationStatusText.setText(getString(R.string.activation_status) + "：" + (sessionRepository.isActivated() ? getString(R.string.status_activated) : getString(R.string.status_not_activated)));
    }

    private void setupActions() {
        saveButton.setOnClickListener(v -> saveAndRegister());
        playerButton.setOnClickListener(v -> {
            if (saveAndRegister()) {
                startActivity(new Intent(this, PlayerActivity.class));
            }
        });
        discoverButton.setOnClickListener(v -> startDiscovery());

        if (forceSetup) {
            saveButton.requestFocus();
        } else {
            playerButton.requestFocus();
        }
    }

    private boolean saveAndRegister() {
        if (saving) {
            return false;
        }

        String serverBaseUrl = serverBaseUrlInput.getText().toString().trim();
        if (serverBaseUrl.isEmpty()) {
            updateSetupStatus(getString(R.string.setup_status_failed), getString(R.string.toast_missing_server), buildDeviceSnapshot());
            return false;
        }

        sessionRepository.saveServerBaseUrl(serverBaseUrl);
        sessionRepository.saveDeviceName(deviceNameInput.getText().toString().trim());
        populateFields();
        setSaving(true);
        updateSetupStatus(getString(R.string.setup_status_saving), null, buildDeviceSnapshot());

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final DeviceResponse response = repository.selfRegisterCurrentDevice();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            populateFields();
                            String detail = buildDeviceSnapshot()
                                    + "\n\n执行阶段：selfRegisterCurrentDevice"
                                    + "\n\n服务端返回：\n"
                                    + "ID=" + (response == null ? getString(R.string.unknown_value) : response.getId())
                                    + "\nUID=" + (response == null ? getString(R.string.unknown_value) : safeText(response.getDeviceUid()))
                                    + "\n名称=" + (response == null ? getString(R.string.unknown_value) : safeText(response.getName()))
                                    + "\n类型=" + (response == null ? getString(R.string.unknown_value) : safeText(response.getType()))
                                    + "\n状态=" + (response == null ? getString(R.string.unknown_value) : safeText(response.getStatus()));
                            updateSetupStatus(getString(R.string.setup_status_success), null, detail);
                            Toast.makeText(SetupActivity.this, R.string.toast_settings_saved, Toast.LENGTH_SHORT).show();
                            setSaving(false);
                        }
                    });
                } catch (final Throwable error) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            populateFields();
                            updateSetupStatus(
                                    getString(R.string.setup_status_failed),
                                    describeThrowable(error),
                                    buildFailureDetail(error)
                            );
                            setSaving(false);
                        }
                    });
                }
            }
        }).start();
        return true;
    }

    private AlertDialog discoverLoadingDialog;

    private void startDiscovery() {
        String subnet = resolveLocalSubnet();
        if (subnet == null) {
            updateSetupStatus(getString(R.string.discover_none), null, null);
            return;
        }
        discoverButton.setEnabled(false);
        updateSetupStatus(getString(R.string.discover_scanning), null, "扫描网段：" + subnet + ".0/24");

        discoverLoadingDialog = new AlertDialog.Builder(this, R.style.Theme_CloudAlbum_Dialog)
                .setTitle(getString(R.string.discover_scanning))
                .setView(buildLoadingView())
                .setCancelable(false)
                .setNegativeButton(android.R.string.cancel, (d, w) -> {
                    // Thread will finish on its own; just dismiss
                })
                .show();

        new Thread(() -> {
            List<String> found = scanSubnet(subnet);
            runOnUiThread(() -> {
                if (discoverLoadingDialog != null && discoverLoadingDialog.isShowing()) {
                    discoverLoadingDialog.dismiss();
                }
                discoverButton.setEnabled(true);
                if (found.isEmpty()) {
                    updateSetupStatus(getString(R.string.discover_none), null, null);
                } else {
                    updateSetupStatus(String.format(getString(R.string.discover_found), found.size()), null, null);
                    showServerPicker(found);
                }
            });
        }).start();
    }

    private View buildLoadingView() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(android.view.Gravity.CENTER_VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);

        android.widget.ProgressBar spinner = new android.widget.ProgressBar(this);
        spinner.setIndeterminate(true);
        LinearLayout.LayoutParams spinnerLp = new LinearLayout.LayoutParams(
                (int) (24 * getResources().getDisplayMetrics().density),
                (int) (24 * getResources().getDisplayMetrics().density));
        spinnerLp.setMarginEnd((int) (12 * getResources().getDisplayMetrics().density));
        layout.addView(spinner, spinnerLp);

        TextView text = new TextView(this);
        text.setText(getString(R.string.discover_scanning));
        text.setTextColor(getResources().getColor(R.color.ca_text_secondary));
        text.setTextSize(14);
        layout.addView(text);

        return layout;
    }

    private String resolveLocalSubnet() {
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            int ip = wm.getConnectionInfo().getIpAddress();
            if (ip != 0) {
                return (ip & 0xFF) + "." + ((ip >> 8) & 0xFF) + "." + ((ip >> 16) & 0xFF);
            }
        } catch (Exception ignored) {
        }
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || !ni.isUp()) continue;
                for (InetAddress addr : Collections.list(ni.getInetAddresses())) {
                    if (addr instanceof Inet4Address) {
                        byte[] bytes = addr.getAddress();
                        return (bytes[0] & 0xFF) + "." + (bytes[1] & 0xFF) + "." + (bytes[2] & 0xFF);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private List<String> scanSubnet(String subnet) {
        List<String> results = Collections.synchronizedList(new ArrayList<>());
        List<Integer> ports = new ArrayList<>();
        ports.add(8080);
        try {
            URL savedUrl = new URL(sessionRepository.getServerBaseUrl());
            int savedPort = savedUrl.getPort();
            if (savedPort > 0 && !ports.contains(savedPort)) {
                ports.add(savedPort);
            }
        } catch (Exception ignored) {
        }
        ExecutorService pool = Executors.newFixedThreadPool(32);
        for (int i = 1; i <= 254; i++) {
            final String ip = subnet + "." + i;
            for (final int port : ports) {
                pool.execute(() -> {
                    try {
                        URL url = new URL("http://" + ip + ":" + port + "/api/v1/discover");
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setConnectTimeout(800);
                        conn.setReadTimeout(800);
                        conn.setRequestMethod("GET");
                        if (conn.getResponseCode() == 200) {
                            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                            StringBuilder sb = new StringBuilder();
                            String line;
                            while ((line = reader.readLine()) != null) sb.append(line);
                            reader.close();
                            JSONObject json = new JSONObject(sb.toString());
                            JSONObject data = json.optJSONObject("data");
                            if (data != null && "CloudAlbum".equals(data.optString("name"))) {
                                results.add(ip + ":" + data.optInt("port", port));
                            }
                        }
                        conn.disconnect();
                    } catch (Exception ignored) {
                    }
                });
            }
        }
        pool.shutdown();
        try {
            pool.awaitTermination(8, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
        }
        return results;
    }

    private void showServerPicker(List<String> servers) {
        String[] items = servers.toArray(new String[0]);
        new AlertDialog.Builder(this, R.style.Theme_CloudAlbum_Dialog)
                .setTitle(getString(R.string.discover_select_title))
                .setItems(items, (dialog, which) -> {
                    serverBaseUrlInput.setText(items[which]);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void setSaving(boolean value) {
        saving = value;
        saveButton.setEnabled(!value);
        playerButton.setEnabled(!value);
    }

    private void updateSetupStatus(String status, String error, String detail) {
        setupStatusText.setText(status);
        if (error == null || error.trim().isEmpty()) {
            setupErrorText.setVisibility(View.GONE);
            setupErrorText.setText("");
        } else {
            setupErrorText.setVisibility(View.VISIBLE);
            setupErrorText.setText(error);
        }
        if (detail == null || detail.trim().isEmpty()) {
            setupDetailText.setVisibility(View.GONE);
            setupDetailText.setText("");
        } else {
            setupDetailText.setVisibility(View.VISIBLE);
            setupDetailText.setText(getString(R.string.setup_status_detail_prefix) + detail);
        }
    }

    private String buildDeviceSnapshot() {
        return "当前配置："
                + "\n服务器=" + sessionRepository.getServerBaseUrl()
                + "\n设备名称=" + safeText(sessionRepository.getDeviceName())
                + "\n设备UID=" + safeText(sessionRepository.getDeviceUid())
                + "\n设备类型=" + safeText(sessionRepository.getDeviceType())
                + "\n设备型号=" + safeText(sessionRepository.getDeviceModel())
                + "\n设备ID=" + (sessionRepository.getDeviceId() > 0 ? sessionRepository.getDeviceId() : getString(R.string.unknown_value))
                + "\n激活状态=" + (sessionRepository.isActivated() ? getString(R.string.status_activated) : getString(R.string.status_not_activated));
    }

    private String buildFailureDetail(Throwable error) {
        return buildDeviceSnapshot()
                + "\n\n执行阶段：selfRegisterCurrentDevice"
                + "\n\n异常链：\n" + buildThrowableChain(error)
                + "\n\n堆栈：\n" + stackTraceOf(error);
    }

    private String describeThrowable(Throwable error) {
        if (error == null) {
            return getString(R.string.setup_status_failed);
        }
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String rootMessage = root.getMessage();
        if (rootMessage != null && !rootMessage.trim().isEmpty()) {
            return root.getClass().getName() + ": " + rootMessage;
        }
        String message = error.getMessage();
        if (message != null && !message.trim().isEmpty()) {
            return error.getClass().getName() + ": " + message;
        }
        return error.getClass().getName();
    }

    private String buildThrowableChain(Throwable error) {
        if (error == null) {
            return getString(R.string.unknown_value);
        }
        StringBuilder builder = new StringBuilder();
        Throwable current = error;
        int depth = 0;
        while (current != null && depth < 8) {
            if (depth > 0) {
                builder.append("\nCaused by: ");
            }
            builder.append(current.getClass().getName());
            if (current.getMessage() != null && !current.getMessage().trim().isEmpty()) {
                builder.append(": ").append(current.getMessage());
            }
            current = current.getCause();
            depth++;
        }
        return builder.toString();
    }

    private String stackTraceOf(Throwable error) {
        if (error == null) {
            return getString(R.string.unknown_value);
        }
        StringWriter writer = new StringWriter();
        PrintWriter printWriter = new PrintWriter(writer);
        error.printStackTrace(printWriter);
        printWriter.flush();
        String stack = writer.toString();
        if (stack.length() > 4000) {
            return stack.substring(0, 4000);
        }
        return stack;
    }

    private String safeText(String value) {
        if (value == null || value.trim().isEmpty()) {
            return getString(R.string.unknown_value);
        }
        return value;
    }
}
