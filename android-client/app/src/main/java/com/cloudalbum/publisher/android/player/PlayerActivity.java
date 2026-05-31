package com.cloudalbum.publisher.android.player;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.RectEvaluator;
import android.animation.ValueAnimator;
import android.app.ActivityManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Display;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.cloudalbum.publisher.android.R;
import com.cloudalbum.publisher.android.data.model.AppUpdateResponse;
import com.cloudalbum.publisher.android.data.model.DevicePullResponse;
import com.cloudalbum.publisher.android.data.model.DeviceTokenResponse;
import com.cloudalbum.publisher.android.data.repository.CloudAlbumRepository;
import com.cloudalbum.publisher.android.data.repository.DeviceSessionRepository;
import com.cloudalbum.publisher.android.setup.SetupActivity;
import com.cloudalbum.publisher.android.ui.PageRotationController;
import com.cloudalbum.publisher.android.update.AppUpdateChecker;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.ui.StyledPlayerView;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.text.SimpleDateFormat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PlayerActivity extends AppCompatActivity implements PullSyncCoordinator.Listener {
    private static final String TAG = "PlayerActivity";
    private static final long TOKEN_POLL_BASE_MS = 15000L;
    private static final long TOKEN_POLL_MAX_MS = 120000L;
    private static final int TOKEN_MAX_RETRIES = 10;
    private static final long UPDATE_POLL_INTERVAL_MS = 60 * 1000L;
    private static final long IMAGE_ADVANCE_RETRY_DELAY_MS = 200L;
    private static final int IMAGE_ADVANCE_MAX_RETRY_COUNT = 8;
    private static final int DRAWER_HANDLE_SWIPE_THRESHOLD_DP = 36;
    private static final long IMAGE_TRANSITION_DURATION_MS = 650L;
    private static final int ADVANCED_IMAGE_POOL_SIZE = 19;
    private static final long ADVANCED_READY_RETRY_DELAY_MS = 150L;
    private static final int ADVANCED_READY_MAX_RETRIES = 15;
    private static final String[] RANDOM_IMAGE_TRANSITIONS = new String[] {"FADE", "SLIDE", "CUBE", "REVEAL", "FLIP"};
    private static final Set<String> ADVANCED_DISPLAY_STYLES = new HashSet<String>();

    static {
        ADVANCED_DISPLAY_STYLES.add("BENTO");
        ADVANCED_DISPLAY_STYLES.add("FRAME_WALL");
        ADVANCED_DISPLAY_STYLES.add("FRAMEWALL");
        ADVANCED_DISPLAY_STYLES.add("CAROUSEL");
    }
    private static final String REFRESH_BUTTON_LABEL = "\u7acb\u5373\u540c\u6b65";
    private static final String SETUP_BUTTON_LABEL = "\u670d\u52a1\u5668\u8bbe\u7f6e";
    private static final String APP_UPDATE_TITLE = "\u7248\u672c\u66f4\u65b0";
    private static final String APP_UPDATE_CHECKING = "\u6b63\u5728\u68c0\u67e5\u65b0\u7248\u672c...";
    private static final String APP_UPDATE_CURRENT = "\u5df2\u662f\u6700\u65b0\u7248\u672c";
    private static final String APP_UPDATE_AVAILABLE_BUTTON = "\u4e0b\u8f7d\u5e76\u5b89\u88c5";
    private static final String APP_UPDATE_CHECK_FAILED = "\u7248\u672c\u68c0\u67e5\u5931\u8d25";
    private static final String PLAYBACK_SELECTION_TITLE = "\u5206\u7ec4\u9009\u62e9";
    private static final String PLAYBACK_SELECTION_EMPTY = "\u6682\u65e0\u5df2\u540c\u6b65\u5206\u7ec4";
    private static final String PLAYBACK_SELECTION_WAITING = "\u7b49\u5f85\u540c\u6b65\u540e\u53ef\u9009\u62e9\u5206\u7ec4";
    private static final String PLAYBACK_SELECTION_ALL_DISABLED = "\u5f53\u524d\u5df2\u5168\u90e8\u5173\u95ed";
    private static final String WAITING_FOR_BINDING_STATUS = "\u7b49\u5f85\u540e\u53f0\u7ed1\u5b9a";
    private static final String STYLE_SWITCHING_STATUS = "\u6837\u5f0f\u5207\u6362\u4e2d\uff0c\u8bf7\u7a0d\u5019...";
    private static final String ROTATION_TITLE = "\u5c4f\u5e55\u65b9\u5411";
    private static final String ROTATION_AUTO_LABEL = "\u81ea\u52a8\uff08\u8ddf\u968f\u8bbe\u5907\uff09";
    private static final String ROTATION_0_LABEL = "\u4e0d\u65cb\u8f6c";
    private static final String ROTATION_90_LABEL = "\u65cb\u8f6c 90 \u5ea6";
    private static final String ROTATION_180_LABEL = "\u65cb\u8f6c 180 \u5ea6";
    private static final String ROTATION_270_LABEL = "\u65cb\u8f6c 270 \u5ea6";
    private static final String MUTE_TITLE = "\u58f0\u97f3\u8bbe\u7f6e";
    private static final String MUTE_OPTION_LABEL = "\u9759\u97f3\u64ad\u653e";
    private static final String MUTE_ENABLED_LABEL = "\u5f53\u524d\u5df2\u9759\u97f3";
    private static final String MUTE_DISABLED_LABEL = "\u5f53\u524d\u5df2\u5f00\u542f\u58f0\u97f3";
    private static final String DISPLAY_MODE_TITLE = "\u663e\u793a\u6e05\u6670\u5ea6";
    private static final String DISPLAY_MODE_ENABLE_LABEL = "\u4f18\u5148\u4f7f\u7528\u8bbe\u5907\u6700\u9ad8\u5206\u8fa8\u7387";
    private static final String DISPLAY_MODE_SUMMARY_DISABLED = "\u5df2\u5173\u95ed\uff0c\u4f7f\u7528\u7cfb\u7edf\u9ed8\u8ba4\u663e\u793a\u6a21\u5f0f";
    private static final String BRIGHTNESS_TITLE = "\u4eae\u5ea6\u8c03\u8282";
    private static final String BRIGHTNESS_ENABLE_LABEL = "\u542f\u7528\u5b9a\u65f6\u4eae\u5ea6";
    private static final String BRIGHTNESS_SUMMARY_ENABLED = "\u65f6\u6bb5\u5916\u81ea\u52a8\u964d\u4f4e\u4eae\u5ea6";
    private static final String BRIGHTNESS_SUMMARY_DISABLED = "\u672a\u542f\u7528\u5b9a\u65f6\u4eae\u5ea6";
    private static final long BRIGHTNESS_CHECK_INTERVAL_MS = 60000L;
    private static final String BOOT_AUTO_START_TITLE = "开机自启动";
    private static final String BOOT_AUTO_START_LABEL = "开机时自动启动应用";
    private static final String HOME_LAUNCHER_LABEL = "设置为默认桌面";
    private static final String HOME_LAUNCHER_SUMMARY_SET = "已设为默认桌面，按HOME键回到本应用";
    private static final String HOME_LAUNCHER_SUMMARY_UNSET = "设为默认桌面后，按HOME键将回到本应用";
    private static final String LAUNCH_ORIGINAL_HOME_LABEL = "启动原桌面";
    private static final String LAUNCH_ORIGINAL_HOME_CONFIRM = "将临时启动原桌面，按HOME键可返回本应用";
    private static final String HOME_LAUNCHER_SET_SUCCESS = "已设置为默认桌面";
    private static final String HOME_LAUNCHER_SET_FAILED = "设置失败，需要Root权限";
    private static final String HOME_LAUNCHER_UNSET_SUCCESS = "已恢复原桌面";
    private static final String HOME_LAUNCHER_UNSET_FAILED = "恢复失败";
    private static final String ORIGINAL_HOME_PACKAGE = "com.dangbei.tvlauncher";
    private static final String ORIGINAL_HOME_ACTIVITY = "com.dangbei.tvlauncher/com.dangbei.launcher.ui.main.MainActivity";
    private static final String SYSTEM_CAPABILITY_TITLE = "\u7cfb\u7edf\u80fd\u529b";
    private static final String SYSTEM_CAPABILITY_CLOSE = "\u5173\u95ed";
    private static final String[] SYSTEM_CAPABILITY_MIME_TYPES = new String[] {
            "video/avc",
            "video/hevc",
            "video/mp4v-es",
            "video/x-vnd.on2.vp8",
            "video/x-vnd.on2.vp9",
            "video/av01",
            "audio/mpeg",
            "audio/mp4a-latm",
            "audio/opus",
            "audio/vorbis"
    };
    private static final String[] SYSTEM_CAPABILITY_LABELS = new String[] {
            "H.264 / AVC",
            "H.265 / HEVC",
            "MPEG-4 Visual",
            "VP8",
            "VP9",
            "AV1",
            "MP3",
            "AAC",
            "Opus",
            "Vorbis"
    };

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService backgroundExecutor = Executors.newFixedThreadPool(2);

    private final Runnable imageAdvanceRunnable = new Runnable() {
        @Override
        public void run() {
            advanceImageWhenReady();
        }
    };
    private final Runnable tokenPollRunnable = new Runnable() {
        @Override
        public void run() {
            attemptAcquireToken(false);
        }
    };
    private final Runnable updatePollRunnable = new Runnable() {
        @Override
        public void run() {
            checkAppUpdate(false);
        }
    };
    private final Runnable clockRunnable = new Runnable() {
        @Override
        public void run() {
            updateClockViews();
            mainHandler.postDelayed(this, 1000L);
        }
    };
    private final Runnable brightnessCheckRunnable = new Runnable() {
        @Override
        public void run() {
            applyBrightness();
            mainHandler.postDelayed(this, BRIGHTNESS_CHECK_INTERVAL_MS);
        }
    };
    private final Set<String> disabledDistributionIds = new HashSet<String>();
    private final Set<String> preloadedImageIdentities = new HashSet<String>();
    private int tokenRetryCount = 0;
    private ConnectivityManager.NetworkCallback networkCallback;
    private final List<View> drawerFocusableViews = new ArrayList<View>();
    private CloudAlbumRepository repository;
    private DeviceSessionRepository sessionRepository;
    private PullSyncCoordinator pullSyncCoordinator;
    private AppUpdateChecker appUpdateChecker;
    private PlaybackEngine playbackEngine;
    private SimpleExoPlayer contentPlayer;
    private SimpleExoPlayer bgmPlayer;

    private FrameLayout pageRoot;
    private FrameLayout playbackStageContainer;
    private FrameLayout drawerGestureHandle;
    private DrawerLayout drawerLayout;
    private ScrollView menuDrawer;
    private LinearLayout deviceInfoPanel;
    private LinearLayout currentMediaPanel;
    private ImageView imageStage;
    private ImageView imageStageNext;
    private FrameLayout advancedImageStage;
    private FrameLayout advancedImageStageNext;
    private StyledPlayerView playerView;
    private TextView deviceSummaryText;
    private TextView distributionText;
    private TextView mediaText;
    private TextView statusText;
    private TextView emptyStateText;
    private FrameLayout calendarStage;
    private ImageView calendarImageView;
    private ImageView calendarImageViewNext;
    private boolean primaryCalendarStageActive = true;
    private LinearLayout calendarInfoPanel;
    private TextView calendarMonthText;
    private TextView calendarDayText;
    private TextView calendarWeekdayText;
    private TextView calendarLunarText;
    private LinearLayout timeOverlay;
    private TextView overlayTimeText;
    private TextView overlayDateText;
    private TextView rotationTitleText;
    private TextView rotationSummaryText;
    private TextView muteTitleText;
    private TextView muteSummaryText;
    private TextView displayModeTitleText;
    private TextView displayModeSummaryText;
    private TextView playbackSelectionTitleText;
    private TextView playbackSelectionSummaryText;
    private TextView appUpdateTitleText;
    private TextView appUpdateSummaryText;
    private TextView systemCapabilityTitleText;
    private TextView systemCapabilityContentText;
    private LinearLayout playbackSelectionContainer;
    private Spinner rotationSpinner;
    private FrameLayout systemCapabilityOverlay;
    private CheckBox muteToggleCheckBox;
    private CheckBox displayModeToggleCheckBox;
    private Button refreshButton;
    private Button setupButton;
    private Button appUpdateButton;
    private LinearLayout appUpdatePanel;
    private LinearLayout rotationPanel;
    private LinearLayout mutePanel;
    private LinearLayout displayModePanel;
    private LinearLayout brightnessPanel;
    private LinearLayout bootAutoStartPanel;
    private Button aboutButton;
    private Button systemCapabilityButton;
    private Button systemCapabilityCloseButton;
    private TextView brightnessTitleText;
    private TextView brightnessSummaryText;
    private CheckBox brightnessToggleCheckBox;
    private Spinner brightnessStartSpinner;
    private Spinner brightnessEndSpinner;
    private Spinner brightnessDimSpinner;
    private TextView bootAutoStartTitleText;
    private CheckBox bootAutoStartToggleCheckBox;
    private CheckBox homeLauncherToggleCheckBox;
    private Button launchOriginalHomeButton;
    private CompoundButton.OnCheckedChangeListener homeLauncherCheckedChangeListener;
    private ScrollView systemCapabilityScrollView;

    private String lastRenderedMediaIdentity = "";
    private String lastRenderedMediaType = "";
    private String lastRenderedStyleSignature = "";
    private String lastRenderedBgmIdentity = "";
    private String playbackRotationMode = DeviceSessionRepository.PLAYBACK_ROTATION_AUTO;
    private String playerAccessToken = "";
    private boolean playbackMuted = false;
    private boolean waitingForBinding = false;
    private DevicePullResponse remotePullResponse;
    private DevicePullResponse lastPullResponse;
    private PageRotationController rotationController;
    private AppUpdateResponse availableUpdate;
    private String pendingAdvancedMediaIdentity = "";
    private boolean advancedNextReady = false;
    private int advancedReadyRetryCount = 0;
    private boolean advancedPrimaryStageActive = true;
    private int advancedPendingLoadCount = 0;
    private boolean advancedAnyLoadSuccess = false;
    private final List<ImageView> bentoImageViews = new ArrayList<ImageView>();
    private final List<ImageView> frameWallImageViews = new ArrayList<ImageView>();
    private int bentoNextSourceIndex = 0;
    private int bentoPreviousSlotIndex = -1;
    private int bentoPreviousSlotIndex2 = -1;
    private int frameWallNextSourceIndex = 0;
    private int frameWallPreviousSlotIndex = -1;
    private int carouselActiveSourceIndex = 0;
    private int advancedDisplayedCountInCycle = 0;
    private int advancedCycleMediaCount = 0;
    private final List<DevicePullResponse.MediaItem> cachedAdvancedImageItems = new ArrayList<DevicePullResponse.MediaItem>();
    private String cachedAdvancedImageItemsKey = "";
    private float drawerHandleDownX;
    private boolean drawerHandleOpenedBySwipe;
    private boolean primaryImageStageActive = true;
    private int imageAdvanceRetryCount = 0;
    private Animator imageTransitionAnimator;
    private boolean useSimpleTransition = false;
    private int transitionPerfProbeCount = 0;
    private long transitionStartTimeMs;
    private boolean bindingSpinners = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sessionRepository = new DeviceSessionRepository(this);
        repository = new CloudAlbumRepository(this);

        configureFullscreenWindow();
        setContentView(R.layout.activity_player);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        playbackEngine = new PlaybackEngine();
        pullSyncCoordinator = new PullSyncCoordinator(repository, this);
        appUpdateChecker = new AppUpdateChecker(this, repository);

        bindViews();
        rotationController = new PageRotationController(this, pageRoot);
        loadPlaybackSelection();
        configureSelectionPanel();
        setupButtons();
        setupPlayers();
        applyPlaybackRotation();
        updateDeviceSummary();
        updateStatus(getString(R.string.sync_status_idle));
        registerNetworkCallback();
    }

    @Override
    protected void onStart() {
        super.onStart();
        enterImmersiveMode();
        checkAppUpdate(true);
        if (sessionRepository.isActivated()) {
            waitingForBinding = false;
            pullSyncCoordinator.start();
            startBrightnessCheck();
        } else {
            waitingForBinding = true;
            showWaitingForBinding();
            attemptAcquireToken(true);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        pullSyncCoordinator.stop();
        clearScheduledTasks();
        mainHandler.removeCallbacks(tokenPollRunnable);
        mainHandler.removeCallbacks(updatePollRunnable);
        mainHandler.removeCallbacks(brightnessCheckRunnable);
        if (contentPlayer != null) {
            contentPlayer.pause();
        }
        if (bgmPlayer != null) {
            bgmPlayer.pause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        clearScheduledTasks();
        mainHandler.removeCallbacksAndMessages(null);
        unregisterNetworkCallback();
        pullSyncCoordinator.shutdown();
        releasePlayers();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            enterImmersiveMode();
            applyPlaybackRotation();
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        enterImmersiveMode();
        applyPlaybackRotation();
        updateRotationSummary();
        updateDisplayModeSummary();
        updateDeviceSummary();
    }

    @Override
    public boolean dispatchKeyEvent(@NonNull KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (isSystemCapabilityVisible()) {
                if (event.getKeyCode() == KeyEvent.KEYCODE_BACK
                        || event.getKeyCode() == KeyEvent.KEYCODE_ESCAPE
                        || event.getKeyCode() == KeyEvent.KEYCODE_MENU
                        || event.getKeyCode() == KeyEvent.KEYCODE_SETTINGS) {
                    hideSystemCapabilityOverlay();
                    return true;
                }
                return super.dispatchKeyEvent(event);
            }
            if (event.getKeyCode() == KeyEvent.KEYCODE_MENU || event.getKeyCode() == KeyEvent.KEYCODE_SETTINGS) {
                toggleDrawerMenu();
                return true;
            }
            if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_LEFT && !isDrawerOpen()) {
                openDrawerMenu();
                return true;
            }
            if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_RIGHT && isDrawerOpen()) {
                closeDrawerMenu();
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        if (isSystemCapabilityVisible()) {
            hideSystemCapabilityOverlay();
            return;
        }
        if (isDrawerOpen()) {
            closeDrawerMenu();
            return;
        }
        super.onBackPressed();
    }

    private void bindViews() {
        pageRoot = findViewById(R.id.playerRoot);
        playbackStageContainer = findViewById(R.id.playbackStageContainer);
        drawerGestureHandle = findViewById(R.id.drawerGestureHandle);
        drawerLayout = findViewById(R.id.playerDrawerLayout);
        menuDrawer = findViewById(R.id.menuDrawer);
        deviceInfoPanel = findViewById(R.id.deviceInfoPanel);
        currentMediaPanel = findViewById(R.id.currentMediaPanel);
        imageStage = findViewById(R.id.imageStage);
        imageStageNext = findViewById(R.id.imageStageNext);
        advancedImageStage = findViewById(R.id.advancedImageStage);
        advancedImageStageNext = findViewById(R.id.advancedImageStageNext);
        calendarStage = findViewById(R.id.calendarStage);
        calendarImageView = findViewById(R.id.calendarImageView);
        calendarImageViewNext = findViewById(R.id.calendarImageViewNext);
        calendarInfoPanel = findViewById(R.id.calendarInfoPanel);
        calendarMonthText = findViewById(R.id.calendarMonthText);
        calendarDayText = findViewById(R.id.calendarDayText);
        calendarWeekdayText = findViewById(R.id.calendarWeekdayText);
        calendarLunarText = findViewById(R.id.calendarLunarText);
        timeOverlay = findViewById(R.id.timeOverlay);
        overlayTimeText = findViewById(R.id.overlayTimeText);
        overlayDateText = findViewById(R.id.overlayDateText);
        playerView = findViewById(R.id.playerView);
        deviceSummaryText = findViewById(R.id.deviceSummaryText);
        distributionText = findViewById(R.id.distributionText);
        mediaText = findViewById(R.id.mediaText);
        statusText = findViewById(R.id.statusText);
        emptyStateText = findViewById(R.id.emptyStateText);
        rotationTitleText = findViewById(R.id.rotationTitleText);
        rotationSummaryText = findViewById(R.id.rotationSummaryText);
        muteTitleText = findViewById(R.id.muteTitleText);
        muteSummaryText = findViewById(R.id.muteSummaryText);
        displayModeTitleText = findViewById(R.id.displayModeTitleText);
        displayModeSummaryText = findViewById(R.id.displayModeSummaryText);
        playbackSelectionTitleText = findViewById(R.id.playbackSelectionTitleText);
        playbackSelectionSummaryText = findViewById(R.id.playbackSelectionSummaryText);
        appUpdateTitleText = findViewById(R.id.appUpdateTitleText);
        appUpdateSummaryText = findViewById(R.id.appUpdateSummaryText);
        systemCapabilityOverlay = findViewById(R.id.systemCapabilityOverlay);
        systemCapabilityTitleText = findViewById(R.id.systemCapabilityTitleText);
        systemCapabilityContentText = findViewById(R.id.systemCapabilityContentText);
        systemCapabilityScrollView = findViewById(R.id.systemCapabilityScrollView);
        playbackSelectionContainer = findViewById(R.id.playbackSelectionContainer);
        rotationSpinner = findViewById(R.id.rotationSpinner);
        muteToggleCheckBox = findViewById(R.id.muteToggleCheckBox);
        displayModeToggleCheckBox = findViewById(R.id.displayModeToggleCheckBox);
        refreshButton = findViewById(R.id.refreshButton);
        setupButton = findViewById(R.id.setupButton);
        appUpdateButton = findViewById(R.id.appUpdateButton);
        appUpdatePanel = findViewById(R.id.appUpdatePanel);
        rotationPanel = findViewById(R.id.rotationPanel);
        mutePanel = findViewById(R.id.mutePanel);
        displayModePanel = findViewById(R.id.displayModePanel);
        brightnessPanel = findViewById(R.id.brightnessPanel);
        bootAutoStartPanel = findViewById(R.id.bootAutoStartPanel);
        aboutButton = findViewById(R.id.aboutButton);
        systemCapabilityButton = findViewById(R.id.systemCapabilityButton);
        systemCapabilityCloseButton = findViewById(R.id.systemCapabilityCloseButton);
        brightnessTitleText = findViewById(R.id.brightnessTitleText);
        brightnessSummaryText = findViewById(R.id.brightnessSummaryText);
        brightnessToggleCheckBox = findViewById(R.id.brightnessToggleCheckBox);
        brightnessStartSpinner = findViewById(R.id.brightnessStartSpinner);
        brightnessEndSpinner = findViewById(R.id.brightnessEndSpinner);
        brightnessDimSpinner = findViewById(R.id.brightnessDimSpinner);
        bootAutoStartTitleText = findViewById(R.id.bootAutoStartTitleText);
        bootAutoStartToggleCheckBox = findViewById(R.id.bootAutoStartToggleCheckBox);
        homeLauncherToggleCheckBox = findViewById(R.id.homeLauncherToggleCheckBox);
        launchOriginalHomeButton = findViewById(R.id.launchOriginalHomeButton);
    }

    private void loadPlaybackSelection() {
        disabledDistributionIds.clear();
        disabledDistributionIds.addAll(sessionRepository.getDisabledDistributionIds());
        sessionRepository.saveDisabledMediaIdentities(new HashSet<String>());
        playbackRotationMode = sessionRepository.getPlaybackRotationMode();
        playbackMuted = sessionRepository.isPlaybackMuted();
        playbackEngine.setDisabledPlayback(disabledDistributionIds, new HashSet<String>());
    }

    private void configureSelectionPanel() {
        refreshButton.setText(REFRESH_BUTTON_LABEL);
        setupButton.setText(SETUP_BUTTON_LABEL);
        rotationTitleText.setText(ROTATION_TITLE);
        rebuildRotationOptions();
        muteTitleText.setText(MUTE_TITLE);
        muteToggleCheckBox.setText(MUTE_OPTION_LABEL);
        muteToggleCheckBox.setChecked(playbackMuted);
        applyDrawerOptionStyle(muteToggleCheckBox);
        updateMuteSummary();
        displayModeTitleText.setText(DISPLAY_MODE_TITLE);
        displayModeToggleCheckBox.setText(DISPLAY_MODE_ENABLE_LABEL);
        displayModeToggleCheckBox.setChecked(sessionRepository.isPreferNativeDisplayModeEnabled());
        applyDrawerOptionStyle(displayModeToggleCheckBox);
        updateDisplayModeSummary();
        brightnessTitleText.setText(BRIGHTNESS_TITLE);
        brightnessToggleCheckBox.setText(BRIGHTNESS_ENABLE_LABEL);
        brightnessToggleCheckBox.setChecked(sessionRepository.isBrightnessScheduleEnabled());
        applyDrawerOptionStyle(brightnessToggleCheckBox);
        rebuildBrightnessHourOptions(brightnessStartSpinner, sessionRepository.getBrightnessStartHour());
        rebuildBrightnessHourOptions(brightnessEndSpinner, sessionRepository.getBrightnessEndHour());
        rebuildBrightnessDimOptions();
        updateBrightnessSummary();
        updateBrightnessSpinnerFocusable(sessionRepository.isBrightnessScheduleEnabled());
        bootAutoStartTitleText.setText(BOOT_AUTO_START_TITLE);
        bootAutoStartToggleCheckBox.setText(BOOT_AUTO_START_LABEL);
        bootAutoStartToggleCheckBox.setChecked(sessionRepository.isBootAutoStartEnabled());
        applyDrawerOptionStyle(bootAutoStartToggleCheckBox);
        boolean isHomeLauncher = sessionRepository.isHomeLauncherEnabled();
        homeLauncherToggleCheckBox.setText(HOME_LAUNCHER_LABEL);
        homeLauncherToggleCheckBox.setChecked(isHomeLauncher);
        applyDrawerOptionStyle(homeLauncherToggleCheckBox);
        launchOriginalHomeButton.setText(LAUNCH_ORIGINAL_HOME_LABEL);
        applyDrawerButtonStyle(launchOriginalHomeButton);
        updateHomeLauncherUI(isHomeLauncher);
        playbackSelectionTitleText.setText(PLAYBACK_SELECTION_TITLE);
        showPlaybackSelectionMessage(PLAYBACK_SELECTION_WAITING);
        appUpdateTitleText.setText(APP_UPDATE_TITLE);
        appUpdateButton.setText(APP_UPDATE_AVAILABLE_BUTTON);
        updateAppUpdatePanel(null, APP_UPDATE_CHECKING);
        systemCapabilityButton.setText(SYSTEM_CAPABILITY_TITLE);
        systemCapabilityTitleText.setText(SYSTEM_CAPABILITY_TITLE);
        systemCapabilityCloseButton.setText(SYSTEM_CAPABILITY_CLOSE);
    }

    private void setupButtons() {
        setupDrawerGestureHandle();
        drawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerOpened(@NonNull View drawerView) {
                drawerGestureHandle.setVisibility(View.GONE);
                focusFirstDrawerItem();
                enterImmersiveMode();
            }

            @Override
            public void onDrawerClosed(@NonNull View drawerView) {
                drawerGestureHandle.setVisibility(View.VISIBLE);
                enterImmersiveMode();
            }
        });
        refreshButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeDrawerMenu();
                if (waitingForBinding) {
                    attemptAcquireToken(true);
                } else {
                    pullSyncCoordinator.refreshNow();
                }
            }
        });
        setupButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeDrawerMenu();
                startActivity(new Intent(PlayerActivity.this, SetupActivity.class).putExtra("force_setup", true));
            }
        });
        appUpdateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (availableUpdate != null) {
                    appUpdateChecker.installUpdate(availableUpdate);
                }
            }
        });
        appUpdatePanel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkAppUpdate(false);
            }
        });
        muteToggleCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                updatePlaybackMuted(isChecked);
            }
        });
        displayModeToggleCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                sessionRepository.savePreferNativeDisplayModeEnabled(isChecked);
                applyPreferredDisplayMode();
                updateDisplayModeSummary();
                updateDeviceSummary();
                preloadedImageIdentities.clear();
                lastRenderedMediaIdentity = "";
                renderPlayback();
            }
        });
        brightnessToggleCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                sessionRepository.saveBrightnessScheduleEnabled(isChecked);
                updateBrightnessSummary();
                updateBrightnessSpinnerFocusable(isChecked);
                applyBrightness();
            }
        });
        brightnessStartSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (bindingSpinners) return;
                sessionRepository.saveBrightnessStartHour(position);
                applyBrightness();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        brightnessEndSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (bindingSpinners) return;
                sessionRepository.saveBrightnessEndHour(position);
                applyBrightness();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        brightnessDimSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (bindingSpinners) return;
                int[] levels = new int[] {5, 10, 15, 20, 30, 50};
                sessionRepository.saveBrightnessDimLevel(levels[position]);
                applyBrightness();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        bootAutoStartToggleCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                sessionRepository.saveBootAutoStartEnabled(isChecked);
            }
        });
        homeLauncherCheckedChangeListener = new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                boolean success = setHomeLauncherEnabled(isChecked);
                if (success) {
                    sessionRepository.saveHomeLauncherEnabled(isChecked);
                    updateHomeLauncherUI(isChecked);
                    Toast.makeText(PlayerActivity.this,
                            isChecked ? HOME_LAUNCHER_SET_SUCCESS : HOME_LAUNCHER_UNSET_SUCCESS,
                            Toast.LENGTH_SHORT).show();
                } else {
                    buttonView.setOnCheckedChangeListener(null);
                    buttonView.setChecked(!isChecked);
                    buttonView.setOnCheckedChangeListener(this);
                    Toast.makeText(PlayerActivity.this,
                            isChecked ? HOME_LAUNCHER_SET_FAILED : HOME_LAUNCHER_UNSET_FAILED,
                            Toast.LENGTH_SHORT).show();
                }
            }
        };
        homeLauncherToggleCheckBox.setOnCheckedChangeListener(homeLauncherCheckedChangeListener);
        rotationPanel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                rotationSpinner.performClick();
            }
        });
        mutePanel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                muteToggleCheckBox.toggle();
            }
        });
        displayModePanel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                displayModeToggleCheckBox.toggle();
            }
        });
        launchOriginalHomeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean restored = setHomeLauncherEnabled(false);
                if (!restored) {
                    Toast.makeText(PlayerActivity.this, HOME_LAUNCHER_UNSET_FAILED, Toast.LENGTH_SHORT).show();
                    return;
                }
                sessionRepository.saveHomeLauncherEnabled(false);
                homeLauncherToggleCheckBox.setOnCheckedChangeListener(null);
                homeLauncherToggleCheckBox.setChecked(false);
                homeLauncherToggleCheckBox.setOnCheckedChangeListener(homeLauncherCheckedChangeListener);
                updateHomeLauncherUI(false);
                Toast.makeText(PlayerActivity.this, "已恢复原桌面，正在启动...", Toast.LENGTH_SHORT).show();
                Intent launch = new Intent(Intent.ACTION_MAIN);
                launch.addCategory(Intent.CATEGORY_HOME);
                launch.addCategory(Intent.CATEGORY_DEFAULT);
                launch.setClassName(ORIGINAL_HOME_PACKAGE, ORIGINAL_HOME_ACTIVITY);
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    startActivity(launch);
                } catch (Exception e) {
                    Log.w(TAG, "Failed to launch original home", e);
                    Toast.makeText(PlayerActivity.this, "启动原桌面失败", Toast.LENGTH_SHORT).show();
                }
            }
        });
        aboutButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(PlayerActivity.this, com.cloudalbum.publisher.android.about.AboutActivity.class);
                startActivity(intent);
            }
        });
        systemCapabilityButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSystemCapabilityDialog();
            }
        });
        systemCapabilityCloseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideSystemCapabilityOverlay();
            }
        });
        systemCapabilityOverlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideSystemCapabilityOverlay();
            }
        });
        rebuildDrawerFocusOrder();
    }

    private void setupDrawerGestureHandle() {
        drawerGestureHandle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isDrawerOpen()) {
                    openDrawerMenu();
                }
            }
        });
        drawerGestureHandle.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (isDrawerOpen() || isSystemCapabilityVisible()) {
                    return false;
                }
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    drawerHandleDownX = event.getX();
                    drawerHandleOpenedBySwipe = false;
                    return true;
                }
                if (event.getAction() == MotionEvent.ACTION_MOVE
                        && event.getX() - drawerHandleDownX >= dp(DRAWER_HANDLE_SWIPE_THRESHOLD_DP)) {
                    openDrawerMenu();
                    drawerHandleOpenedBySwipe = true;
                    return true;
                }
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    if (!drawerHandleOpenedBySwipe) {
                        v.performClick();
                    }
                    return true;
                }
                if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                    return true;
                }
                return false;
            }
        });
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            drawerGestureHandle.post(new Runnable() {
                @Override
                public void run() {
                    drawerGestureHandle.setSystemGestureExclusionRects(java.util.Collections.singletonList(
                            new Rect(0, 0, drawerGestureHandle.getWidth(), drawerGestureHandle.getHeight())
                    ));
                }
            });
        }
    }

    private void setupPlayers() {
        releasePlayers();
        playerAccessToken = sessionRepository.getDeviceAccessToken();
        contentPlayer = new SimpleExoPlayer.Builder(this)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(buildHttpFactory()))
                .build();
        contentPlayer.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(com.google.android.exoplayer2.C.USAGE_MEDIA)
                .setContentType(com.google.android.exoplayer2.C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(), true);
        playerView.setPlayer(contentPlayer);
        contentPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_ENDED) {
                    playbackEngine.nextMedia();
                    renderPlayback();
                }
            }

            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                Toast.makeText(PlayerActivity.this, R.string.toast_media_error, Toast.LENGTH_SHORT).show();
                mainHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) return;
                        playbackEngine.nextMedia();
                        renderPlayback();
                    }
                }, 1500L);
            }
        });

        bgmPlayer = new SimpleExoPlayer.Builder(this)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(buildHttpFactory()))
                .build();
        bgmPlayer.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(com.google.android.exoplayer2.C.USAGE_MEDIA)
                .setContentType(com.google.android.exoplayer2.C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(), false);
        bgmPlayer.setRepeatMode(Player.REPEAT_MODE_OFF);
        bgmPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_ENDED) {
                    playbackEngine.nextBgm();
                    renderBgm();
                }
            }

            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                Toast.makeText(PlayerActivity.this, R.string.toast_bgm_error, Toast.LENGTH_SHORT).show();
                mainHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) return;
                        playbackEngine.nextBgm();
                        renderBgm();
                    }
                }, 1500L);
            }
        });
        applyPlaybackMutedState();
    }

    private void ensurePlayersAuthorized() {
        String latestToken = sessionRepository.getDeviceAccessToken();
        if (contentPlayer != null && bgmPlayer != null && safeEquals(playerAccessToken, latestToken)) {
            return;
        }
        setupPlayers();
    }

    private void releasePlayers() {
        if (contentPlayer != null) {
            contentPlayer.release();
            contentPlayer = null;
        }
        if (bgmPlayer != null) {
            bgmPlayer.release();
            bgmPlayer = null;
        }
    }

    private DefaultHttpDataSource.Factory buildHttpFactory() {
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Authorization", "Bearer " + sessionRepository.getDeviceAccessToken());
        return new DefaultHttpDataSource.Factory().setDefaultRequestProperties(headers);
    }

    private void attemptAcquireToken(boolean immediateFeedback) {
        mainHandler.removeCallbacks(tokenPollRunnable);
        backgroundExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    repository.selfRegisterCurrentDevice();
                    DeviceTokenResponse tokenResponse = repository.issueDeviceToken();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (isFinishing() || isDestroyed()) return;
                            tokenRetryCount = 0;
                            waitingForBinding = false;
                            updateDeviceSummary();
                            updateStatus(getString(R.string.sync_status_ready));
                            ensurePlayersAuthorized();
                            if (immediateFeedback) {
                                Toast.makeText(PlayerActivity.this, R.string.status_activated, Toast.LENGTH_SHORT).show();
                            }
                            pullSyncCoordinator.start();
                        }
                    });
                } catch (final Throwable error) {
                    Log.e(TAG, "acquire token failed (attempt " + (tokenRetryCount + 1) + "/" + TOKEN_MAX_RETRIES + ")", error);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (isFinishing() || isDestroyed()) return;
                            waitingForBinding = true;
                            showWaitingForBinding();
                            tokenRetryCount += 1;
                            if (tokenRetryCount >= TOKEN_MAX_RETRIES) {
                                updateStatus("连接服务器失败，点击重试");
                                emptyStateText.setOnClickListener(new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        tokenRetryCount = 0;
                                        attemptAcquireToken(false);
                                    }
                                });
                                return;
                            }
                            String errorMsg = error.getMessage() == null || error.getMessage().trim().isEmpty()
                                    ? WAITING_FOR_BINDING_STATUS
                                    : error.getMessage();
                            updateStatus(errorMsg + " (重试 " + tokenRetryCount + "/" + TOKEN_MAX_RETRIES + ")");
                            long delay = Math.min(TOKEN_POLL_BASE_MS * (1L << Math.min(tokenRetryCount - 1, 4)), TOKEN_POLL_MAX_MS);
                            mainHandler.postDelayed(tokenPollRunnable, delay);
                        }
                    });
                }
            }
        });
    }

    private void updateDeviceSummary() {
        deviceSummaryText.setText(sessionRepository.getDeviceName() + " / " + sessionRepository.getDeviceModel());
    }

    private void registerNetworkCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return;
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm == null) return;
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                Log.i(TAG, "Network available, triggering immediate pull");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing() || isDestroyed()) return;
                        if (sessionRepository.isActivated() && !waitingForBinding) {
                            pullSyncCoordinator.refreshNow();
                        } else if (waitingForBinding) {
                            tokenRetryCount = 0;
                            attemptAcquireToken(false);
                        }
                    }
                });
            }
        };
        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
        cm.registerNetworkCallback(request, networkCallback);
    }

    private void unregisterNetworkCallback() {
        if (networkCallback == null) return;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return;
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm != null) {
            try {
                cm.unregisterNetworkCallback(networkCallback);
            } catch (Exception ignored) {
            }
        }
        networkCallback = null;
    }

    @Override
    public void onPullSuccess(final DevicePullResponse response) {
        backgroundExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final DevicePullResponse cloned = deepCopyPullResponse(response);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (isFinishing() || isDestroyed()) return;
                            remotePullResponse = cloned;
                            applyPlaybackResponse(response, true);
                            updateStatus(getString(R.string.sync_status_ready) + " / "
                                    + (playbackEngine.getPulledAt().isEmpty() ? getString(R.string.unknown_value) : playbackEngine.getPulledAt()));
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "onPullSuccess processing failed", e);
                }
            }
        });
    }

    private DevicePullResponse deepCopyPullResponse(DevicePullResponse response) {
        if (response == null) {
            return null;
        }
        DevicePullResponse copy = new DevicePullResponse();
        copy.setDistributions(new ArrayList<DevicePullResponse.DistributionItem>());
        List<DevicePullResponse.DistributionItem> dists = response.getDistributions();
        if (dists != null) {
            for (DevicePullResponse.DistributionItem dist : dists) {
                DevicePullResponse.DistributionItem distCopy = new DevicePullResponse.DistributionItem();
                distCopy.setMediaList(new ArrayList<DevicePullResponse.MediaItem>());
                List<DevicePullResponse.MediaItem> mediaList = dist.getMediaList();
                if (mediaList != null) {
                    for (DevicePullResponse.MediaItem media : mediaList) {
                        DevicePullResponse.MediaItem mediaCopy = new DevicePullResponse.MediaItem();
                        mediaCopy.setUrl(media.getUrl());
                        distCopy.getMediaList().add(mediaCopy);
                    }
                }
                copy.getDistributions().add(distCopy);
            }
        }
        return copy;
    }

    private void applyPlaybackResponse(DevicePullResponse response, boolean syncSelection) {
        lastPullResponse = response;
        cachedAdvancedImageItemsKey = "";
        playbackEngine.update(response);
        if (syncSelection) {
            syncPlaybackSelectionState(response);
        }
        renderPlayback();
    }

    @Override
    public void onPullError(Exception error) {
        if (CloudAlbumRepository.isDeviceSessionInvalid(error)) {
            pullSyncCoordinator.stop();
            sessionRepository.clearDeviceSession();
            waitingForBinding = true;
            showWaitingForBinding();
            updateStatus(error.getMessage() == null ? WAITING_FOR_BINDING_STATUS : error.getMessage());
            attemptAcquireToken(false);
            return;
        }
        updateStatus(getString(R.string.sync_status_error) + " / "
                + (error.getMessage() == null ? getString(R.string.toast_sync_failed) : error.getMessage()));
    }

    private void showWaitingForBinding() {
        clearScheduledTasks();
        lastPullResponse = null;
        cachedAdvancedImageItemsKey = "";
        remotePullResponse = null;
        lastRenderedMediaIdentity = "";
        lastRenderedMediaType = "";
        lastRenderedStyleSignature = "";
        lastRenderedBgmIdentity = "";
        preloadedImageIdentities.clear();
        imageAdvanceRetryCount = 0;
        hideImageStages();
        hideAdvancedImageStage();
        playerView.setVisibility(View.GONE);
        emptyStateText.setVisibility(View.VISIBLE);
        showPlaybackSelectionMessage(PLAYBACK_SELECTION_WAITING);
        distributionText.setText(getString(R.string.distribution_label) + ": " + getString(R.string.unknown_value));
        mediaText.setText(getString(R.string.media_label) + ": " + WAITING_FOR_BINDING_STATUS);
        if (contentPlayer != null) {
            contentPlayer.stop();
        }
        if (bgmPlayer != null) {
            bgmPlayer.stop();
        }
    }

    private void renderPlayback() {
        if (isFinishing() || isDestroyed()) return;
        DevicePullResponse.DistributionItem distribution = playbackEngine.getCurrentDistribution();
        DevicePullResponse.MediaItem media = playbackEngine.getCurrentMedia();

        if (!playbackEngine.hasPlayableContent() || distribution == null || media == null) {
            clearScheduledTasks();
            lastRenderedMediaIdentity = "";
            lastRenderedMediaType = "";
            lastRenderedStyleSignature = "";
            lastRenderedBgmIdentity = "";
            preloadedImageIdentities.clear();
            imageAdvanceRetryCount = 0;
            hideImageStages();
            hideAdvancedImageStage();
            hideCalendarStage();
            playerView.setVisibility(View.GONE);
            if (timeOverlay != null) timeOverlay.setVisibility(View.GONE);
            emptyStateText.setVisibility(View.VISIBLE);
            distributionText.setText(getString(R.string.distribution_label) + ": " + getString(R.string.unknown_value));
            mediaText.setText(getString(R.string.media_label) + ": " + getString(R.string.no_playable_content));
            if (contentPlayer != null) {
                contentPlayer.stop();
            }
            if (bgmPlayer != null) {
                bgmPlayer.stop();
            }
            if (lastPullResponse != null) {
                rebuildPlaybackSelectionMenu(lastPullResponse);
            } else {
                showPlaybackSelectionMessage(PLAYBACK_SELECTION_EMPTY);
            }
            return;
        }

        ensurePlayersAuthorized();
        emptyStateText.setVisibility(View.GONE);
        distributionText.setText(getString(R.string.distribution_label) + ": " + safeText(distribution.getName(), getString(R.string.unknown_value)));
        mediaText.setText(getString(R.string.media_label) + ": " + safeText(media.getFileName(), getString(R.string.unknown_value)));
        updateTimeOverlayVisibility();

        String currentMediaIdentity = PlaybackEngine.resolveMediaIdentity(media);
        String displayStyle = resolveDisplayStyle(playbackEngine.getCurrentDisplayStyle(), playbackEngine.getCurrentTransitionStyle());
        String styleSignature = buildCurrentStyleSignature(displayStyle);
        boolean styleChanged = !lastRenderedStyleSignature.isEmpty() && !styleSignature.equals(lastRenderedStyleSignature);
        if (styleChanged) {
            showStyleSwitchingHint();
            clearScheduledTasks();
            lastRenderedMediaIdentity = "";
            lastRenderedMediaType = "";
        }
        if ("CALENDAR".equals(displayStyle)) {
            if (!currentMediaIdentity.equals(lastRenderedMediaIdentity) || !"CALENDAR".equals(lastRenderedMediaType)) {
                clearScheduledTasks();
                lastRenderedMediaIdentity = currentMediaIdentity;
                lastRenderedMediaType = "CALENDAR";
                lastRenderedStyleSignature = styleSignature;
                renderCalendarMedia();
            } else {
                mainHandler.removeCallbacks(imageAdvanceRunnable);
                mainHandler.postDelayed(imageAdvanceRunnable, playbackEngine.getCurrentItemDurationSeconds() * 1000L);
            }
            renderBgm();
            return;
        }
        if (!currentMediaIdentity.equals(lastRenderedMediaIdentity)) {
            clearScheduledTasks();
            imageAdvanceRetryCount = 0;
            String previousMediaType = lastRenderedMediaType;
            lastRenderedMediaIdentity = currentMediaIdentity;
            lastRenderedMediaType = safeText(media.getMediaType(), "");
            lastRenderedStyleSignature = styleSignature;
            if ("IMAGE".equalsIgnoreCase(media.getMediaType())) {
                hideCalendarStage();
                playerView.setVisibility(View.GONE);
                if (contentPlayer != null) {
                    contentPlayer.stop();
                }
                if (isAdvancedDisplayStyle(displayStyle)) {
                    renderAdvancedImageMedia(displayStyle, currentMediaIdentity);
                } else {
                    hideAdvancedImageStage();
                    renderImageMedia(media, currentMediaIdentity, "IMAGE".equalsIgnoreCase(previousMediaType));
                }
            } else {
                hideImageStages();
                hideAdvancedImageStage();
                hideCalendarStage();
                playerView.setVisibility(View.VISIBLE);
                MediaItem mediaItem = new MediaItem.Builder()
                        .setUri(Uri.parse(media.getUrl()))
                        .setMimeType(media.getContentType())
                        .build();
                if (contentPlayer != null) {
                    contentPlayer.setMediaItem(mediaItem, true);
                    contentPlayer.prepare();
                    contentPlayer.play();
                }
            }
        } else if ("IMAGE".equalsIgnoreCase(media.getMediaType())) {
            mainHandler.removeCallbacks(imageAdvanceRunnable);
            if (!isAdvancedDisplayStyle(displayStyle)) {
                preloadNextImage();
            }
            mainHandler.postDelayed(imageAdvanceRunnable, playbackEngine.getCurrentItemDurationSeconds() * 1000L);
        } else if (contentPlayer != null && !contentPlayer.isPlaying()) {
            contentPlayer.play();
        }

        renderBgm();
    }

    private String buildCurrentStyleSignature(String displayStyle) {
        return safeText(displayStyle, "SINGLE")
                + "|" + safeText(playbackEngine.getCurrentDisplayVariant(), "DEFAULT")
                + "|" + safeText(playbackEngine.getCurrentTransitionStyle(), "NONE")
                + "|" + playbackEngine.isCurrentShowTimeAndDate();
    }

    private void showStyleSwitchingHint() {
        updateStatus(STYLE_SWITCHING_STATUS);
        if (emptyStateText == null) {
            return;
        }
        emptyStateText.setText(STYLE_SWITCHING_STATUS);
        emptyStateText.setVisibility(View.VISIBLE);
        emptyStateText.bringToFront();
        emptyStateText.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (emptyStateText != null && STYLE_SWITCHING_STATUS.contentEquals(emptyStateText.getText())) {
                    emptyStateText.setVisibility(View.GONE);
                    emptyStateText.setText(R.string.no_playable_content);
                }
            }
        }, 1500L);
    }

    private void renderImageMedia(final DevicePullResponse.MediaItem media, final String mediaIdentity, final boolean previousWasImage) {
        final ImageView targetStage = getInactiveImageStage();
        final ImageView currentStage = getActiveImageStage();
        resetImageStage(targetStage);
        targetStage.setVisibility(View.INVISIBLE);
        loadPlaybackImage(targetStage, media, new AuthenticatedImageLoader.Callback() {
            @Override
            public void onSuccess() {
                if (isFinishing()) return;
                if (!mediaIdentity.equals(lastRenderedMediaIdentity)) {
                    return;
                }
                String transitionStyle = (previousWasImage && !useSimpleTransition)
                        ? resolveImageTransitionStyle(playbackEngine.getCurrentTransitionStyle(), mediaIdentity) : "NONE";
                if ("NONE".equals(transitionStyle) || currentStage.getVisibility() != View.VISIBLE) {
                    showImageWithoutTransition(targetStage, currentStage);
                } else {
                    runImageTransition(currentStage, targetStage, transitionStyle);
                }
                preloadedImageIdentities.add(mediaIdentity);
                preloadNextImage();
                mainHandler.removeCallbacks(imageAdvanceRunnable);
                mainHandler.postDelayed(imageAdvanceRunnable, playbackEngine.getCurrentItemDurationSeconds() * 1000L);
            }

            @Override
            public void onFailure() {
                if (isFinishing()) return;
                if (!mediaIdentity.equals(lastRenderedMediaIdentity)) {
                    return;
                }
                Toast.makeText(PlayerActivity.this, R.string.toast_media_error, Toast.LENGTH_SHORT).show();
                mainHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) return;
                        playbackEngine.nextMedia();
                        renderPlayback();
                    }
                }, 1500L);
            }
        });
    }

    private void loadPlaybackImage(final ImageView imageView, final DevicePullResponse.MediaItem media, final AuthenticatedImageLoader.Callback callback) {
        if (media == null) {
            if (callback != null) callback.onFailure();
            return;
        }
        final String requestedUrl = media.getUrl();
        final int[] targetSize = resolvePreferredImageDecodeTarget(imageView);

        Double focalX = media.getFocalPointX();
        Double focalY = media.getFocalPointY();

        if (focalX != null && focalY != null) {
            AuthenticatedImageLoader.loadWithFocalPoint(imageView, requestedUrl, sessionRepository,
                    targetSize[0], targetSize[1], callback,
                    focalX, focalY, media.getFocalPointRegionWidth(), media.getFocalPointRegionHeight());
        } else {
            AuthenticatedImageLoader.load(imageView, requestedUrl, sessionRepository, targetSize[0], targetSize[1], callback);
        }
    }

    private int[] resolvePreferredImageDecodeTarget(ImageView imageView) {
        int[] defaultTarget = new int[] {0, 0};
        if (imageView == null || sessionRepository == null || !sessionRepository.isPreferNativeDisplayModeEnabled()
                || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return defaultTarget;
        }
        Display.Mode mode = findLargestDisplayMode();
        if (mode == null) {
            return defaultTarget;
        }
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int appWidth = Math.max(1, metrics.widthPixels);
        int appHeight = Math.max(1, metrics.heightPixels);
        int modeWidth = Math.max(1, mode.getPhysicalWidth());
        int modeHeight = Math.max(1, mode.getPhysicalHeight());
        if ((appWidth < appHeight && modeWidth > modeHeight) || (appWidth > appHeight && modeWidth < modeHeight)) {
            int tmp = modeWidth;
            modeWidth = modeHeight;
            modeHeight = tmp;
        }
        if ((long) modeWidth * (long) modeHeight <= (long) appWidth * (long) appHeight) {
            return defaultTarget;
        }
        int viewWidth = imageView.getWidth();
        int viewHeight = imageView.getHeight();
        if (viewWidth <= 0 && imageView.getLayoutParams() != null && imageView.getLayoutParams().width > 0) {
            viewWidth = imageView.getLayoutParams().width;
        }
        if (viewHeight <= 0 && imageView.getLayoutParams() != null && imageView.getLayoutParams().height > 0) {
            viewHeight = imageView.getLayoutParams().height;
        }
        if (viewWidth <= 0) {
            viewWidth = appWidth;
        }
        if (viewHeight <= 0) {
            viewHeight = appHeight;
        }
        int targetWidth = Math.max(1, Math.round(viewWidth * (modeWidth / (float) appWidth)));
        int targetHeight = Math.max(1, Math.round(viewHeight * (modeHeight / (float) appHeight)));
        return new int[] {targetWidth, targetHeight};
    }

    private void renderCalendarMedia() {
        hideImageStages();
        hideAdvancedImageStage();
        if (playerView != null) {
            playerView.setVisibility(View.GONE);
        }
        if (contentPlayer != null) {
            contentPlayer.stop();
        }
        if (calendarStage != null) {
            calendarStage.setVisibility(View.VISIBLE);
        }
        layoutCalendarStage();
        DevicePullResponse.MediaItem media = playbackEngine.getCurrentMedia();
        if (media == null || !"IMAGE".equalsIgnoreCase(media.getMediaType())) {
            updateClockViews();
            mainHandler.removeCallbacks(clockRunnable);
            mainHandler.postDelayed(clockRunnable, 1000L);
            mainHandler.removeCallbacks(imageAdvanceRunnable);
            mainHandler.postDelayed(imageAdvanceRunnable, playbackEngine.getCurrentItemDurationSeconds() * 1000L);
            return;
        }
        final ImageView targetStage = getInactiveCalendarStage();
        final ImageView currentStage = getActiveCalendarStage();
        resetImageStage(targetStage);
        targetStage.setVisibility(View.INVISIBLE);
        loadPlaybackImage(targetStage, media, new AuthenticatedImageLoader.Callback() {
            @Override
            public void onSuccess() {
                if (isFinishing()) return;
                String currentIdentity = PlaybackEngine.resolveMediaIdentity(playbackEngine.getCurrentMedia());
                if (!currentIdentity.equals(lastRenderedMediaIdentity)) return;
                if (useSimpleTransition) {
                    showCalendarImageWithoutTransition(targetStage, currentStage);
                    targetStage.setAlpha(0.2f);
                    targetStage.animate().alpha(1f).setDuration(800L).start();
                    return;
                }
                boolean previousWasCalendarImage = "CALENDAR".equals(lastRenderedMediaType) && currentStage.getDrawable() != null;
                String transitionStyle = previousWasCalendarImage
                        ? resolveImageTransitionStyle(playbackEngine.getCurrentTransitionStyle(), currentIdentity)
                        : "NONE";
                if ("NONE".equals(transitionStyle) || currentStage.getVisibility() != View.VISIBLE) {
                    showCalendarImageWithoutTransition(targetStage, currentStage);
                } else {
                    runCalendarImageTransition(currentStage, targetStage, transitionStyle);
                }
            }

            @Override
            public void onFailure() {
                if (isFinishing()) return;
                showCalendarImageWithoutTransition(targetStage, currentStage);
            }
        });
        updateClockViews();
        mainHandler.removeCallbacks(clockRunnable);
        mainHandler.postDelayed(clockRunnable, 1000L);
        mainHandler.removeCallbacks(imageAdvanceRunnable);
        mainHandler.postDelayed(imageAdvanceRunnable, playbackEngine.getCurrentItemDurationSeconds() * 1000L);
    }

    private void showCalendarImageWithoutTransition(ImageView targetStage, ImageView oldStage) {
        cancelCalendarImageTransition();
        resetImageStage(targetStage);
        targetStage.setVisibility(View.VISIBLE);
        oldStage.setVisibility(View.GONE);
        resetImageStage(oldStage);
        primaryCalendarStageActive = targetStage == calendarImageView;
    }

    private Animator calendarImageTransitionAnimator;

    private void markTransitionStart() {
        transitionStartTimeMs = System.currentTimeMillis();
    }

    private void evaluateTransitionPerformance() {
        if (useSimpleTransition) return;
        long elapsed = System.currentTimeMillis() - transitionStartTimeMs;
        if (transitionPerfProbeCount < 2) {
            transitionPerfProbeCount += 1;
            if (elapsed > IMAGE_TRANSITION_DURATION_MS * 2) {
                useSimpleTransition = true;
            }
        }
    }

    private void cancelCalendarImageTransition() {
        if (calendarImageTransitionAnimator != null) {
            calendarImageTransitionAnimator.cancel();
            calendarImageTransitionAnimator = null;
        }
    }

    private void runCalendarImageTransition(final ImageView fromStage, final ImageView toStage, String transitionStyle) {
        cancelCalendarImageTransition();
        resetImageStage(fromStage);
        resetImageStage(toStage);
        fromStage.setVisibility(View.VISIBLE);
        toStage.setVisibility(View.VISIBLE);
        markTransitionStart();
        Animator animator;
        if ("SLIDE".equals(transitionStyle)) {
            animator = buildSlideTransition(fromStage, toStage);
        } else if ("CUBE".equals(transitionStyle)) {
            animator = buildCubeTransition(fromStage, toStage);
        } else if ("REVEAL".equals(transitionStyle)) {
            animator = buildRevealTransition(fromStage, toStage);
        } else if ("FLIP".equals(transitionStyle)) {
            animator = buildFlipTransition(fromStage, toStage);
        } else {
            animator = buildFadeTransition(fromStage, toStage);
        }
        calendarImageTransitionAnimator = animator;
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                calendarImageTransitionAnimator = null;
                evaluateTransitionPerformance();
                fromStage.setVisibility(View.GONE);
                resetImageStage(fromStage);
                resetImageStage(toStage);
                toStage.setVisibility(View.VISIBLE);
                primaryCalendarStageActive = toStage == calendarImageView;
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                calendarImageTransitionAnimator = null;
            }
        });
        animator.start();
    }

    private ImageView getActiveCalendarStage() {
        return primaryCalendarStageActive ? calendarImageView : calendarImageViewNext;
    }

    private ImageView getInactiveCalendarStage() {
        return primaryCalendarStageActive ? calendarImageViewNext : calendarImageView;
    }

    private void hideCalendarStage() {
        cancelCalendarImageTransition();
        mainHandler.removeCallbacks(clockRunnable);
        if (calendarStage != null) {
            calendarStage.setVisibility(View.GONE);
        }
        if (calendarImageView != null) {
            calendarImageView.setImageDrawable(null);
            resetImageStage(calendarImageView);
        }
        if (calendarImageViewNext != null) {
            calendarImageViewNext.setImageDrawable(null);
            resetImageStage(calendarImageViewNext);
        }
        primaryCalendarStageActive = true;
    }

    private void layoutCalendarStage() {
        if (calendarStage == null || calendarImageView == null || calendarInfoPanel == null) {
            return;
        }
        int width = getContainerWidth(calendarStage);
        int height = getContainerHeight(calendarStage);
        boolean portrait = height > width;
        if (portrait) {
            int imageHeight = Math.round(height * 0.55f);
            int infoHeight = Math.max(1, height - imageHeight);
            FrameLayout.LayoutParams imageParams = new FrameLayout.LayoutParams(width, imageHeight);
            imageParams.leftMargin = 0;
            imageParams.topMargin = 0;
            calendarImageView.setLayoutParams(imageParams);
            if (calendarImageViewNext != null) calendarImageViewNext.setLayoutParams(new FrameLayout.LayoutParams(imageParams));
            FrameLayout.LayoutParams infoParams = new FrameLayout.LayoutParams(width, infoHeight);
            infoParams.leftMargin = 0;
            infoParams.topMargin = imageHeight;
            calendarInfoPanel.setLayoutParams(infoParams);
            int horizontalPadding = Math.max(dp(18), Math.round(width * 0.06f));
            int verticalPadding = Math.max(dp(8), Math.round(infoHeight * 0.05f));
            calendarInfoPanel.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding);
            setCalendarTextSizes(18f, 56f, 24f, 16f);
            return;
        }
        int imageWidth = Math.round(width * 0.67f);
        int infoWidth = Math.max(1, width - imageWidth);
        FrameLayout.LayoutParams imageParams = new FrameLayout.LayoutParams(imageWidth, height);
        imageParams.leftMargin = 0;
        imageParams.topMargin = 0;
        calendarImageView.setLayoutParams(imageParams);
        if (calendarImageViewNext != null) calendarImageViewNext.setLayoutParams(new FrameLayout.LayoutParams(imageParams));
        FrameLayout.LayoutParams infoParams = new FrameLayout.LayoutParams(infoWidth, height);
        infoParams.leftMargin = imageWidth;
        infoParams.topMargin = 0;
        calendarInfoPanel.setLayoutParams(infoParams);
        int horizontalPadding = Math.max(dp(12), Math.round(infoWidth * 0.08f));
        int verticalPadding = Math.max(dp(8), Math.round(height * 0.03f));
        calendarInfoPanel.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding);
        setCalendarTextSizes(24f, 96f, 36f, 20f);
    }

    private void setCalendarTextSizes(float weekdaySp, float daySp, float monthSp, float lunarSp) {
        if (calendarWeekdayText != null) calendarWeekdayText.setTextSize(weekdaySp);
        if (calendarDayText != null) calendarDayText.setTextSize(daySp);
        if (calendarMonthText != null) calendarMonthText.setTextSize(monthSp);
        if (calendarLunarText != null) calendarLunarText.setTextSize(lunarSp);
    }

    private void updateTimeOverlayVisibility() {
        boolean calendar = "CALENDAR".equals(resolveDisplayStyle(playbackEngine.getCurrentDisplayStyle(), playbackEngine.getCurrentTransitionStyle()));
        boolean show = playbackEngine.isCurrentShowTimeAndDate() && !calendar;
        if (timeOverlay != null) {
            timeOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (show || calendar) {
            updateClockViews();
            mainHandler.removeCallbacks(clockRunnable);
            mainHandler.postDelayed(clockRunnable, 1000L);
        } else {
            mainHandler.removeCallbacks(clockRunnable);
        }
    }

    private void updateClockViews() {
        Calendar calendar = Calendar.getInstance();
        Locale locale = Locale.getDefault();
        String time = new SimpleDateFormat("HH:mm", locale).format(calendar.getTime());
        String month = calendar.get(Calendar.YEAR) + "\u5e74" + (calendar.get(Calendar.MONTH) + 1) + "\u6708";
        String day = String.valueOf(calendar.get(Calendar.DAY_OF_MONTH));
        String weekday = formatCalendarWeekday(calendar);
        String lunar = LunarCalendar.formatLunar(calendar);
        String date = new SimpleDateFormat("yyyy/MM/dd", locale).format(calendar.getTime()) + " " + weekday;
        if (calendarMonthText != null) calendarMonthText.setText(month);
        if (calendarDayText != null) calendarDayText.setText(day);
        if (calendarWeekdayText != null) calendarWeekdayText.setText(weekday);
        if (calendarLunarText != null) calendarLunarText.setText(lunar);
        if (overlayTimeText != null) overlayTimeText.setText(time);
        if (overlayDateText != null) overlayDateText.setText(date);
    }

    private String formatCalendarWeekday(Calendar calendar) {
        switch (calendar.get(Calendar.DAY_OF_WEEK)) {
            case Calendar.MONDAY:
                return "星期一";
            case Calendar.TUESDAY:
                return "星期二";
            case Calendar.WEDNESDAY:
                return "星期三";
            case Calendar.THURSDAY:
                return "星期四";
            case Calendar.FRIDAY:
                return "星期五";
            case Calendar.SATURDAY:
                return "星期六";
            case Calendar.SUNDAY:
            default:
                return "星期日";
        }
    }

    private void showImageWithoutTransition(ImageView targetStage, ImageView oldStage) {
        cancelImageTransition();
        resetImageStage(targetStage);
        targetStage.setVisibility(View.VISIBLE);
        oldStage.setVisibility(View.GONE);
        resetImageStage(oldStage);
        primaryImageStageActive = targetStage == imageStage;
    }

    private ImageView getActiveImageStage() {
        return primaryImageStageActive ? imageStage : imageStageNext;
    }

    private ImageView getInactiveImageStage() {
        return primaryImageStageActive ? imageStageNext : imageStage;
    }

    private void hideImageStages() {
        cancelImageTransition();
        if (imageStage != null) {
            imageStage.setVisibility(View.GONE);
            resetImageStage(imageStage);
        }
        if (imageStageNext != null) {
            imageStageNext.setVisibility(View.GONE);
            resetImageStage(imageStageNext);
        }
    }

    private void hideAdvancedImageStage() {
        pendingAdvancedMediaIdentity = "";
        advancedNextReady = false;
        advancedPendingLoadCount = 0;
        advancedAnyLoadSuccess = false;
        bentoImageViews.clear();
        bentoNextSourceIndex = 0;
        bentoPreviousSlotIndex = -1;
        bentoPreviousSlotIndex2 = -1;
        frameWallNextSourceIndex = 0;
        frameWallPreviousSlotIndex = -1;
        carouselActiveSourceIndex = 0;
        advancedDisplayedCountInCycle = 0;
        advancedCycleMediaCount = 0;
        if (advancedImageStage != null) {
            advancedImageStage.setVisibility(View.GONE);
            advancedImageStage.removeAllViews();
        }
        if (advancedImageStageNext != null) {
            advancedImageStageNext.setVisibility(View.GONE);
            advancedImageStageNext.removeAllViews();
        }
    }

    private void renderAdvancedImageMedia(String displayStyle, String mediaIdentity) {
        hideImageStages();
        pendingAdvancedMediaIdentity = mediaIdentity;
        advancedNextReady = false;
        advancedReadyRetryCount = 0;
        advancedPendingLoadCount = 0;
        advancedAnyLoadSuccess = false;
        final FrameLayout nextStage = getInactiveAdvancedStage();
        nextStage.removeAllViews();
        nextStage.setPadding(0, 0, 0, 0);
        nextStage.setAlpha(0f);
        nextStage.setVisibility(View.INVISIBLE);
        List<DevicePullResponse.MediaItem> imageItems = getAdvancedImageItems();
        if (imageItems.isEmpty()) {
            playbackEngine.nextMedia();
            renderPlayback();
            return;
        }
        ensureAdvancedCycleStarted(imageItems.size());
        if ("CAROUSEL".equals(displayStyle)) {
            renderCarouselLayout(nextStage, imageItems);
        } else if ("FRAME_WALL".equals(displayStyle) || "FRAMEWALL".equals(displayStyle)) {
            renderFrameWallLayout(nextStage, imageItems);
        } else {
            renderBentoLayout(nextStage, imageItems);
        }
        preloadAdvancedImages(imageItems);
        waitForAdvancedStageReady(mediaIdentity, nextStage);
    }

    private void waitForAdvancedStageReady(final String mediaIdentity, final FrameLayout nextStage) {
        if (!mediaIdentity.equals(pendingAdvancedMediaIdentity)) {
            return;
        }
        if (advancedNextReady) {
            showPreparedAdvancedStage(nextStage);
            return;
        }
        if (advancedReadyRetryCount >= ADVANCED_READY_MAX_RETRIES) {
            if (getActiveAdvancedStage().getVisibility() == View.VISIBLE && getActiveAdvancedStage().getChildCount() > 0) {
                nextStage.removeAllViews();
                nextStage.setVisibility(View.GONE);
                playbackEngine.nextMedia();
                renderPlayback();
            } else {
                showPreparedAdvancedStage(nextStage);
            }
            return;
        }
        advancedReadyRetryCount += 1;
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                waitForAdvancedStageReady(mediaIdentity, nextStage);
            }
        }, ADVANCED_READY_RETRY_DELAY_MS);
    }

    private void showPreparedAdvancedStage(final FrameLayout nextStage) {
        final FrameLayout currentStage = getActiveAdvancedStage();
        nextStage.setVisibility(View.VISIBLE);
        nextStage.animate().alpha(1f).setDuration(IMAGE_TRANSITION_DURATION_MS).start();
        currentStage.animate().alpha(0f).setDuration(IMAGE_TRANSITION_DURATION_MS).withEndAction(new Runnable() {
            @Override
            public void run() {
                currentStage.setVisibility(View.GONE);
                currentStage.removeAllViews();
                currentStage.setAlpha(1f);
            }
        }).start();
        advancedPrimaryStageActive = nextStage == advancedImageStage;
        mainHandler.removeCallbacks(imageAdvanceRunnable);
        mainHandler.postDelayed(imageAdvanceRunnable, playbackEngine.getCurrentItemDurationSeconds() * 1000L);
    }

    private FrameLayout getActiveAdvancedStage() {
        return advancedPrimaryStageActive ? advancedImageStage : advancedImageStageNext;
    }

    private FrameLayout getInactiveAdvancedStage() {
        return advancedPrimaryStageActive ? advancedImageStageNext : advancedImageStage;
    }

    private void renderBentoLayout(FrameLayout container, List<DevicePullResponse.MediaItem> imageItems) {
        String variant = playbackEngine.getCurrentDisplayVariant();
        int gapDp = isDenseBentoVariant(variant) ? 2 : 3;
        int gap = dp(gapDp);
        int count = imageItems.size();
        bentoImageViews.clear();
        container.setPadding(0, 0, 0, 0);
        float[][] rawSlots = getBentoSlots(variant, isPortraitContainer(container));
        int maxCount = Math.min(rawSlots.length, ADVANCED_IMAGE_POOL_SIZE);
        int cw = getContainerWidth(container);
        int ch = getContainerHeight(container);
        float sx = cw > 0 ? (float) gap / cw : 0f;
        float sy = ch > 0 ? (float) gap / ch : 0f;
        for (int i = 0; i < maxCount; i += 1) {
            float[] s = rawSlots[i];
            float left = s[0] * (1f - 2f * sx) + sx;
            float top = s[1] * (1f - 2f * sy) + sy;
            float w = s[2] * (1f - 2f * sx);
            float h = s[3] * (1f - 2f * sy);
            addAdvancedImage(container, imageItems.get(i % count), left, top, w, h, gap, i == 0);
        }
        markAdvancedImagesDisplayed(Math.min(maxCount, count));
        bentoNextSourceIndex = maxCount % count;
    }

    private boolean isDenseBentoVariant(String variant) {
        String normalized = normalizeStyle(variant);
        return "BENTO_6".equals(normalized) || "BENTO_7".equals(normalized);
    }

    private float[][] getBentoSlots(String variant, boolean portrait) {
        float[][] slots = getBentoBaseSlots(variant);
        return portrait ? transposeBentoSlots(slots) : slots;
    }

    private float[][] getBentoBaseSlots(String variant) {
        String normalized = normalizeStyle(variant);
        if ("DEFAULT".equals(normalized) || normalized.isEmpty()) {
            normalized = "BENTO_5";
        }
        if ("BENTO_1".equals(normalized)) {
            return new float[][] {{0f, 0f, 1f / 6f, 0.25f}, {1f / 6f, 0f, 1f / 6f, 0.25f}, {2f / 6f, 0f, 2f / 6f, 0.25f}, {4f / 6f, 0.5f, 1f / 6f, 0.25f}, {5f / 6f, 0.5f, 1f / 6f, 0.25f}, {0f, 0.25f, 2f / 6f, 0.5f}, {2f / 6f, 0.25f, 2f / 6f, 0.25f}, {4f / 6f, 0f, 2f / 6f, 0.5f}, {4f / 6f, 0.75f, 2f / 6f, 0.25f}};
        }
        if ("BENTO_2".equals(normalized)) {
            return new float[][] {{0f, 0f, 0.25f, 2f / 3f}, {0f, 2f / 3f, 0.375f, 1f / 3f}, {0.25f, 0f, 0.375f, 1f / 3f}, {0.625f, 0f, 0.375f, 1f / 3f}, {0.75f, 1f / 3f, 0.25f, 2f / 3f}, {0.375f, 2f / 3f, 0.375f, 1f / 3f}, {0.25f, 1f / 3f, 0.25f, 1f / 3f}, {0.5f, 1f / 3f, 0.25f, 1f / 3f}};
        }
        if ("BENTO_3".equals(normalized)) {
            return new float[][] {{0f, 0f, 0.25f, 0.615f}, {0.25f, 0f, 0.25f, 0.385f}, {0.5f, 0f, 0.5f, 0.385f}, {0f, 0.615f, 0.25f, 0.385f}, {0.25f, 0.385f, 0.5f, 0.385f}, {0.75f, 0.385f, 0.25f, 0.385f}, {0.25f, 0.77f, 0.25f, 0.23f}, {0.5f, 0.77f, 0.25f, 0.23f}, {0.75f, 0.77f, 0.25f, 0.23f}};
        }
        if ("BENTO_4".equals(normalized)) {
            return new float[][] {{0f, 0f, 0.5f, 2f / 3f}, {0.5f, 0f, 0.25f, 2f / 3f}, {0.75f, 0f, 0.25f, 1f / 3f}, {0f, 2f / 3f, 0.25f, 1f / 3f}, {0.25f, 2f / 3f, 0.25f, 1f / 3f}, {0.5f, 2f / 3f, 0.25f, 1f / 3f}, {0.75f, 1f / 3f, 0.25f, 2f / 3f}};
        }
        if ("BENTO_6".equals(normalized)) {
            return new float[][] {{0f, 0f, 2f / 7f, 0.25f}, {2f / 7f, 0f, 1f / 7f, 0.325f}, {3f / 7f, 0f, 1f / 7f, 0.325f}, {4f / 7f, 0f, 1f / 7f, 0.325f}, {5f / 7f, 0f, 2f / 7f, 0.25f}, {0f, 0.25f, 1f / 7f, 0.25f}, {1f / 7f, 0.25f, 1f / 7f, 0.25f}, {0f, 0.5f, 1f / 7f, 0.25f}, {1f / 7f, 0.5f, 1f / 7f, 0.25f}, {0f, 0.75f, 2f / 7f, 0.25f}, {2f / 7f, 0.325f, 3f / 7f, 0.35f}, {2f / 7f, 0.675f, 1f / 7f, 0.325f}, {3f / 7f, 0.675f, 1f / 7f, 0.325f}, {4f / 7f, 0.675f, 1f / 7f, 0.325f}, {5f / 7f, 0.25f, 1f / 7f, 0.25f}, {6f / 7f, 0.25f, 1f / 7f, 0.25f}, {5f / 7f, 0.5f, 1f / 7f, 0.25f}, {6f / 7f, 0.5f, 1f / 7f, 0.25f}, {5f / 7f, 0.75f, 2f / 7f, 0.25f}};
        }
        if ("BENTO_7".equals(normalized)) {
            return new float[][] {{0f, 0f, 2f / 7f, 0.25f}, {2f / 7f, 0f, 1f / 7f, 0.325f}, {3f / 7f, 0f, 1f / 7f, 0.325f}, {4f / 7f, 0f, 1f / 7f, 0.325f}, {5f / 7f, 0f, 2f / 7f, 0.25f}, {0f, 0.25f, 1f / 7f, 0.25f}, {1f / 7f, 0.25f, 1f / 7f, 0.25f}, {0f, 0.5f, 1f / 7f, 0.25f}, {1f / 7f, 0.5f, 1f / 7f, 0.25f}, {0f, 0.75f, 2f / 7f, 0.25f}, {2f / 7f, 0.325f, 3f / 7f, 0.35f}, {2f / 7f, 0.675f, 1f / 7f, 0.325f}, {3f / 7f, 0.675f, 1f / 7f, 0.325f}, {4f / 7f, 0.675f, 1f / 7f, 0.325f}, {5f / 7f, 0.25f, 1f / 7f, 0.25f}, {6f / 7f, 0.25f, 1f / 7f, 0.175f}, {5f / 7f, 0.5f, 1f / 7f, 0.175f}, {6f / 7f, 0.425f, 1f / 7f, 0.25f}, {5f / 7f, 0.675f, 2f / 7f, 0.325f}};
        }
        return new float[][] {{0f, 0f, 0.75f, 2f / 3f}, {0.75f, 0f, 0.25f, 2f / 3f}, {0f, 2f / 3f, 0.25f, 1f / 3f}, {0.25f, 2f / 3f, 0.5f, 1f / 3f}, {0.75f, 2f / 3f, 0.25f, 1f / 3f}};
    }

    private float[][] transposeBentoSlots(float[][] slots) {
        float[][] transposed = new float[slots.length][4];
        for (int i = 0; i < slots.length; i += 1) {
            transposed[i][0] = slots[i][1];
            transposed[i][1] = slots[i][0];
            transposed[i][2] = slots[i][3];
            transposed[i][3] = slots[i][2];
        }
        return transposed;
    }

    private boolean isPortraitContainer(View container) {
        return getContainerHeight(container) > getContainerWidth(container);
    }

    private void addCenteredAdvancedImage(FrameLayout container, DevicePullResponse.MediaItem media, float widthRatio, float heightRatio, int margin, boolean marksReady) {
        float leftRatio = Math.max(0f, (1f - widthRatio) / 2f);
        float topRatio = Math.max(0f, (1f - heightRatio) / 2f);
        addAdvancedImage(container, media, leftRatio, topRatio, widthRatio, heightRatio, margin, marksReady);
    }

    private int resolveFrameWallSlotCount() {
        String variant = playbackEngine.getCurrentDisplayVariant();
        if (variant == null) {
            return 8;
        }
        String normalized = variant.trim().toUpperCase(Locale.getDefault()).replace('-', '_');
        if (normalized.startsWith("FRAME_WALL_")) {
            try {
                int count = Integer.parseInt(normalized.substring("FRAME_WALL_".length()));
                if (count == 2 || count == 4 || count == 6 || count == 8) {
                    return count;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return 8;
    }

    private void renderFrameWallLayout(FrameLayout container, List<DevicePullResponse.MediaItem> imageItems) {
        frameWallImageViews.clear();
        boolean portrait = isPortraitContainer(container);
        int totalSlots = resolveFrameWallSlotCount();
        int landscapeCols;
        int landscapeRows;
        if (totalSlots <= 2) {
            landscapeCols = 2;
            landscapeRows = 1;
        } else if (totalSlots <= 4) {
            landscapeCols = 2;
            landscapeRows = 2;
        } else if (totalSlots <= 6) {
            landscapeCols = 3;
            landscapeRows = 2;
        } else {
            landscapeCols = 4;
            landscapeRows = 2;
        }
        int cols = portrait ? landscapeRows : landscapeCols;
        int rows = portrait ? landscapeCols : landscapeRows;
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(cols);
        grid.setRowCount(rows);
        int gap = dp(3);
        grid.setPadding(gap, gap, gap, gap);
        container.addView(grid, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        int slotCount = cols * rows;
        if (!imageItems.isEmpty()) {
            frameWallNextSourceIndex = slotCount % imageItems.size();
        }
        int parentWidth = Math.max(1, getContainerWidth(container));
        int parentHeight = Math.max(1, getContainerHeight(container));
        int cellWidth = Math.max(1, (parentWidth - gap * (cols * 2 + 2)) / cols);
        int cellHeight = Math.max(1, (parentHeight - gap * (rows * 2 + 2)) / rows);
        for (int i = 0; i < slotCount; i += 1) {
            ImageView imageView = createAdvancedImageView();
            GridLayout.LayoutParams params = createFrameWallCellParams(i, cellWidth, cellHeight, cols);
            params.setMargins(gap, gap, gap, gap);
            grid.addView(imageView, params);
            frameWallImageViews.add(imageView);
            loadAdvancedImage(imageView, imageItems.get(i % imageItems.size()), i == 0);
        }
        markAdvancedImagesDisplayed(Math.min(slotCount, imageItems.size()));
    }

    private GridLayout.LayoutParams createFrameWallCellParams(int index, int fallbackWidth, int fallbackHeight, int cols) {
        int row = index / cols;
        int column = index % cols;
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            params.width = 0;
            params.height = 0;
            params.columnSpec = GridLayout.spec(column, 1f);
            params.rowSpec = GridLayout.spec(row, 1f);
        } else {
            params.width = fallbackWidth;
            params.height = fallbackHeight;
            params.columnSpec = GridLayout.spec(column);
            params.rowSpec = GridLayout.spec(row);
        }
        return params;
    }

    private void renderCarouselLayout(FrameLayout container, List<DevicePullResponse.MediaItem> imageItems) {
        int parentWidth = getContainerWidth(container);
        int parentHeight = getContainerHeight(container);
        boolean portrait = isPortraitContainer(container);
        int layoutCount = imageItems.size() >= 5 ? 5 : Math.min(3, imageItems.size());
        if (imageItems.size() < 3) {
            layoutCount = imageItems.size();
        }
        int center = layoutCount / 2;
        for (int i = 0; i < layoutCount; i += 1) {
            ImageView imageView = createAdvancedImageView();
            int sourceIndex = (carouselActiveSourceIndex + i - center) % imageItems.size();
            if (sourceIndex < 0) {
                sourceIndex += imageItems.size();
            }
            boolean primary = i == center;
            float offset = i - center;
            int width;
            int height;
            int leftMargin;
            int topMargin;
            if (portrait) {
                float heightRatio = primary ? 0.42f : 0.24f;
                float widthRatio = primary ? 0.78f : 0.58f;
                float spacingRatio = 0.19f;
                width = Math.max(1, Math.round(parentWidth * widthRatio));
                height = Math.max(1, Math.round(parentHeight * heightRatio));
                leftMargin = clampMargin(Math.round((parentWidth - width) / 2f), width, parentWidth);
                float centerRatio = 0.5f + offset * spacingRatio;
                topMargin = clampMargin(Math.round(parentHeight * centerRatio - height / 2f), height, parentHeight);
            } else {
                float widthRatio = primary ? 0.42f : 0.24f;
                float heightRatio = primary ? 0.78f : 0.58f;
                float spacingRatio = 0.19f;
                width = Math.max(1, Math.round(parentWidth * widthRatio));
                height = Math.max(1, Math.round(parentHeight * heightRatio));
                float centerRatio = 0.5f + offset * spacingRatio;
                leftMargin = clampMargin(Math.round(parentWidth * centerRatio - width / 2f), width, parentWidth);
                topMargin = clampMargin(Math.round((parentHeight - height) / 2f), height, parentHeight);
            }
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, height);
            params.leftMargin = leftMargin;
            params.topMargin = topMargin;
            imageView.setAlpha(primary ? 1f : 0.68f);
            imageView.setScaleX(primary ? 1f : 0.9f);
            imageView.setScaleY(primary ? 1f : 0.9f);
            ViewCompat.setElevation(imageView, primary ? dp(8) : dp(2));
            container.addView(imageView, params);
            loadAdvancedImage(imageView, imageItems.get(sourceIndex), primary);
        }
        if (advancedDisplayedCountInCycle == 0) {
            markAdvancedImagesDisplayed(Math.min(layoutCount, imageItems.size()));
        }
    }

    private void addAdvancedImage(FrameLayout container, DevicePullResponse.MediaItem media, float leftRatio, float topRatio, float widthRatio, float heightRatio, int margin, boolean marksReady) {
        ImageView imageView = createAdvancedImageView();
        int parentWidth = getContainerWidth(container);
        int parentHeight = getContainerHeight(container);
        int availableLeft = container.getPaddingLeft();
        int availableTop = container.getPaddingTop();
        int availableWidth = Math.max(1, parentWidth - container.getPaddingLeft() - container.getPaddingRight());
        int availableHeight = Math.max(1, parentHeight - container.getPaddingTop() - container.getPaddingBottom());
        int slotLeft = availableLeft + Math.round(availableWidth * leftRatio);
        int slotTop = availableTop + Math.round(availableHeight * topRatio);
        int slotRight = availableLeft + Math.round(availableWidth * (leftRatio + widthRatio));
        int slotBottom = availableTop + Math.round(availableHeight * (topRatio + heightRatio));
        slotRight = Math.min(availableLeft + availableWidth, Math.max(slotLeft + 1, slotRight));
        slotBottom = Math.min(availableTop + availableHeight, Math.max(slotTop + 1, slotBottom));

        int imageLeft = slotLeft + margin;
        int imageTop = slotTop + margin;
        int imageRight = Math.max(imageLeft + 1, slotRight - margin);
        int imageBottom = Math.max(imageTop + 1, slotBottom - margin);
        imageRight = Math.min(availableLeft + availableWidth, imageRight);
        imageBottom = Math.min(availableTop + availableHeight, imageBottom);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                Math.max(1, imageRight - imageLeft),
                Math.max(1, imageBottom - imageTop));
        params.leftMargin = imageLeft;
        params.topMargin = imageTop;
        container.addView(imageView, params);
        if ("BENTO".equals(resolveDisplayStyle(playbackEngine.getCurrentDisplayStyle(), playbackEngine.getCurrentTransitionStyle()))) {
            bentoImageViews.add(imageView);
        }
        imageView.setScaleX(0.96f);
        imageView.setScaleY(0.96f);
        imageView.animate().scaleX(1f).scaleY(1f).setDuration(IMAGE_TRANSITION_DURATION_MS).start();
        loadAdvancedImage(imageView, media, marksReady);
    }

    private int getContainerWidth(View container) {
        if (container != null && container.getWidth() > 0) {
            return container.getWidth();
        }
        return getScreenWidth();
    }

    private int getContainerHeight(View container) {
        if (container != null && container.getHeight() > 0) {
            return container.getHeight();
        }
        return getScreenHeight();
    }

    private int clampMargin(int margin, int size, int parentSize) {
        int maxMargin = Math.max(0, parentSize - size);
        if (margin < 0) {
            return 0;
        }
        if (margin > maxMargin) {
            return maxMargin;
        }
        return margin;
    }

    private void markAdvancedImagesDisplayed(int count) {
        if (advancedCycleMediaCount <= 0 || count <= 0) {
            return;
        }
        advancedDisplayedCountInCycle = Math.min(advancedCycleMediaCount, advancedDisplayedCountInCycle + count);
    }

    private void ensureAdvancedCycleStarted(int mediaCount) {
        if (mediaCount <= 0) {
            advancedCycleMediaCount = 0;
            advancedDisplayedCountInCycle = 0;
            return;
        }
        if (advancedCycleMediaCount != mediaCount || advancedDisplayedCountInCycle <= 0) {
            advancedCycleMediaCount = mediaCount;
            advancedDisplayedCountInCycle = 0;
        }
    }

    private boolean hasDisplayedCurrentAdvancedCycle() {
        return advancedCycleMediaCount > 0 && advancedDisplayedCountInCycle >= advancedCycleMediaCount;
    }

    private void advanceToNextDistributionAfterAdvancedCycle() {
        mainHandler.removeCallbacks(imageAdvanceRunnable);
        advancedDisplayedCountInCycle = 0;
        advancedCycleMediaCount = 0;
        playbackEngine.nextDistribution();
        renderPlayback();
    }

    private void loadAdvancedImage(ImageView imageView, DevicePullResponse.MediaItem media, final boolean marksReady) {
        advancedPendingLoadCount += 1;
        loadPlaybackImage(imageView, media, new AuthenticatedImageLoader.Callback() {
            @Override
            public void onSuccess() {
                advancedAnyLoadSuccess = true;
                markAdvancedImageLoaded();
            }

            @Override
            public void onFailure() {
                markAdvancedImageLoaded();
            }
        });
    }

    private void markAdvancedImageLoaded() {
        advancedPendingLoadCount = Math.max(0, advancedPendingLoadCount - 1);
        if (advancedPendingLoadCount == 0) {
            advancedNextReady = advancedAnyLoadSuccess;
        }
    }

    private ImageView createAdvancedImageView() {
        RoundedImageView imageView = new RoundedImageView(this);
        imageView.setCornerRadiusDp(8);
        imageView.setBackgroundColor(ContextCompat.getColor(this, R.color.ca_background));
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imageView.setAdjustViewBounds(false);
        return imageView;
    }

    private List<DevicePullResponse.MediaItem> getAdvancedImageItems() {
        List<DevicePullResponse.MediaItem> mediaList = playbackEngine.getCurrentMediaList();
        DevicePullResponse.MediaItem current = playbackEngine.getCurrentMedia();
        String currentIdentity = PlaybackEngine.resolveMediaIdentity(current);
        String displayStyle = resolveDisplayStyle(playbackEngine.getCurrentDisplayStyle(), playbackEngine.getCurrentTransitionStyle());
        String cacheKey = currentIdentity + "|" + displayStyle + "|" + mediaList.size();
        if (cacheKey.equals(cachedAdvancedImageItemsKey) && !cachedAdvancedImageItems.isEmpty()) {
            return cachedAdvancedImageItems;
        }
        List<DevicePullResponse.MediaItem> result = new ArrayList<DevicePullResponse.MediaItem>();
        int startIndex = 0;
        for (int i = 0; i < mediaList.size(); i += 1) {
            if (currentIdentity.equals(PlaybackEngine.resolveMediaIdentity(mediaList.get(i)))) {
                startIndex = i;
                break;
            }
        }
        int imageLimit = mediaList.size();
        for (int offset = 0; offset < mediaList.size() && result.size() < imageLimit; offset += 1) {
            DevicePullResponse.MediaItem candidate = mediaList.get((startIndex + offset) % mediaList.size());
            if (candidate != null && "IMAGE".equalsIgnoreCase(candidate.getMediaType())) {
                result.add(candidate);
            }
        }
        cachedAdvancedImageItems.clear();
        cachedAdvancedImageItems.addAll(result);
        cachedAdvancedImageItemsKey = cacheKey;
        return cachedAdvancedImageItems;
    }

    private int getAdaptiveAdvancedImagePoolSize() {
        String displayStyle = resolveDisplayStyle(playbackEngine.getCurrentDisplayStyle(), playbackEngine.getCurrentTransitionStyle());
        ActivityManager activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        int memoryClass = activityManager == null ? 128 : activityManager.getMemoryClass();
        int cores = Math.max(1, Runtime.getRuntime().availableProcessors());
        boolean lowMemoryNow = false;
        if (activityManager != null) {
            ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
            activityManager.getMemoryInfo(memoryInfo);
            lowMemoryNow = memoryInfo.lowMemory;
        }
        if (lowMemoryNow || memoryClass <= 128 || cores <= 2) {
            return "CAROUSEL".equals(displayStyle) ? 3 : 4;
        }
        if (memoryClass <= 256 || cores <= 4) {
            return "FRAME_WALL".equals(displayStyle) ? 6 : 5;
        }
        return "CAROUSEL".equals(displayStyle) ? 5 : ADVANCED_IMAGE_POOL_SIZE;
    }

    private void preloadAdvancedImages(List<DevicePullResponse.MediaItem> imageItems) {
        int limit = Math.min(imageItems.size(), ADVANCED_IMAGE_POOL_SIZE);
        for (int i = 0; i < limit; i += 1) {
            DevicePullResponse.MediaItem imageItem = imageItems.get(i);
            if (imageItem != null) {
                int[] targetSize = resolvePreferredImageDecodeTarget(imageStage);
                AuthenticatedImageLoader.preloadWithFocalPoint(imageStage, imageItem.getUrl(), sessionRepository,
                        targetSize[0], targetSize[1], null,
                        imageItem.getFocalPointX(), imageItem.getFocalPointY(),
                        imageItem.getFocalPointRegionWidth(), imageItem.getFocalPointRegionHeight());
            }
        }
    }

    private void prefetchUpcomingImages(List<DevicePullResponse.MediaItem> imageItems, int startIndex, int count) {
        for (int i = 0; i < count; i += 1) {
            DevicePullResponse.MediaItem item = imageItems.get((startIndex + i) % imageItems.size());
            if (item != null) {
                int[] targetSize = resolvePreferredImageDecodeTarget(imageStage);
                AuthenticatedImageLoader.preloadWithFocalPoint(imageStage, item.getUrl(), sessionRepository,
                        targetSize[0], targetSize[1], null,
                        item.getFocalPointX(), item.getFocalPointY(),
                        item.getFocalPointRegionWidth(), item.getFocalPointRegionHeight());
            }
        }
    }

    private String resolveDisplayStyle(String displayStyle, String transitionStyle) {
        String normalizedDisplay = normalizeStyle(displayStyle);
        if ("CALENDAR".equals(normalizedDisplay)) {
            return "CALENDAR";
        }
        if (isAdvancedDisplayStyle(normalizedDisplay)) {
            return normalizedDisplay;
        }
        String normalizedTransition = normalizeStyle(transitionStyle);
        return isAdvancedDisplayStyle(normalizedTransition) ? normalizedTransition : "SINGLE";
    }

    private boolean isAdvancedDisplayStyle(String style) {
        return ADVANCED_DISPLAY_STYLES.contains(normalizeStyle(style));
    }

    private String normalizeStyle(String style) {
        return style == null ? "" : style.trim().toUpperCase(Locale.US);
    }

    private int getScreenWidth() {
        if (playbackStageContainer != null && playbackStageContainer.getWidth() > 0) {
            return playbackStageContainer.getWidth();
        }
        return Math.max(1, pageRoot == null ? getResources().getDisplayMetrics().widthPixels : pageRoot.getWidth());
    }

    private int getScreenHeight() {
        if (playbackStageContainer != null && playbackStageContainer.getHeight() > 0) {
            return playbackStageContainer.getHeight();
        }
        return Math.max(1, pageRoot == null ? getResources().getDisplayMetrics().heightPixels : pageRoot.getHeight());
    }

    private void runImageTransition(final ImageView fromStage, final ImageView toStage, String transitionStyle) {
        cancelImageTransition();
        resetImageStage(fromStage);
        resetImageStage(toStage);
        fromStage.setVisibility(View.VISIBLE);
        toStage.setVisibility(View.VISIBLE);
        markTransitionStart();
        Animator animator;
        if ("SLIDE".equals(transitionStyle)) {
            animator = buildSlideTransition(fromStage, toStage);
        } else if ("CUBE".equals(transitionStyle)) {
            animator = buildCubeTransition(fromStage, toStage);
        } else if ("REVEAL".equals(transitionStyle)) {
            animator = buildRevealTransition(fromStage, toStage);
        } else if ("FLIP".equals(transitionStyle)) {
            animator = buildFlipTransition(fromStage, toStage);
        } else {
            animator = buildFadeTransition(fromStage, toStage);
        }
        imageTransitionAnimator = animator;
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                imageTransitionAnimator = null;
                evaluateTransitionPerformance();
                fromStage.setVisibility(View.GONE);
                resetImageStage(fromStage);
                resetImageStage(toStage);
                toStage.setVisibility(View.VISIBLE);
                primaryImageStageActive = toStage == imageStage;
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                imageTransitionAnimator = null;
            }
        });
        animator.start();
    }

    private Animator buildFadeTransition(ImageView fromStage, ImageView toStage) {
        toStage.setAlpha(0f);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(
                ObjectAnimator.ofFloat(fromStage, View.ALPHA, 1f, 0f),
                ObjectAnimator.ofFloat(toStage, View.ALPHA, 0f, 1f));
        configureImageAnimator(set);
        return set;
    }

    private Animator buildSlideTransition(ImageView fromStage, ImageView toStage) {
        float width = Math.max(1, fromStage.getWidth());
        toStage.setTranslationX(width);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(
                ObjectAnimator.ofFloat(fromStage, View.TRANSLATION_X, 0f, -width * 0.25f),
                ObjectAnimator.ofFloat(fromStage, View.ALPHA, 1f, 0f),
                ObjectAnimator.ofFloat(toStage, View.TRANSLATION_X, width, 0f),
                ObjectAnimator.ofFloat(toStage, View.ALPHA, 0.4f, 1f));
        configureImageAnimator(set);
        return set;
    }

    private Animator buildCubeTransition(ImageView fromStage, ImageView toStage) {
        float width = Math.max(1, fromStage.getWidth());
        float cameraDistance = getResources().getDisplayMetrics().density * 8000f;
        fromStage.setCameraDistance(cameraDistance);
        toStage.setCameraDistance(cameraDistance);
        fromStage.setPivotX(0f);
        fromStage.setPivotY(fromStage.getHeight() / 2f);
        toStage.setPivotX(width);
        toStage.setPivotY(toStage.getHeight() / 2f);
        toStage.setRotationY(90f);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(
                ObjectAnimator.ofFloat(fromStage, View.ROTATION_Y, 0f, -90f),
                ObjectAnimator.ofFloat(fromStage, View.ALPHA, 1f, 0.2f),
                ObjectAnimator.ofFloat(toStage, View.ROTATION_Y, 90f, 0f),
                ObjectAnimator.ofFloat(toStage, View.ALPHA, 0.2f, 1f));
        configureImageAnimator(set);
        return set;
    }

    private Animator buildRevealTransition(final ImageView fromStage, final ImageView toStage) {
        int width = Math.max(1, toStage.getWidth());
        int height = Math.max(1, toStage.getHeight());
        toStage.setClipBounds(new Rect(0, 0, 0, height));
        ValueAnimator reveal = ValueAnimator.ofObject(new RectEvaluator(), new Rect(0, 0, 0, height), new Rect(0, 0, width, height));
        reveal.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                toStage.setClipBounds((Rect) animation.getAnimatedValue());
            }
        });
        AnimatorSet set = new AnimatorSet();
        set.playTogether(reveal, ObjectAnimator.ofFloat(fromStage, View.ALPHA, 1f, 0.35f));
        configureImageAnimator(set);
        return set;
    }

    private Animator buildFlipTransition(final ImageView fromStage, final ImageView toStage) {
        float cameraDistance = getResources().getDisplayMetrics().density * 8000f;
        fromStage.setCameraDistance(cameraDistance);
        toStage.setCameraDistance(cameraDistance);
        toStage.setRotationY(-90f);
        AnimatorSet firstHalf = new AnimatorSet();
        firstHalf.playTogether(
                ObjectAnimator.ofFloat(fromStage, View.ROTATION_Y, 0f, 90f),
                ObjectAnimator.ofFloat(fromStage, View.ALPHA, 1f, 0.2f));
        firstHalf.setDuration(IMAGE_TRANSITION_DURATION_MS / 2);
        firstHalf.setInterpolator(new AccelerateDecelerateInterpolator());
        firstHalf.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                fromStage.setVisibility(View.GONE);
                toStage.setVisibility(View.VISIBLE);
            }
        });
        AnimatorSet secondHalf = new AnimatorSet();
        secondHalf.playTogether(
                ObjectAnimator.ofFloat(toStage, View.ROTATION_Y, -90f, 0f),
                ObjectAnimator.ofFloat(toStage, View.ALPHA, 0.2f, 1f));
        secondHalf.setDuration(IMAGE_TRANSITION_DURATION_MS / 2);
        secondHalf.setInterpolator(new AccelerateDecelerateInterpolator());
        AnimatorSet full = new AnimatorSet();
        full.playSequentially(firstHalf, secondHalf);
        return full;
    }

    private void configureImageAnimator(Animator animator) {
        animator.setDuration(IMAGE_TRANSITION_DURATION_MS);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
    }

    private void cancelImageTransition() {
        if (imageTransitionAnimator != null) {
            imageTransitionAnimator.cancel();
            imageTransitionAnimator = null;
        }
    }

    private void resetImageStage(ImageView stage) {
        if (stage == null) {
            return;
        }
        stage.setAlpha(1f);
        stage.setTranslationX(0f);
        stage.setTranslationY(0f);
        stage.setScaleX(1f);
        stage.setScaleY(1f);
        stage.setRotation(0f);
        stage.setRotationX(0f);
        stage.setRotationY(0f);
        stage.setPivotX(stage.getWidth() / 2f);
        stage.setPivotY(stage.getHeight() / 2f);
        stage.setClipBounds(null);
    }

    private String resolveImageTransitionStyle(String transitionStyle, String mediaIdentity) {
        if (transitionStyle == null || transitionStyle.trim().isEmpty() || "NONE".equals(transitionStyle)) {
            return "NONE";
        }
        if ("RANDOM".equals(transitionStyle)) {
            int index = Math.abs(mediaIdentity == null ? 0 : mediaIdentity.hashCode()) % RANDOM_IMAGE_TRANSITIONS.length;
            return RANDOM_IMAGE_TRANSITIONS[index];
        }
        return transitionStyle;
    }

    private void preloadNextImage() {
        DevicePullResponse.MediaItem nextMedia = playbackEngine.peekNextMedia();
        if (nextMedia == null || !"IMAGE".equalsIgnoreCase(nextMedia.getMediaType())) {
            return;
        }
        final String nextMediaIdentity = PlaybackEngine.resolveMediaIdentity(nextMedia);
        if (preloadedImageIdentities.contains(nextMediaIdentity)) {
            return;
        }
        int[] targetSize = resolvePreferredImageDecodeTarget(imageStage);
        AuthenticatedImageLoader.preloadWithFocalPoint(imageStage, nextMedia.getUrl(), sessionRepository,
                targetSize[0], targetSize[1], new AuthenticatedImageLoader.Callback() {
            @Override
            public void onSuccess() {
                preloadedImageIdentities.add(nextMediaIdentity);
            }

            @Override
            public void onFailure() {
                preloadedImageIdentities.remove(nextMediaIdentity);
            }
        }, nextMedia.getFocalPointX(), nextMedia.getFocalPointY(),
                nextMedia.getFocalPointRegionWidth(), nextMedia.getFocalPointRegionHeight());
    }

    private void advanceImageWhenReady() {
        String displayStyle = resolveDisplayStyle(playbackEngine.getCurrentDisplayStyle(), playbackEngine.getCurrentTransitionStyle());
        DevicePullResponse.MediaItem currentMedia = playbackEngine.getCurrentMedia();
        if ("BENTO".equals(displayStyle) && currentMedia != null && "IMAGE".equalsIgnoreCase(currentMedia.getMediaType())) {
            if (hasDisplayedCurrentAdvancedCycle()) {
                advanceToNextDistributionAfterAdvancedCycle();
                return;
            }
            imageAdvanceRetryCount = 0;
            swapOneBentoImage();
            mainHandler.removeCallbacks(imageAdvanceRunnable);
            mainHandler.postDelayed(imageAdvanceRunnable, playbackEngine.getCurrentItemDurationSeconds() * 1000L);
            return;
        }
        if (("FRAME_WALL".equals(displayStyle) || "FRAMEWALL".equals(displayStyle))
                && currentMedia != null && "IMAGE".equalsIgnoreCase(currentMedia.getMediaType())) {
            if (hasDisplayedCurrentAdvancedCycle()) {
                advanceToNextDistributionAfterAdvancedCycle();
                return;
            }
            imageAdvanceRetryCount = 0;
            swapFrameWallImages();
            mainHandler.removeCallbacks(imageAdvanceRunnable);
            mainHandler.postDelayed(imageAdvanceRunnable, playbackEngine.getCurrentItemDurationSeconds() * 1000L);
            return;
        }
        if ("CAROUSEL".equals(displayStyle) && currentMedia != null && "IMAGE".equalsIgnoreCase(currentMedia.getMediaType())) {
            if (hasDisplayedCurrentAdvancedCycle()) {
                advanceToNextDistributionAfterAdvancedCycle();
                return;
            }
            imageAdvanceRetryCount = 0;
            carouselActiveSourceIndex += 1;
            markAdvancedImagesDisplayed(1);
            renderAdvancedImageMedia(displayStyle, PlaybackEngine.resolveMediaIdentity(currentMedia));
            return;
        }
        DevicePullResponse.MediaItem nextMedia = playbackEngine.peekNextMedia();
        if (nextMedia != null && "IMAGE".equalsIgnoreCase(nextMedia.getMediaType())) {
            String nextMediaIdentity = PlaybackEngine.resolveMediaIdentity(nextMedia);
            if (!preloadedImageIdentities.contains(nextMediaIdentity)
                    && imageAdvanceRetryCount < IMAGE_ADVANCE_MAX_RETRY_COUNT) {
                imageAdvanceRetryCount += 1;
                preloadNextImage();
                mainHandler.postDelayed(imageAdvanceRunnable, IMAGE_ADVANCE_RETRY_DELAY_MS);
                return;
            }
        }
        imageAdvanceRetryCount = 0;
        playbackEngine.nextMedia();
        renderPlayback();
    }

    private void swapOneBentoImage() {
        List<DevicePullResponse.MediaItem> imageItems = getAdvancedImageItems();
        if (imageItems.isEmpty() || bentoImageViews.isEmpty()) {
            return;
        }
        if (bentoImageViews.get(0).getParent() == null) {
            bentoImageViews.clear();
            return;
        }
        int slotIndex = pickNextBentoSlot(bentoImageViews.size());
        final ImageView imageView = bentoImageViews.get(slotIndex);
        final DevicePullResponse.MediaItem nextMedia = imageItems.get(bentoNextSourceIndex % imageItems.size());
        bentoNextSourceIndex = (bentoNextSourceIndex + 1) % imageItems.size();
        markAdvancedImagesDisplayed(1);
        prefetchUpcomingImages(imageItems, bentoNextSourceIndex, Math.min(8, imageItems.size()));
        animateReplaceAdvancedImage(imageView, nextMedia);
    }

    private void swapFrameWallImages() {
        List<DevicePullResponse.MediaItem> imageItems = getAdvancedImageItems();
        if (imageItems.isEmpty() || frameWallImageViews.isEmpty()) {
            return;
        }
        prefetchUpcomingImages(imageItems, frameWallNextSourceIndex, Math.min(3, imageItems.size()));
        int replaceCount = Math.max(1, frameWallImageViews.size() / 10 + 1);
        for (int i = 0; i < replaceCount; i += 1) {
            final int delay = i * 800;
            mainHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (isFinishing() || frameWallImageViews.isEmpty()) return;
                    int slotIndex = pickNextFrameWallSlot(frameWallImageViews.size());
                    if (slotIndex >= frameWallImageViews.size()) return;
                    final ImageView imageView = frameWallImageViews.get(slotIndex);
                    final DevicePullResponse.MediaItem nextMedia = imageItems.get(frameWallNextSourceIndex % imageItems.size());
                    frameWallNextSourceIndex = (frameWallNextSourceIndex + 1) % imageItems.size();
                    markAdvancedImagesDisplayed(1);
                    animateReplaceAdvancedImage(imageView, nextMedia);
                }
            }, delay);
        }
    }

    private void animateReplaceAdvancedImage(final ImageView imageView, final DevicePullResponse.MediaItem nextMedia) {
        imageView.animate().cancel();
        imageView.setRotationX(0f);
        imageView.setRotationY(0f);
        imageView.setAlpha(1f);
        String url = nextMedia != null ? nextMedia.getUrl() : null;
        if (url == null || url.trim().isEmpty()) {
            return;
        }
        int[] targetSize = resolvePreferredImageDecodeTarget(imageView);
        AuthenticatedImageLoader.preload(imageView, url, sessionRepository, targetSize[0], targetSize[1], new AuthenticatedImageLoader.Callback() {
            @Override
            public void onSuccess() {
                if (isFinishing()) return;
                runFlipAnimation(imageView, nextMedia);
            }

            @Override
            public void onFailure() {
                if (isFinishing()) return;
                runFlipAnimation(imageView, nextMedia);
            }
        });
    }

    private void runFlipAnimation(final ImageView imageView, final DevicePullResponse.MediaItem nextMedia) {
        final boolean flipX = Math.random() > 0.5d;
        if (flipX) {
            imageView.animate().rotationX(90f).alpha(0.35f).setDuration(IMAGE_TRANSITION_DURATION_MS / 2).withEndAction(new Runnable() {
                @Override
                public void run() {
                    if (isFinishing()) return;
                    loadPlaybackImage(imageView, nextMedia, new AuthenticatedImageLoader.Callback() {
                        @Override
                        public void onSuccess() {
                            if (isFinishing()) return;
                            imageView.setRotationX(-90f);
                            imageView.setAlpha(0.35f);
                            imageView.animate().rotationX(0f).alpha(1f).setDuration(IMAGE_TRANSITION_DURATION_MS / 2).start();
                        }

                        @Override
                        public void onFailure() {
                            if (isFinishing()) return;
                            imageView.animate().rotationX(0f).alpha(1f).setDuration(IMAGE_TRANSITION_DURATION_MS / 2).start();
                        }
                    });
                }
            }).start();
            return;
        }
        imageView.animate().rotationY(90f).alpha(0.35f).setDuration(IMAGE_TRANSITION_DURATION_MS / 2).withEndAction(new Runnable() {
            @Override
            public void run() {
                if (isFinishing()) return;
                loadPlaybackImage(imageView, nextMedia, new AuthenticatedImageLoader.Callback() {
                    @Override
                    public void onSuccess() {
                        if (isFinishing()) return;
                        imageView.setRotationY(-90f);
                        imageView.setAlpha(0.35f);
                        imageView.animate().rotationY(0f).alpha(1f).setDuration(IMAGE_TRANSITION_DURATION_MS / 2).start();
                    }

                    @Override
                    public void onFailure() {
                        if (isFinishing()) return;
                        imageView.animate().rotationY(0f).alpha(1f).setDuration(IMAGE_TRANSITION_DURATION_MS / 2).start();
                    }
                });
            }
        }).start();
    }

    private int pickNextFrameWallSlot(int slotCount) {
        if (slotCount <= 1) {
            return 0;
        }
        int next = (int) Math.floor(Math.random() * slotCount);
        if (next == frameWallPreviousSlotIndex) {
            next = (next + 1) % slotCount;
        }
        frameWallPreviousSlotIndex = next;
        return next;
    }

    private int pickNextBentoSlot(int slotCount) {
        if (slotCount <= 2) {
            return 0;
        }
        int next = (int) Math.floor(Math.random() * slotCount);
        if (next == bentoPreviousSlotIndex || next == bentoPreviousSlotIndex2) {
            next = (next + 1) % slotCount;
            if (next == bentoPreviousSlotIndex || next == bentoPreviousSlotIndex2) {
                next = (next + 1) % slotCount;
            }
        }
        bentoPreviousSlotIndex2 = bentoPreviousSlotIndex;
        bentoPreviousSlotIndex = next;
        return next;
    }

    private void renderBgm() {
        if (bgmPlayer == null) return;
        String bgmUrl = playbackEngine.getCurrentBgmUrl();
        DevicePullResponse.MediaItem currentMedia = playbackEngine.getCurrentMedia();
        if (bgmUrl == null || bgmUrl.trim().isEmpty()) {
            lastRenderedBgmIdentity = "";
            bgmPlayer.stop();
            return;
        }

        float volume = Math.max(0f, Math.min(playbackEngine.getCurrentBgmVolume() / 100f, 1f));
        if (playbackMuted || (currentMedia != null && "VIDEO".equalsIgnoreCase(currentMedia.getMediaType()))) {
            bgmPlayer.setVolume(0f);
        } else {
            bgmPlayer.setVolume(volume);
        }

        String currentBgmIdentity = PlaybackEngine.resolveBgmIdentity(playbackEngine.getCurrentBgm());
        if (currentBgmIdentity == null || currentBgmIdentity.isEmpty()) {
            currentBgmIdentity = bgmUrl;
        }
        if (!currentBgmIdentity.equals(lastRenderedBgmIdentity)) {
            lastRenderedBgmIdentity = currentBgmIdentity;
            MediaItem bgmItem = new MediaItem.Builder().setUri(Uri.parse(bgmUrl)).build();
            bgmPlayer.setMediaItem(bgmItem, true);
            bgmPlayer.prepare();
        }
        if (!bgmPlayer.isPlaying()) {
            bgmPlayer.play();
        }
    }

    private void syncPlaybackSelectionState(DevicePullResponse response) {
        Set<String> availableDistributionIds = new HashSet<String>();
        if (response != null && response.getDistributions() != null) {
            for (DevicePullResponse.DistributionItem distribution : response.getDistributions()) {
                availableDistributionIds.add(String.valueOf(distribution.getId()));
            }
        }
        disabledDistributionIds.retainAll(availableDistributionIds);
        persistPlaybackSelection();
        playbackEngine.setDisabledPlayback(disabledDistributionIds, new HashSet<String>());
        rebuildPlaybackSelectionMenu(response);
    }

    private void rebuildPlaybackSelectionMenu(DevicePullResponse response) {
        playbackSelectionContainer.removeAllViews();
        if (response == null || response.getDistributions() == null || response.getDistributions().isEmpty()) {
            showPlaybackSelectionMessage(PLAYBACK_SELECTION_EMPTY);
            return;
        }

        int enabledGroupCount = 0;
        int totalGroupCount = 0;
        for (DevicePullResponse.DistributionItem distribution : response.getDistributions()) {
            if (distribution == null || distribution.getMediaList() == null || distribution.getMediaList().isEmpty()) {
                continue;
            }
            totalGroupCount += 1;
            if (!disabledDistributionIds.contains(String.valueOf(distribution.getId()))) {
                enabledGroupCount += 1;
            }
            playbackSelectionContainer.addView(createDistributionSelectionView(distribution));
        }

        if (totalGroupCount == 0) {
            showPlaybackSelectionMessage(PLAYBACK_SELECTION_EMPTY);
            return;
        }
        if (enabledGroupCount == 0) {
            playbackSelectionSummaryText.setText(PLAYBACK_SELECTION_ALL_DISABLED);
            rebuildDrawerFocusOrder();
            return;
        }
        playbackSelectionSummaryText.setText("\u5df2\u542f\u7528 " + enabledGroupCount + " / " + totalGroupCount + " \u4e2a\u5206\u7ec4");
        rebuildDrawerFocusOrder();
    }

    private View createDistributionSelectionView(final DevicePullResponse.DistributionItem distribution) {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(0, 0, 0, 0);

        LinearLayout.LayoutParams sectionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        sectionParams.bottomMargin = dp(10);
        section.setLayoutParams(sectionParams);

        final String distributionId = String.valueOf(distribution.getId());
        final CheckBox distributionCheckBox = new CheckBox(this);
        distributionCheckBox.setId(View.generateViewId());
        distributionCheckBox.setText(buildDistributionLabel(distribution));
        applyDrawerOptionStyle(distributionCheckBox);
        distributionCheckBox.setFocusable(true);
        distributionCheckBox.setFocusableInTouchMode(false);
        distributionCheckBox.setChecked(!disabledDistributionIds.contains(distributionId));
        section.addView(distributionCheckBox);

        distributionCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                updateDistributionSelection(distributionId, isChecked);
            }
        });
        return section;
    }

    private void updateDistributionSelection(String distributionId, boolean enabled) {
        if (enabled) {
            disabledDistributionIds.remove(distributionId);
        } else {
            disabledDistributionIds.add(distributionId);
        }
        persistPlaybackSelection();
        playbackEngine.setDisabledPlayback(disabledDistributionIds, new HashSet<String>());
        rebuildPlaybackSelectionMenu(lastPullResponse);
        renderPlayback();
    }

    private void persistPlaybackSelection() {
        sessionRepository.saveDisabledDistributionIds(disabledDistributionIds);
        sessionRepository.saveDisabledMediaIdentities(new HashSet<String>());
    }

    private void rebuildRotationOptions() {
        final String[] modes = new String[] {
                DeviceSessionRepository.PLAYBACK_ROTATION_AUTO,
                DeviceSessionRepository.PLAYBACK_ROTATION_0,
                DeviceSessionRepository.PLAYBACK_ROTATION_90,
                DeviceSessionRepository.PLAYBACK_ROTATION_180,
                DeviceSessionRepository.PLAYBACK_ROTATION_270
        };
        String[] labels = new String[] {ROTATION_AUTO_LABEL, ROTATION_0_LABEL, ROTATION_90_LABEL, ROTATION_180_LABEL, ROTATION_270_LABEL};
        bindSpinner(rotationSpinner, labels, indexOf(modes, playbackRotationMode), new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!bindingSpinners && position >= 0 && position < modes.length) {
                    updatePlaybackRotationMode(modes[position]);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        updateRotationSummary();
        rebuildDrawerFocusOrder();
    }

    private void applyDrawerOptionStyle(CheckBox option) {
        option.setBackgroundResource(R.drawable.bg_menu_option);
        option.setTextColor(ContextCompat.getColorStateList(this, R.color.menu_option_text));
        option.setPadding(dp(12), dp(10), dp(12), dp(10));
        option.setMinHeight(dp(48));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = dp(8);
        option.setLayoutParams(params);
    }

    private void applyDrawerButtonStyle(Button button) {
        button.setBackgroundResource(R.drawable.bg_menu_option);
        button.setTextColor(ContextCompat.getColor(this, R.color.ca_text_primary));
        button.setPadding(dp(12), dp(10), dp(12), dp(10));
        button.setMinHeight(dp(48));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = dp(8);
        button.setLayoutParams(params);
    }

    private void updateHomeLauncherUI(boolean isHomeLauncher) {
        homeLauncherToggleCheckBox.setText(HOME_LAUNCHER_LABEL);
        launchOriginalHomeButton.setVisibility(isHomeLauncher ? View.VISIBLE : View.GONE);
    }

    private boolean setHomeLauncherEnabled(boolean enabled) {
        try {
            String command = enabled
                    ? "pm disable " + ORIGINAL_HOME_PACKAGE
                    : "pm enable " + ORIGINAL_HOME_PACKAGE;
            Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", command});
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            Log.w(TAG, "setHomeLauncherEnabled failed", e);
            return false;
        }
    }

    private void bindSpinner(Spinner spinner, String[] labels, int selectedIndex, AdapterView.OnItemSelectedListener listener) {
        if (spinner == null) {
            return;
        }
        bindingSpinners = true;
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setFocusable(true);
        spinner.setFocusableInTouchMode(false);
        spinner.setBackgroundResource(R.drawable.bg_menu_option);
        spinner.setPadding(dp(12), dp(8), dp(12), dp(8));
        spinner.setMinimumHeight(dp(48));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = dp(8);
        spinner.setLayoutParams(params);
        spinner.setSelection(Math.max(0, selectedIndex), false);
        spinner.setOnItemSelectedListener(listener);
        bindingSpinners = false;
    }

    private int indexOf(String[] values, String value) {
        for (int i = 0; i < values.length; i += 1) {
            if (values[i].equals(value)) {
                return i;
            }
        }
        return 0;
    }

    private int indexOf(int[] values, int value) {
        for (int i = 0; i < values.length; i += 1) {
            if (values[i] == value) {
                return i;
            }
        }
        return 0;
    }

    private void updatePlaybackRotationMode(String mode) {
        if (mode == null || mode.equals(playbackRotationMode)) {
            return;
        }
        playbackRotationMode = mode;
        sessionRepository.savePlaybackRotationMode(mode);
        rebuildRotationOptions();
        applyPlaybackRotation();
    }

    private void updatePlaybackMuted(boolean muted) {
        if (playbackMuted == muted) {
            updateMuteSummary();
            return;
        }
        playbackMuted = muted;
        sessionRepository.savePlaybackMuted(muted);
        updateMuteSummary();
        applyPlaybackMutedState();
    }

    private void startBrightnessCheck() {
        mainHandler.removeCallbacks(brightnessCheckRunnable);
        applyBrightness();
        mainHandler.postDelayed(brightnessCheckRunnable, BRIGHTNESS_CHECK_INTERVAL_MS);
    }

    private void applyBrightness() {
        boolean enabled = sessionRepository.isBrightnessScheduleEnabled();
        if (!enabled) {
            setScreenBrightness(1.0f);
            return;
        }
        int startHour = sessionRepository.getBrightnessStartHour();
        int endHour = sessionRepository.getBrightnessEndHour();
        int currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        boolean inBrightPeriod;
        if (startHour <= endHour) {
            inBrightPeriod = currentHour >= startHour && currentHour < endHour;
        } else {
            inBrightPeriod = currentHour >= startHour || currentHour < endHour;
        }
        if (inBrightPeriod) {
            setScreenBrightness(1.0f);
        } else {
            int dimLevel = sessionRepository.getBrightnessDimLevel();
            setScreenBrightness(dimLevel / 100f);
        }
    }

    private void setScreenBrightness(float brightness) {
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        if (Math.abs(lp.screenBrightness - brightness) < 0.01f) {
            return;
        }
        lp.screenBrightness = brightness;
        getWindow().setAttributes(lp);
    }

    private void rebuildBrightnessHourOptions(Spinner spinner, int selectedHour) {
        bindingSpinners = true;
        String[] hours = new String[24];
        for (int i = 0; i < 24; i++) {
            hours[i] = String.format(Locale.getDefault(), "%02d:00", i);
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, hours);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(selectedHour);
        bindingSpinners = false;
    }

    private void rebuildBrightnessDimOptions() {
        bindingSpinners = true;
        String[] labels = new String[] {"5%", "10%", "15%", "20%", "30%", "50%"};
        int[] levels = new int[] {5, 10, 15, 20, 30, 50};
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        brightnessDimSpinner.setAdapter(adapter);
        int current = sessionRepository.getBrightnessDimLevel();
        int selectedIndex = 2;
        for (int i = 0; i < levels.length; i++) {
            if (levels[i] == current) {
                selectedIndex = i;
                break;
            }
        }
        brightnessDimSpinner.setSelection(selectedIndex);
        bindingSpinners = false;
    }

    private void updateBrightnessSummary() {
        if (sessionRepository.isBrightnessScheduleEnabled()) {
            int start = sessionRepository.getBrightnessStartHour();
            int end = sessionRepository.getBrightnessEndHour();
            int dim = sessionRepository.getBrightnessDimLevel();
            brightnessSummaryText.setText(String.format(Locale.getDefault(),
                    "%s %02d:00-%02d:00, \u964d\u81f3%d%%",
                    BRIGHTNESS_SUMMARY_ENABLED, start, end, dim));
        } else {
            brightnessSummaryText.setText(BRIGHTNESS_SUMMARY_DISABLED);
        }
    }

    private void updateBrightnessSpinnerFocusable(boolean enabled) {
        brightnessStartSpinner.setFocusable(enabled);
        brightnessEndSpinner.setFocusable(enabled);
        brightnessDimSpinner.setFocusable(enabled);
        if (!enabled) {
            brightnessStartSpinner.setFocusableInTouchMode(false);
            brightnessEndSpinner.setFocusableInTouchMode(false);
            brightnessDimSpinner.setFocusableInTouchMode(false);
        }
        rebuildDrawerFocusOrder();
    }


    private void updateMuteSummary() {
        muteSummaryText.setText(playbackMuted ? MUTE_ENABLED_LABEL : MUTE_DISABLED_LABEL);
    }


    private void updateDisplayModeSummary() {
        if (displayModeSummaryText == null || sessionRepository == null) {
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            displayModeSummaryText.setText("\u5f53\u524d\u7cfb\u7edf\u7248\u672c\u4e0d\u652f\u6301\u5207\u6362\u663e\u793a\u6a21\u5f0f");
            return;
        }
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        Display.Mode currentMode = getCurrentDisplayMode();
        Display.Mode bestMode = findLargestDisplayMode();
        String text = sessionRepository.isPreferNativeDisplayModeEnabled()
                ? "\u5df2\u542f\u7528\uff0c\u5c06\u8bf7\u6c42\u6700\u9ad8\u5206\u8fa8\u7387\u6a21\u5f0f"
                : DISPLAY_MODE_SUMMARY_DISABLED;
        text += " / \u5e94\u7528\u7a97\u53e3 " + metrics.widthPixels + " x " + metrics.heightPixels;
        if (currentMode != null) {
            text += " / \u5f53\u524d\u6a21\u5f0f " + formatDisplayMode(currentMode);
        }
        if (bestMode != null) {
            text += " / \u6700\u9ad8\u6a21\u5f0f " + formatDisplayMode(bestMode);
        }
        displayModeSummaryText.setText(text);
    }

    private String formatDisplayMode(Display.Mode mode) {
        if (mode == null) {
            return "-";
        }
        return mode.getPhysicalWidth() + " x " + mode.getPhysicalHeight() + " @ " + Math.round(mode.getRefreshRate()) + "Hz";
    }

    private Display.Mode getCurrentDisplayMode() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return null;
        }
        Display display = getWindowManager().getDefaultDisplay();
        return display == null ? null : display.getMode();
    }


    private void applyPlaybackMutedState() {
        if (contentPlayer != null) {
            contentPlayer.setVolume(playbackMuted ? 0f : 1f);
        }
        if (bgmPlayer != null) {
            renderBgm();
        }
    }

    private void updateRotationSummary() {
        if (DeviceSessionRepository.PLAYBACK_ROTATION_AUTO.equals(playbackRotationMode)) {
            rotationSummaryText.setText("\u8ddf\u968f\u8bbe\u5907\u65b9\u5411");
            return;
        }
        rotationSummaryText.setText("\u5c06\u6574\u4e2a\u64ad\u653e\u754c\u9762\u65cb\u8f6c " + playbackRotationMode + " \u5ea6");
    }

    private void applyPlaybackRotation() {
        if (rotationController != null) {
            rotationController.apply(playbackRotationMode);
        }
        refreshRotatedLayout();
    }

    private void refreshRotatedLayout() {
        if (pageRoot == null) {
            return;
        }
        pageRoot.post(new Runnable() {
            @Override
            public void run() {
                drawerLayout.requestLayout();
                if (menuDrawer != null) {
                    menuDrawer.requestLayout();
                }
                if (imageStage != null) {
                    imageStage.requestLayout();
                }
                if (imageStageNext != null) {
                    imageStageNext.requestLayout();
                }
                if (playerView != null) {
                    playerView.requestLayout();
                }
                layoutCalendarStage();
                if (advancedImageStage != null) {
                    advancedImageStage.requestLayout();
                }
                if (advancedImageStageNext != null) {
                    advancedImageStageNext.requestLayout();
                }
                rebuildDrawerFocusOrder();
                if (isDrawerOpen()) {
                    focusFirstDrawerItem();
                }
                pageRoot.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        rerenderAdvancedLayoutAfterResize();
                    }
                }, 80L);
                pageRoot.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        rerenderAdvancedLayoutAfterResize();
                    }
                }, 260L);
            }
        });
    }

    private void rerenderAdvancedLayoutAfterResize() {
        DevicePullResponse.MediaItem currentMedia = playbackEngine == null ? null : playbackEngine.getCurrentMedia();
        if (currentMedia == null || !"IMAGE".equalsIgnoreCase(currentMedia.getMediaType())) {
            return;
        }
        String displayStyle = resolveDisplayStyle(playbackEngine.getCurrentDisplayStyle(), playbackEngine.getCurrentTransitionStyle());
        if (!isAdvancedDisplayStyle(displayStyle)) {
            return;
        }
        final String currentIdentity = PlaybackEngine.resolveMediaIdentity(currentMedia);
        mainHandler.removeCallbacks(imageAdvanceRunnable);
        pendingAdvancedMediaIdentity = currentIdentity;
        advancedNextReady = false;
        advancedReadyRetryCount = 0;
        advancedPendingLoadCount = 0;
        advancedAnyLoadSuccess = false;
        FrameLayout targetStage = getActiveAdvancedStage();
        if (targetStage == null) {
            return;
        }
        targetStage.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        targetStage.requestLayout();
        targetStage.removeAllViews();
        targetStage.setPadding(0, 0, 0, 0);
        targetStage.setAlpha(1f);
        targetStage.setVisibility(View.VISIBLE);
        bentoImageViews.clear();
        List<DevicePullResponse.MediaItem> imageItems = getAdvancedImageItems();
        if (imageItems.isEmpty()) {
            return;
        }
        if ("CAROUSEL".equals(displayStyle)) {
            renderCarouselLayout(targetStage, imageItems);
        } else if ("FRAME_WALL".equals(displayStyle) || "FRAMEWALL".equals(displayStyle)) {
            renderFrameWallLayout(targetStage, imageItems);
        } else {
            renderBentoLayout(targetStage, imageItems);
        }
        mainHandler.postDelayed(imageAdvanceRunnable, playbackEngine.getCurrentItemDurationSeconds() * 1000L);
    }

    private void showPlaybackSelectionMessage(String message) {
        playbackSelectionSummaryText.setText(message);
        playbackSelectionContainer.removeAllViews();
        rebuildDrawerFocusOrder();
    }

    private void showSystemCapabilityDialog() {
        String report;
        try {
            report = buildSystemCapabilityReport();
        } catch (Throwable error) {
            Log.e(TAG, "build system capability report failed", error);
            report = "\u672a\u80fd\u8bfb\u53d6\u7cfb\u7edf\u80fd\u529b\u4fe1\u606f\n\n"
                    + safeText(error.getMessage(), error.getClass().getSimpleName());
        }
        if (report.trim().isEmpty()) {
            report = "\u672a\u83b7\u53d6\u5230\u7cfb\u7edf\u80fd\u529b\u4fe1\u606f";
        }
        systemCapabilityContentText.setText(report);
        systemCapabilityScrollView.scrollTo(0, 0);
        systemCapabilityScrollView.setNextFocusDownId(systemCapabilityCloseButton.getId());
        systemCapabilityCloseButton.setNextFocusUpId(systemCapabilityScrollView.getId());
        if (isDrawerOpen()) {
            drawerLayout.closeDrawer(GravityCompat.START, false);
        }
        systemCapabilityOverlay.setVisibility(View.VISIBLE);
        systemCapabilityOverlay.bringToFront();
        ViewCompat.setElevation(systemCapabilityOverlay, dp(24));
        systemCapabilityOverlay.requestLayout();
        systemCapabilityScrollView.requestFocus();
    }

    private boolean isSystemCapabilityVisible() {
        return systemCapabilityOverlay != null && systemCapabilityOverlay.getVisibility() == View.VISIBLE;
    }

    private void hideSystemCapabilityOverlay() {
        if (!isSystemCapabilityVisible()) {
            return;
        }
        systemCapabilityOverlay.setVisibility(View.GONE);
        systemCapabilityButton.requestFocus();
    }

    private String buildSystemCapabilityReport() {
        StringBuilder builder = new StringBuilder();
        appendSectionTitle(builder, "\u7cfb\u7edf\u786c\u4ef6\u4fe1\u606f");
        appendKeyValue(builder, "\u54c1\u724c", safeText(Build.BRAND, "-"));
        appendKeyValue(builder, "\u5382\u5546", safeText(Build.MANUFACTURER, "-"));
        appendKeyValue(builder, "\u578b\u53f7", safeText(Build.MODEL, "-"));
        appendKeyValue(builder, "\u8bbe\u5907", safeText(Build.DEVICE, "-"));
        appendKeyValue(builder, "\u4ea7\u54c1", safeText(Build.PRODUCT, "-"));
        appendKeyValue(builder, "Hardware", safeText(Build.HARDWARE, "-"));
        appendKeyValue(builder, "Android", safeText(Build.VERSION.RELEASE, "-") + " (SDK " + Build.VERSION.SDK_INT + ")");
        appendKeyValue(builder, "ABI", resolveSupportedAbis());
        appendKeyValue(builder, "\u5e94\u7528\u7a97\u53e3\u5206\u8fa8\u7387", getResources().getDisplayMetrics().widthPixels + " x " + getResources().getDisplayMetrics().heightPixels);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            appendKeyValue(builder, "\u5f53\u524d\u663e\u793a\u6a21\u5f0f", formatDisplayMode(getCurrentDisplayMode()));
            appendKeyValue(builder, "\u6700\u9ad8\u663e\u793a\u6a21\u5f0f", formatDisplayMode(findLargestDisplayMode()));
            appendKeyValue(builder, "\u4f18\u5148\u9ad8\u5206\u8fa8\u7387", sessionRepository != null && sessionRepository.isPreferNativeDisplayModeEnabled() ? "\u5df2\u542f\u7528" : "\u5df2\u5173\u95ed");
        }

        ActivityManager activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        if (activityManager != null) {
            appendKeyValue(builder, "\u53ef\u7528\u5185\u5b58\u7ea7\u522b", activityManager.getMemoryClass() + " MB");
            ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
            activityManager.getMemoryInfo(memoryInfo);
            appendKeyValue(builder, "\u53ef\u7528\u8fd0\u884c\u5185\u5b58", formatBytes(memoryInfo.availMem));
            appendKeyValue(builder, "\u603b\u5185\u5b58", formatBytes(memoryInfo.totalMem));
            appendKeyValue(builder, "\u4f4e\u5185\u5b58\u8bbe\u5907", memoryInfo.lowMemory ? "\u662f" : "\u5426");
        }

        builder.append('\n');
        appendSectionTitle(builder, "\u89e3\u7801\u80fd\u529b");
        for (int i = 0; i < SYSTEM_CAPABILITY_MIME_TYPES.length; i += 1) {
            appendCodecCapability(builder, SYSTEM_CAPABILITY_LABELS[i], SYSTEM_CAPABILITY_MIME_TYPES[i]);
        }
        return builder.toString().trim();
    }

    private void appendSectionTitle(StringBuilder builder, String title) {
        builder.append(title).append('\n');
    }

    private void appendKeyValue(StringBuilder builder, String key, String value) {
        builder.append(key).append(": ").append(value).append('\n');
    }

    private void appendCodecCapability(StringBuilder builder, String label, String mimeType) {
        List<String> decoderNames = findDecoderNames(mimeType);
        if (decoderNames.isEmpty()) {
            builder.append(label).append(": ").append("\u4e0d\u652f\u6301").append('\n');
            return;
        }
        builder.append(label).append(": ").append("\u652f\u6301").append(" (").append(decoderNames.size()).append(")").append('\n');
        builder.append("\u89e3\u7801\u5668: ").append(joinValues(decoderNames)).append('\n');
    }

    private String resolveSupportedAbis() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return joinValues(Build.SUPPORTED_ABIS);
        }
        List<String> abis = new ArrayList<String>();
        if (Build.CPU_ABI != null && !Build.CPU_ABI.trim().isEmpty()) {
            abis.add(Build.CPU_ABI);
        }
        if (Build.CPU_ABI2 != null && !Build.CPU_ABI2.trim().isEmpty()) {
            abis.add(Build.CPU_ABI2);
        }
        return abis.isEmpty() ? "-" : joinValues(abis);
    }

    private String formatBytes(long bytes) {
        if (bytes <= 0L) {
            return "-";
        }
        double gb = bytes / (1024d * 1024d * 1024d);
        if (gb >= 1d) {
            return String.format(Locale.US, "%.1f GB", gb);
        }
        double mb = bytes / (1024d * 1024d);
        if (mb >= 10d) {
            return String.format(Locale.US, "%.0f MB", mb);
        }
        return String.format(Locale.US, "%.1f MB", mb);
    }

    private List<String> findDecoderNames(String mimeType) {
        LinkedHashSet<String> decoderNames = new LinkedHashSet<String>();
        try {
            MediaCodecInfo[] codecInfos = loadCodecInfos();
            for (MediaCodecInfo codecInfo : codecInfos) {
                if (codecInfo == null || codecInfo.isEncoder()) {
                    continue;
                }
                String[] supportedTypes = codecInfo.getSupportedTypes();
                for (String supportedType : supportedTypes) {
                    if (supportedType != null && supportedType.equalsIgnoreCase(mimeType)) {
                        decoderNames.add(buildCodecDisplayName(codecInfo));
                        break;
                    }
                }
            }
        } catch (Throwable error) {
            Log.w(TAG, "load decoder names failed for " + mimeType, error);
        }
        return new ArrayList<String>(decoderNames);
    }

    private MediaCodecInfo[] loadCodecInfos() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return new MediaCodecList(MediaCodecList.ALL_CODECS).getCodecInfos();
        }
        MediaCodecInfo[] codecInfos = new MediaCodecInfo[MediaCodecList.getCodecCount()];
        for (int i = 0; i < codecInfos.length; i += 1) {
            codecInfos[i] = MediaCodecList.getCodecInfoAt(i);
        }
        return codecInfos;
    }

    private String buildCodecDisplayName(MediaCodecInfo codecInfo) {
        String name = codecInfo.getName();
        if (Build.VERSION.SDK_INT >= 29) {
            if (codecInfo.isHardwareAccelerated()) {
                return name + " [\u786c\u89e3]";
            }
            if (codecInfo.isSoftwareOnly()) {
                return name + " [\u8f6f\u89e3]";
            }
        }
        return name;
    }

    private String joinValues(String[] values) {
        if (values == null || values.length == 0) {
            return "-";
        }
        List<String> cleanValues = new ArrayList<String>();
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                cleanValues.add(value.trim());
            }
        }
        return cleanValues.isEmpty() ? "-" : joinValues(cleanValues);
    }

    private String joinValues(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "-";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.size(); i += 1) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(values.get(i));
        }
        return builder.toString();
    }

    private String buildDistributionLabel(DevicePullResponse.DistributionItem distribution) {
        String name = distribution == null ? "" : distribution.getName();
        name = safeText(name, "\u672a\u547d\u540d\u5206\u7ec4");
        int mediaCount = distribution == null || distribution.getMediaList() == null ? 0 : distribution.getMediaList().size();
        return name + " (" + mediaCount + " \u4e2a\u5a92\u4f53)";
    }

    private String safeText(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private void updateStatus(String status) {
        statusText.setText(status);
    }

    private void checkAppUpdate(final boolean showInitialDialog) {
        mainHandler.removeCallbacks(updatePollRunnable);
        if (showInitialDialog) {
            updateAppUpdatePanel(availableUpdate, APP_UPDATE_CHECKING);
        }
        appUpdateChecker.checkForUpdate(showInitialDialog, new AppUpdateChecker.UpdateCallback() {
            @Override
            public void onChecked(AppUpdateResponse update) {
                updateAppUpdatePanel(update, null);
                scheduleNextUpdateCheck();
            }

            @Override
            public void onFailed(java.io.IOException error) {
                updateAppUpdatePanel(availableUpdate, APP_UPDATE_CHECK_FAILED);
                scheduleNextUpdateCheck();
            }
        });
    }

    private void scheduleNextUpdateCheck() {
        mainHandler.removeCallbacks(updatePollRunnable);
        mainHandler.postDelayed(updatePollRunnable, UPDATE_POLL_INTERVAL_MS);
    }

    private void updateAppUpdatePanel(AppUpdateResponse update, String fallbackMessage) {
        boolean hasUpdate = update != null && Boolean.TRUE.equals(update.getHasUpdate());
        availableUpdate = hasUpdate ? update : null;
        appUpdateButton.setVisibility(hasUpdate ? View.VISIBLE : View.GONE);
        appUpdateButton.setEnabled(hasUpdate);
        appUpdateButton.setFocusable(hasUpdate);
        appUpdateSummaryText.setText(hasUpdate ? buildAppUpdateSummary(update) : safeText(fallbackMessage, APP_UPDATE_CURRENT));
        rebuildDrawerFocusOrder();
    }

    private String buildAppUpdateSummary(AppUpdateResponse update) {
        StringBuilder builder = new StringBuilder();
        builder.append("\u53d1\u73b0\u65b0\u7248\u672c");
        if (safeText(update.getLatestVersion(), "").length() > 0) {
            builder.append("\uff1a").append(update.getLatestVersion());
        }
        Integer latestVersionCode = update.getLatestVersionCode();
        if (latestVersionCode != null) {
            builder.append(" (").append(latestVersionCode).append(")");
        }
        if (Boolean.TRUE.equals(update.getForceUpdate())) {
            builder.append("\n\u5fc5\u987b\u66f4\u65b0");
        }
        if (safeText(update.getReleaseNotes(), "").length() > 0) {
            builder.append("\n\n").append(update.getReleaseNotes());
        }
        return builder.toString();
    }

    private void clearScheduledTasks() {
        mainHandler.removeCallbacks(imageAdvanceRunnable);
        mainHandler.removeCallbacksAndMessages(null);
        cancelImageTransition();
    }

    private void enterImmersiveMode() {
        configureFullscreenWindow();
        View decorView = getWindow().getDecorView();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );
        } else {
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN);
        }
    }

    private void configureFullscreenWindow() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        applyPreferredDisplayMode();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams attributes = getWindow().getAttributes();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            } else {
                attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            }
            getWindow().setAttributes(attributes);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !isTvDevice()) {
            getWindow().setDecorFitsSystemWindows(false);
        }
    }

    private void applyPreferredDisplayMode() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || sessionRepository == null) {
            return;
        }
        WindowManager.LayoutParams attributes = getWindow().getAttributes();
        int preferredModeId = 0;
        if (sessionRepository.isPreferNativeDisplayModeEnabled()) {
            Display.Mode mode = findLargestDisplayMode();
            preferredModeId = mode == null ? 0 : mode.getModeId();
        }
        if (attributes.preferredDisplayModeId != preferredModeId) {
            attributes.preferredDisplayModeId = preferredModeId;
            getWindow().setAttributes(attributes);
        }
    }

    private Display.Mode findLargestDisplayMode() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return null;
        }
        Display display = getWindowManager().getDefaultDisplay();
        if (display == null) {
            return null;
        }
        Display.Mode[] modes = display.getSupportedModes();
        if (modes == null || modes.length == 0) {
            return display.getMode();
        }
        Display.Mode best = modes[0];
        for (Display.Mode mode : modes) {
            if (mode == null) {
                continue;
            }
            long modePixels = (long) mode.getPhysicalWidth() * (long) mode.getPhysicalHeight();
            long bestPixels = (long) best.getPhysicalWidth() * (long) best.getPhysicalHeight();
            if (modePixels > bestPixels
                    || (modePixels == bestPixels && mode.getRefreshRate() > best.getRefreshRate())) {
                best = mode;
            }
        }
        return best;
    }

    private boolean isTvDevice() {
        int uiModeType = getResources().getConfiguration().uiMode & Configuration.UI_MODE_TYPE_MASK;
        return uiModeType == Configuration.UI_MODE_TYPE_TELEVISION
                || getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK);
    }

    private void toggleDrawerMenu() {
        if (isDrawerOpen()) {
            closeDrawerMenu();
        } else {
            openDrawerMenu();
        }
    }

    private void openDrawerMenu() {
        drawerLayout.openDrawer(GravityCompat.START);
    }

    private void closeDrawerMenu() {
        drawerLayout.closeDrawer(GravityCompat.START);
    }

    private boolean isDrawerOpen() {
        return drawerLayout.isDrawerOpen(GravityCompat.START);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void rebuildDrawerFocusOrder() {
        View focusedView = getCurrentFocus();

        drawerFocusableViews.clear();
        addDrawerFocusableView(deviceInfoPanel);
        addDrawerFocusableView(currentMediaPanel);
        collectFocusableChildren(playbackSelectionContainer);
        addDrawerFocusableView(refreshButton);
        addDrawerFocusableView(setupButton);
        addDrawerFocusableView(appUpdatePanel);
        addDrawerFocusableView(rotationPanel);
        addDrawerFocusableView(mutePanel);
        addDrawerFocusableView(displayModePanel);
        collectFocusableChildren(brightnessPanel);
        collectFocusableChildren(bootAutoStartPanel);
        addDrawerFocusableView(aboutButton);
        addDrawerFocusableView(systemCapabilityButton);

        for (int i = 0; i < drawerFocusableViews.size(); i += 1) {
            View current = drawerFocusableViews.get(i);
            View up = i > 0 ? drawerFocusableViews.get(i - 1) : current;
            View down = i < drawerFocusableViews.size() - 1 ? drawerFocusableViews.get(i + 1) : current;
            current.setNextFocusUpId(up.getId());
            current.setNextFocusDownId(down.getId());
        }

        if (focusedView != null && focusedView.isAttachedToWindow()
                && focusedView.getVisibility() == View.VISIBLE && focusedView.isFocusable()) {
            focusedView.requestFocus();
        }
    }

    private void addDrawerFocusableView(View view) {
        if (view == null) {
            return;
        }
        if (view.getVisibility() != View.VISIBLE || !view.isEnabled()) {
            return;
        }
        if (view.getId() == View.NO_ID) {
            view.setId(View.generateViewId());
        }
        view.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View focusedView, boolean hasFocus) {
                if (hasFocus) {
                    ensureDrawerFocusVisible(focusedView);
                }
            }
        });
        drawerFocusableViews.add(view);
    }

    private void collectFocusableChildren(LinearLayout container) {
        if (container == null) {
            return;
        }
        for (int i = 0; i < container.getChildCount(); i += 1) {
            View child = container.getChildAt(i);
            collectFocusableView(child);
        }
    }

    private void collectFocusableView(View view) {
        if (view == null) {
            return;
        }
        if (view.getVisibility() != View.VISIBLE || !view.isEnabled()) {
            return;
        }
        if (view instanceof CheckBox || view instanceof Button || view instanceof Spinner) {
            addDrawerFocusableView(view);
            return;
        }
        if (view instanceof LinearLayout) {
            LinearLayout layout = (LinearLayout) view;
            for (int i = 0; i < layout.getChildCount(); i += 1) {
                collectFocusableView(layout.getChildAt(i));
            }
        }
    }

    private void focusFirstDrawerItem() {
        rebuildDrawerFocusOrder();
        if (!drawerFocusableViews.isEmpty()) {
            drawerFocusableViews.get(0).requestFocus();
        }
    }

    private void ensureDrawerFocusVisible(final View focusedView) {
        if (menuDrawer == null || focusedView == null) {
            return;
        }
        menuDrawer.post(new Runnable() {
            @Override
            public void run() {
                int viewTop = 0;
                View child = focusedView;
                while (child != menuDrawer && child != null) {
                    viewTop += child.getTop();
                    child = (View) child.getParent();
                }
                int targetScroll = Math.max(0, viewTop - dp(32));
                menuDrawer.smoothScrollTo(0, targetScroll);
            }
        });
    }

    private boolean safeEquals(String left, String right) {
        if (left == null) {
            return right == null;
        }
        return left.equals(right);
    }
}
