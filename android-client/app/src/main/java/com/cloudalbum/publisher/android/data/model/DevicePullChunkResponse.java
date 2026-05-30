package com.cloudalbum.publisher.android.data.model;

import java.util.List;

public class DevicePullChunkResponse extends DevicePullResponse {
    private String snapshotId;
    private String cursor;
    private boolean hasMore;
    private boolean finalChunk;
    private int returnedDistributionCount;
    private int returnedMediaCount;

    public String getSnapshotId() {
        return snapshotId;
    }

    public String getCursor() {
        return cursor;
    }

    public boolean isHasMore() {
        return hasMore;
    }

    public boolean isFinalChunk() {
        return finalChunk;
    }

    public int getReturnedDistributionCount() {
        return returnedDistributionCount;
    }

    public int getReturnedMediaCount() {
        return returnedMediaCount;
    }
}
