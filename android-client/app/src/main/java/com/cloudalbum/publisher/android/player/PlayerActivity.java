package com.cloudalbum.publisher.android.player;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.RectEvaluator;
import android.animation.ValueAnimator;
import android.app.ActivityManager;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class PlayerActivity extends AppCompatActivity implements PullSyncCoordinator.Listener {
    private static final String TAG = "PlayerActivity";
    private static final long TOKEN_POLL_INTERVAL_MS = 15000L;
    private static final long UPDATE_POLL_INTERVAL_MS = 60 * 1000L;
    private static final long IMAGE_ADVANCE_RETRY_DELAY_MS = 300L;
    private static final int IMAGE_ADVANCE_MAX_RETRY_COUNT = 10;
    private static final int DRAWER_FOCUS_TOP_EXTRA_DP = 96;
    private static final int DRAWER_HANDLE_SWIPE_THRESHOLD_DP = 36;
    private static final long IMAGE_TRANSITION_DURATION_MS = 650L;
    private static final String[] RANDOM_IMAGE_TRANSITIONS = new String[] {"FADE", "SLIDE", "CUBE", "REVEAL", "FLIP"};
    private static final String REFRESH_BUTTON_LABEL = "\u7acb\u5373\u540c\u6b65";
    private static final String SETUP_BUTTON_LABEL = "\u8bbe\u7f6e";
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

    private final Handler imageHandler = new Handler(Looper.getMainLooper());
    private final Handler errorHandler = new Handler(Looper.getMainLooper());
    private final Handler tokenHandler = new Handler(Looper.getMainLooper());
    private final Handler updateHandler = new Handler(Looper.getMainLooper());
    private final Rect drawerFocusRect = new Rect();
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
    private final Set<String> disabledDistributionIds = new HashSet<String>();
    private final Set<String> preloadedImageIdentities = new HashSet<String>();
    private final List<View> drawerFocusableViews = new ArrayList<View>();
    private CloudAlbumRepository repository;
    private DeviceSessionRepository sessionRepository;
    private PullSyncCoordinator pullSyncCoordinator;
    private AppUpdateChecker appUpdateChecker;
    private PlaybackEngine playbackEngine;
    private SimpleExoPlayer contentPlayer;
    private SimpleExoPlayer bgmPlayer;

    private FrameLayout pageRoot;
    private FrameLayout drawerGestureHandle;
    private DrawerLayout drawerLayout;
    private ScrollView menuDrawer;
    private LinearLayout deviceInfoPanel;
    private LinearLayout currentMediaPanel;
    private ImageView imageStage;
    private ImageView imageStageNext;
    private StyledPlayerView playerView;
    private TextView deviceSummaryText;
    private TextView distributionText;
    private TextView mediaText;
    private TextView statusText;
    private TextView emptyStateText;
    private TextView rotationTitleText;
    private TextView rotationSummaryText;
    private TextView muteTitleText;
    private TextView muteSummaryText;
    private TextView playbackSelectionTitleText;
    private TextView playbackSelectionSummaryText;
    private TextView appUpdateTitleText;
    private TextView appUpdateSummaryText;
    private TextView systemCapabilityTitleText;
    private TextView systemCapabilityContentText;
    private LinearLayout rotationOptionContainer;
    private LinearLayout playbackSelectionContainer;
    private FrameLayout systemCapabilityOverlay;
    private CheckBox muteToggleCheckBox;
    private Button refreshButton;
    private Button setupButton;
    private Button appUpdateButton;
    private Button systemCapabilityButton;
    private Button systemCapabilityCloseButton;
    private ScrollView systemCapabilityScrollView;

    private String lastRenderedMediaIdentity = "";
    private String lastRenderedMediaType = "";
    private String lastRenderedBgmIdentity = "";
    private String playbackRotationMode = DeviceSessionRepository.PLAYBACK_ROTATION_AUTO;
    private String playerAccessToken = "";
    private boolean playbackMuted = false;
    private boolean waitingForBinding = false;
    private DevicePullResponse lastPullResponse;
    private PageRotationController rotationController;
    private AppUpdateResponse availableUpdate;
    private float drawerHandleDownX;
    private boolean drawerHandleOpenedBySwipe;
    private boolean primaryImageStageActive = true;
    private int imageAdvanceRetryCount = 0;
    private Animator imageTransitionAnimator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sessionRepository = new DeviceSessionRepository(this);
        repository = new CloudAlbumRepository(this);

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
    }

    @Override
    protected void onStart() {
        super.onStart();
        enterImmersiveMode();
        checkAppUpdate(true);
        if (sessionRepository.isActivated()) {
            waitingForBinding = false;
            pullSyncCoordinator.start();
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
        tokenHandler.removeCallbacks(tokenPollRunnable);
        updateHandler.removeCallbacks(updatePollRunnable);
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
        tokenHandler.removeCallbacks(tokenPollRunnable);
        updateHandler.removeCallbacks(updatePollRunnable);
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
        drawerGestureHandle = findViewById(R.id.drawerGestureHandle);
        drawerLayout = findViewById(R.id.playerDrawerLayout);
        menuDrawer = findViewById(R.id.menuDrawer);
        deviceInfoPanel = findViewById(R.id.deviceInfoPanel);
        currentMediaPanel = findViewById(R.id.currentMediaPanel);
        imageStage = findViewById(R.id.imageStage);
        imageStageNext = findViewById(R.id.imageStageNext);
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
        playbackSelectionTitleText = findViewById(R.id.playbackSelectionTitleText);
        playbackSelectionSummaryText = findViewById(R.id.playbackSelectionSummaryText);
        appUpdateTitleText = findViewById(R.id.appUpdateTitleText);
        appUpdateSummaryText = findViewById(R.id.appUpdateSummaryText);
        systemCapabilityOverlay = findViewById(R.id.systemCapabilityOverlay);
        systemCapabilityTitleText = findViewById(R.id.systemCapabilityTitleText);
        systemCapabilityContentText = findViewById(R.id.systemCapabilityContentText);
        systemCapabilityScrollView = findViewById(R.id.systemCapabilityScrollView);
        rotationOptionContainer = findViewById(R.id.rotationOptionContainer);
        playbackSelectionContainer = findViewById(R.id.playbackSelectionContainer);
        muteToggleCheckBox = findViewById(R.id.muteToggleCheckBox);
        refreshButton = findViewById(R.id.refreshButton);
        setupButton = findViewById(R.id.setupButton);
        appUpdateButton = findViewById(R.id.appUpdateButton);
        systemCapabilityButton = findViewById(R.id.systemCapabilityButton);
        systemCapabilityCloseButton = findViewById(R.id.systemCapabilityCloseButton);
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
        muteToggleCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                updatePlaybackMuted(isChecked);
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
                .setContentType(com.google.android.exoplayer2.C.CONTENT_TYPE_MOVIE)
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
                errorHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
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
                .setContentType(com.google.android.exoplayer2.C.CONTENT_TYPE_MUSIC)
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
                errorHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
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
        tokenHandler.removeCallbacks(tokenPollRunnable);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    repository.selfRegisterCurrentDevice();
                    DeviceTokenResponse tokenResponse = repository.issueDeviceToken();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
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
                    Log.e(TAG, "acquire token failed", error);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            waitingForBinding = true;
                            showWaitingForBinding();
                            updateStatus(error.getMessage() == null || error.getMessage().trim().isEmpty()
                                    ? WAITING_FOR_BINDING_STATUS
                                    : error.getMessage());
                            tokenHandler.postDelayed(tokenPollRunnable, TOKEN_POLL_INTERVAL_MS);
                        }
                    });
                }
            }
        }).start();
    }

    private void updateDeviceSummary() {
        deviceSummaryText.setText(sessionRepository.getDeviceName() + " / " + sessionRepository.getDeviceModel());
    }

    @Override
    public void onPullSuccess(DevicePullResponse response) {
        lastPullResponse = response;
        playbackEngine.update(response);
        syncPlaybackSelectionState(response);
        renderPlayback();
        updateStatus(getString(R.string.sync_status_ready) + " / "
                + (playbackEngine.getPulledAt().isEmpty() ? getString(R.string.unknown_value) : playbackEngine.getPulledAt()));
    }

    @Override
    public void onPullError(Exception error) {
        updateStatus(getString(R.string.sync_status_error) + " / "
                + (error.getMessage() == null ? getString(R.string.toast_sync_failed) : error.getMessage()));
    }

    private void showWaitingForBinding() {
        clearScheduledTasks();
        lastPullResponse = null;
        lastRenderedMediaIdentity = "";
        lastRenderedMediaType = "";
        lastRenderedBgmIdentity = "";
        preloadedImageIdentities.clear();
        imageAdvanceRetryCount = 0;
        hideImageStages();
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
        DevicePullResponse.DistributionItem distribution = playbackEngine.getCurrentDistribution();
        DevicePullResponse.MediaItem media = playbackEngine.getCurrentMedia();

        if (!playbackEngine.hasPlayableContent() || distribution == null || media == null) {
            clearScheduledTasks();
            lastRenderedMediaIdentity = "";
            lastRenderedMediaType = "";
            lastRenderedBgmIdentity = "";
            preloadedImageIdentities.clear();
            imageAdvanceRetryCount = 0;
            hideImageStages();
            playerView.setVisibility(View.GONE);
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

        String currentMediaIdentity = PlaybackEngine.resolveMediaIdentity(media);
        if (!currentMediaIdentity.equals(lastRenderedMediaIdentity)) {
            clearScheduledTasks();
            imageAdvanceRetryCount = 0;
            String previousMediaType = lastRenderedMediaType;
            lastRenderedMediaIdentity = currentMediaIdentity;
            lastRenderedMediaType = safeText(media.getMediaType(), "");
            if ("IMAGE".equalsIgnoreCase(media.getMediaType())) {
                playerView.setVisibility(View.GONE);
                if (contentPlayer != null) {
                    contentPlayer.stop();
                }
                renderImageMedia(media, currentMediaIdentity, "IMAGE".equalsIgnoreCase(previousMediaType));
            } else {
                hideImageStages();
                playerView.setVisibility(View.VISIBLE);
                MediaItem mediaItem = new MediaItem.Builder()
                        .setUri(Uri.parse(media.getUrl()))
                        .setMimeType(media.getContentType())
                        .build();
                contentPlayer.setMediaItem(mediaItem, true);
                contentPlayer.prepare();
                contentPlayer.play();
            }
        } else if ("IMAGE".equalsIgnoreCase(media.getMediaType())) {
            imageHandler.removeCallbacks(imageAdvanceRunnable);
            preloadNextImage();
            imageHandler.postDelayed(imageAdvanceRunnable, playbackEngine.getCurrentItemDurationSeconds() * 1000L);
        } else if (!contentPlayer.isPlaying()) {
            contentPlayer.play();
        }

        renderBgm();
    }

    private void renderImageMedia(final DevicePullResponse.MediaItem media, final String mediaIdentity, final boolean previousWasImage) {
        final ImageView targetStage = getInactiveImageStage();
        final ImageView currentStage = getActiveImageStage();
        resetImageStage(targetStage);
        targetStage.setVisibility(View.INVISIBLE);
        AuthenticatedImageLoader.load(targetStage, media.getUrl(), sessionRepository, new AuthenticatedImageLoader.Callback() {
            @Override
            public void onSuccess() {
                if (!mediaIdentity.equals(lastRenderedMediaIdentity)) {
                    return;
                }
                String transitionStyle = previousWasImage ? resolveImageTransitionStyle(playbackEngine.getCurrentTransitionStyle(), mediaIdentity) : "NONE";
                if ("NONE".equals(transitionStyle) || currentStage.getVisibility() != View.VISIBLE) {
                    showImageWithoutTransition(targetStage, currentStage);
                } else {
                    runImageTransition(currentStage, targetStage, transitionStyle);
                }
                preloadedImageIdentities.add(mediaIdentity);
                preloadNextImage();
                imageHandler.removeCallbacks(imageAdvanceRunnable);
                imageHandler.postDelayed(imageAdvanceRunnable, playbackEngine.getCurrentItemDurationSeconds() * 1000L);
            }

            @Override
            public void onFailure() {
                if (!mediaIdentity.equals(lastRenderedMediaIdentity)) {
                    return;
                }
                Toast.makeText(PlayerActivity.this, R.string.toast_media_error, Toast.LENGTH_SHORT).show();
                errorHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        playbackEngine.nextMedia();
                        renderPlayback();
                    }
                }, 1500L);
            }
        });
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

    private void runImageTransition(final ImageView fromStage, final ImageView toStage, String transitionStyle) {
        cancelImageTransition();
        resetImageStage(fromStage);
        resetImageStage(toStage);
        fromStage.setVisibility(View.VISIBLE);
        toStage.setVisibility(View.VISIBLE);
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
        AuthenticatedImageLoader.preload(imageStage, nextMedia.getUrl(), sessionRepository, new AuthenticatedImageLoader.Callback() {
            @Override
            public void onSuccess() {
                preloadedImageIdentities.add(nextMediaIdentity);
            }

            @Override
            public void onFailure() {
                preloadedImageIdentities.remove(nextMediaIdentity);
            }
        });
    }

    private void advanceImageWhenReady() {
        DevicePullResponse.MediaItem nextMedia = playbackEngine.peekNextMedia();
        if (nextMedia != null && "IMAGE".equalsIgnoreCase(nextMedia.getMediaType())) {
            String nextMediaIdentity = PlaybackEngine.resolveMediaIdentity(nextMedia);
            if (!preloadedImageIdentities.contains(nextMediaIdentity)
                    && imageAdvanceRetryCount < IMAGE_ADVANCE_MAX_RETRY_COUNT) {
                imageAdvanceRetryCount += 1;
                preloadNextImage();
                imageHandler.postDelayed(imageAdvanceRunnable, IMAGE_ADVANCE_RETRY_DELAY_MS);
                return;
            }
        }
        imageAdvanceRetryCount = 0;
        playbackEngine.nextMedia();
        renderPlayback();
    }

    private void renderBgm() {
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
        rotationOptionContainer.removeAllViews();
        rotationOptionContainer.addView(createRotationOption(DeviceSessionRepository.PLAYBACK_ROTATION_AUTO, ROTATION_AUTO_LABEL));
        rotationOptionContainer.addView(createRotationOption(DeviceSessionRepository.PLAYBACK_ROTATION_0, ROTATION_0_LABEL));
        rotationOptionContainer.addView(createRotationOption(DeviceSessionRepository.PLAYBACK_ROTATION_90, ROTATION_90_LABEL));
        rotationOptionContainer.addView(createRotationOption(DeviceSessionRepository.PLAYBACK_ROTATION_180, ROTATION_180_LABEL));
        rotationOptionContainer.addView(createRotationOption(DeviceSessionRepository.PLAYBACK_ROTATION_270, ROTATION_270_LABEL));
        updateRotationSummary();
        rebuildDrawerFocusOrder();
    }

    private View createRotationOption(final String mode, String label) {
        CheckBox option = new CheckBox(this);
        option.setId(View.generateViewId());
        option.setText(label);
        applyDrawerOptionStyle(option);
        option.setFocusable(true);
        option.setFocusableInTouchMode(false);
        option.setChecked(mode.equals(playbackRotationMode));
        option.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    updatePlaybackRotationMode(mode);
                } else if (mode.equals(playbackRotationMode)) {
                    buttonView.setChecked(true);
                }
            }
        });
        return option;
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

    private void updateMuteSummary() {
        muteSummaryText.setText(playbackMuted ? MUTE_ENABLED_LABEL : MUTE_DISABLED_LABEL);
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
                rebuildDrawerFocusOrder();
                if (isDrawerOpen()) {
                    focusFirstDrawerItem();
                }
            }
        });
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
        appendKeyValue(builder, "\u5206\u8fa8\u7387", getResources().getDisplayMetrics().widthPixels + " x " + getResources().getDisplayMetrics().heightPixels);

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
        return String.format(Locale.US, "%.0f MB", mb);
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
        updateHandler.removeCallbacks(updatePollRunnable);
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
        updateHandler.removeCallbacks(updatePollRunnable);
        updateHandler.postDelayed(updatePollRunnable, UPDATE_POLL_INTERVAL_MS);
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
        imageHandler.removeCallbacks(imageAdvanceRunnable);
        errorHandler.removeCallbacksAndMessages(null);
        cancelImageTransition();
    }

    private void enterImmersiveMode() {
        View decorView = getWindow().getDecorView();
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
        drawerFocusableViews.clear();
        addDrawerFocusableView(deviceInfoPanel);
        addDrawerFocusableView(currentMediaPanel);
        addDrawerFocusableView(refreshButton);
        addDrawerFocusableView(setupButton);
        addDrawerFocusableView(appUpdateButton);
        collectFocusableChildren(rotationOptionContainer);
        addDrawerFocusableView(muteToggleCheckBox);
        collectFocusableChildren(playbackSelectionContainer);
        addDrawerFocusableView(systemCapabilityButton);

        for (int i = 0; i < drawerFocusableViews.size(); i += 1) {
            View current = drawerFocusableViews.get(i);
            View up = i > 0 ? drawerFocusableViews.get(i - 1) : current;
            View down = i < drawerFocusableViews.size() - 1 ? drawerFocusableViews.get(i + 1) : current;
            current.setNextFocusUpId(up.getId());
            current.setNextFocusDownId(down.getId());
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
        if (view instanceof CheckBox || view instanceof Button) {
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
                focusedView.getDrawingRect(drawerFocusRect);
                menuDrawer.offsetDescendantRectToMyCoords(focusedView, drawerFocusRect);
                int extraTop = focusedView == refreshButton ? dp(DRAWER_FOCUS_TOP_EXTRA_DP) : dp(16);
                drawerFocusRect.top = Math.max(0, drawerFocusRect.top - extraTop);
                drawerFocusRect.bottom = drawerFocusRect.bottom + dp(16);
                menuDrawer.requestChildRectangleOnScreen(focusedView, drawerFocusRect, false);
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
