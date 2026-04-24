package com.cloudalbum.publisher.android.player;

import android.os.Handler;
import android.os.Looper;

import com.cloudalbum.publisher.android.data.model.DevicePullResponse;
import com.cloudalbum.publisher.android.data.repository.CloudAlbumRepository;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PullSyncCoordinator {
    public interface Listener {
        void onPullSuccess(DevicePullResponse response);
        void onPullError(Exception error);
    }

    private static final long SYNC_INTERVAL_MS = 30000L;

    private final CloudAlbumRepository repository;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Runnable periodicRunnable = new Runnable() {
        @Override
        public void run() {
            refreshNow();
            handler.postDelayed(this, SYNC_INTERVAL_MS);
        }
    };

    public PullSyncCoordinator(CloudAlbumRepository repository, Listener listener) {
        this.repository = repository;
        this.listener = listener;
    }

    public void start() {
        stop();
        refreshNow();
        handler.postDelayed(periodicRunnable, SYNC_INTERVAL_MS);
    }

    public void refreshNow() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final DevicePullResponse response = repository.pullCurrent();
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            listener.onPullSuccess(response);
                        }
                    });
                } catch (final Exception error) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            listener.onPullError(error);
                        }
                    });
                }
            }
        });
    }

    public void stop() {
        handler.removeCallbacks(periodicRunnable);
    }
}
