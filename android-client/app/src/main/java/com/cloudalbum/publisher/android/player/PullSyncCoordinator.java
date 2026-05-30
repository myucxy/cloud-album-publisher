package com.cloudalbum.publisher.android.player;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.cloudalbum.publisher.android.data.model.DevicePullChunkResponse;
import com.cloudalbum.publisher.android.data.model.DevicePullResponse;
import com.cloudalbum.publisher.android.data.repository.CloudAlbumRepository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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
    private static final int MAX_CHUNK_RETRIES = 500;

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
                    final DevicePullResponse response = pullFullContent();
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

    private DevicePullResponse pullFullContent() throws IOException {
        try {
            return pullChunked();
        } catch (IOException e) {
            String message = e.getMessage();
            if (message != null && (message.contains("404") || message.contains("Not Found"))) {
                Log.d(TAG, "Chunk endpoint not available, falling back to full pull");
                return repository.pullCurrent();
            }
            throw e;
        }
    }

    private DevicePullResponse pullChunked() throws IOException {
        List<DevicePullResponse.DistributionItem> allDistributions = new ArrayList<DevicePullResponse.DistributionItem>();
        String pulledAt = null;
        String cursor = null;
        int safetyCounter = 0;

        do {
            DevicePullChunkResponse chunk = repository.pullCurrentChunk(cursor);
            if (chunk.getDistributions() != null) {
                allDistributions.addAll(chunk.getDistributions());
            }
            if (chunk.getPulledAt() != null) {
                pulledAt = chunk.getPulledAt();
            }
            if (!chunk.isHasMore()) {
                break;
            }
            cursor = chunk.getCursor();
            safetyCounter += 1;
        } while (cursor != null && safetyCounter < MAX_CHUNK_RETRIES);

        DevicePullResponse merged = new DevicePullResponse();
        merged.setDistributions(allDistributions);
        merged.setPulledAt(pulledAt);
        return merged;
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
