package com.cloudalbum.publisher.android.player;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.cloudalbum.publisher.android.data.model.DevicePullResponse;
import com.cloudalbum.publisher.android.data.repository.CloudAlbumRepository;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class PullSyncCoordinator {
    private static final String TAG = "PullSyncCoordinator";

    public interface Listener {
        void onPullSuccess(DevicePullResponse response);
        void onPullError(Exception error);
    }

    private static final long SYNC_INTERVAL_MS = 30000L;

    private final CloudAlbumRepository repository;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean pullInProgress = new AtomicBoolean(false);
    private volatile boolean stopped = false;
    private final Runnable periodicRunnable = new Runnable() {
        @Override
        public void run() {
            if (stopped) return;
            refreshNow();
            if (!stopped) {
                handler.postDelayed(this, SYNC_INTERVAL_MS);
            }
        }
    };

    public PullSyncCoordinator(CloudAlbumRepository repository, Listener listener) {
        this.repository = repository;
        this.listener = listener;
    }

    public void start() {
        stop();
        stopped = false;
        refreshNow();
        handler.postDelayed(periodicRunnable, SYNC_INTERVAL_MS);
    }

    public void refreshNow() {
        if (stopped) return;
        if (!pullInProgress.compareAndSet(false, true)) {
            Log.d(TAG, "Pull already in progress, skipping");
            return;
        }
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final DevicePullResponse response = repository.pullCurrent();
                    if (stopped) return;
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (stopped) return;
                            listener.onPullSuccess(response);
                        }
                    });
                } catch (final Exception error) {
                    if (stopped) return;
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (stopped) return;
                            listener.onPullError(error);
                        }
                    });
                } finally {
                    pullInProgress.set(false);
                }
            }
        });
    }

    public void stop() {
        stopped = true;
        handler.removeCallbacks(periodicRunnable);
    }

    public void shutdown() {
        stop();
        executor.shutdownNow();
        handler.removeCallbacksAndMessages(null);
    }
}
