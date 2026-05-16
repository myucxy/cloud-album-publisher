package com.cloudalbum.publisher.android.player;

import com.cloudalbum.publisher.android.data.model.DevicePullResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class PlaybackEngine {
    private List<DevicePullResponse.DistributionItem> distributions = new ArrayList<>();
    private int currentDistributionIndex = 0;
    private int currentMediaIndex = 0;
    private int currentBgmIndex = 0;
    private String pulledAt = "";
    private Set<String> disabledDistributionIds = new HashSet<String>();

    public void update(DevicePullResponse response) {
        long previousDistributionId = getCurrentDistribution() != null ? getCurrentDistribution().getId() : 0L;
        String previousMediaIdentity = resolveMediaIdentity(getCurrentMedia());
        String previousBgmIdentity = resolveBgmIdentity(getCurrentBgm());

        List<DevicePullResponse.DistributionItem> next = response != null && response.getDistributions() != null
                ? response.getDistributions()
                : new ArrayList<DevicePullResponse.DistributionItem>();
        distributions = next;
        pulledAt = response != null && response.getPulledAt() != null ? response.getPulledAt() : "";
        restoreSelection(previousDistributionId, previousMediaIdentity);
        restoreBgmSelection(previousBgmIdentity);
    }

    public void setDisabledPlayback(Set<String> nextDisabledDistributionIds, Set<String> nextDisabledMediaIdentities) {
        long previousDistributionId = getCurrentDistribution() != null ? getCurrentDistribution().getId() : 0L;
        String previousMediaIdentity = resolveMediaIdentity(getCurrentMedia());
        String previousBgmIdentity = resolveBgmIdentity(getCurrentBgm());

        disabledDistributionIds = nextDisabledDistributionIds == null
                ? new HashSet<String>()
                : new HashSet<String>(nextDisabledDistributionIds);

        restoreSelection(previousDistributionId, previousMediaIdentity);
        restoreBgmSelection(previousBgmIdentity);
    }

    public List<DevicePullResponse.DistributionItem> getDistributions() {
        return distributions;
    }

    public String getPulledAt() {
        return pulledAt;
    }

    public DevicePullResponse.DistributionItem getCurrentDistribution() {
        List<DevicePullResponse.DistributionItem> enabled = getEnabledDistributions();
        if (enabled.isEmpty()) {
            return null;
        }
        if (currentDistributionIndex < 0 || currentDistributionIndex >= enabled.size()) {
            currentDistributionIndex = 0;
        }
        return enabled.get(currentDistributionIndex);
    }

    public List<DevicePullResponse.MediaItem> getCurrentMediaList() {
        DevicePullResponse.DistributionItem distribution = getCurrentDistribution();
        if (distribution == null) {
            return Collections.emptyList();
        }
        return getPlayableMediaList(distribution);
    }

    public DevicePullResponse.MediaItem getCurrentMedia() {
        List<DevicePullResponse.MediaItem> list = getCurrentMediaList();
        if (list.isEmpty()) {
            return null;
        }
        if (currentMediaIndex < 0 || currentMediaIndex >= list.size()) {
            currentMediaIndex = 0;
        }
        return list.get(currentMediaIndex);
    }

    public DevicePullResponse.MediaItem peekNextMedia() {
        DevicePullResponse.DistributionItem distribution = getCurrentDistribution();
        if (distribution == null) {
            return null;
        }
        List<DevicePullResponse.MediaItem> currentList = getCurrentMediaList();
        if (currentList.isEmpty()) {
            return null;
        }
        if (currentMediaIndex < currentList.size() - 1) {
            return currentList.get(currentMediaIndex + 1);
        }

        int nextDistributionIndex = getNextDistributionIndex();
        if (nextDistributionIndex < 0) {
            return null;
        }
        List<DevicePullResponse.DistributionItem> enabled = getEnabledDistributions();
        if (nextDistributionIndex >= enabled.size()) {
            return null;
        }
        List<DevicePullResponse.MediaItem> nextList = getPlayableMediaList(enabled.get(nextDistributionIndex));
        return nextList.isEmpty() ? null : nextList.get(0);
    }

    public void nextMedia() {
        DevicePullResponse.DistributionItem distribution = getCurrentDistribution();
        List<DevicePullResponse.MediaItem> list = getCurrentMediaList();
        if (distribution == null || list.isEmpty()) {
            currentDistributionIndex = 0;
            currentMediaIndex = 0;
            currentBgmIndex = 0;
            return;
        }

        if (currentMediaIndex < list.size() - 1) {
            currentMediaIndex += 1;
            return;
        }

        int nextDistributionIndex = getNextDistributionIndex();
        if (nextDistributionIndex >= 0 && nextDistributionIndex != currentDistributionIndex) {
            currentDistributionIndex = nextDistributionIndex;
            currentMediaIndex = 0;
            currentBgmIndex = 0;
            return;
        }

        currentMediaIndex = 0;
    }

    public void nextDistribution() {
        DevicePullResponse.DistributionItem distribution = getCurrentDistribution();
        if (distribution == null) {
            resetIndices();
            return;
        }
        int nextDistributionIndex = getNextDistributionIndex();
        if (nextDistributionIndex >= 0) {
            currentDistributionIndex = nextDistributionIndex;
        }
        currentMediaIndex = 0;
        currentBgmIndex = 0;
    }

    public int getCurrentItemDurationSeconds() {
        DevicePullResponse.MediaItem currentMedia = getCurrentMedia();
        if (currentMedia != null && currentMedia.getItemDuration() != null && currentMedia.getItemDuration() > 0) {
            return currentMedia.getItemDuration();
        }
        DevicePullResponse.DistributionItem distribution = getCurrentDistribution();
        if (distribution != null && distribution.getItemDuration() != null && distribution.getItemDuration() > 0) {
            return distribution.getItemDuration();
        }
        return 10;
    }

    public List<DevicePullResponse.BgmItem> getCurrentBgmList() {
        DevicePullResponse.DistributionItem distribution = getCurrentDistribution();
        if (distribution == null || distribution.getAlbum() == null || distribution.getAlbum().getBgmList() == null) {
            return Collections.emptyList();
        }
        List<DevicePullResponse.BgmItem> sorted = new ArrayList<>(distribution.getAlbum().getBgmList());
        Collections.sort(sorted, new Comparator<DevicePullResponse.BgmItem>() {
            @Override
            public int compare(DevicePullResponse.BgmItem left, DevicePullResponse.BgmItem right) {
                int leftSort = left.getSortOrder() == null ? 0 : left.getSortOrder();
                int rightSort = right.getSortOrder() == null ? 0 : right.getSortOrder();
                return leftSort - rightSort;
            }
        });
        return sorted;
    }

    public DevicePullResponse.BgmItem getCurrentBgm() {
        List<DevicePullResponse.BgmItem> list = getCurrentBgmList();
        if (list.isEmpty()) {
            return null;
        }
        if (currentBgmIndex < 0 || currentBgmIndex >= list.size()) {
            currentBgmIndex = 0;
        }
        return list.get(currentBgmIndex);
    }

    public String getCurrentBgmUrl() {
        DevicePullResponse.BgmItem bgm = getCurrentBgm();
        if (bgm != null && bgm.getUrl() != null && !bgm.getUrl().trim().isEmpty()) {
            return bgm.getUrl();
        }
        DevicePullResponse.DistributionItem distribution = getCurrentDistribution();
        if (distribution != null && distribution.getAlbum() != null) {
            return distribution.getAlbum().getBgmUrl();
        }
        return "";
    }

    public int getCurrentBgmVolume() {
        DevicePullResponse.DistributionItem distribution = getCurrentDistribution();
        if (distribution == null || distribution.getAlbum() == null || distribution.getAlbum().getBgmVolume() == null) {
            return 40;
        }
        return distribution.getAlbum().getBgmVolume();
    }

    public String getCurrentTransitionStyle() {
        DevicePullResponse.DistributionItem distribution = getCurrentDistribution();
        if (distribution == null || distribution.getAlbum() == null) {
            return "NONE";
        }
        return normalizeTransitionStyle(distribution.getAlbum().getTransitionStyle());
    }

    public String getCurrentDisplayStyle() {
        DevicePullResponse.DistributionItem distribution = getCurrentDistribution();
        if (distribution == null || distribution.getAlbum() == null) {
            return "SINGLE";
        }
        return normalizeDisplayStyle(distribution.getAlbum().getDisplayStyle());
    }

    public String getCurrentDisplayVariant() {
        DevicePullResponse.DistributionItem distribution = getCurrentDistribution();
        if (distribution == null || distribution.getAlbum() == null || distribution.getAlbum().getDisplayVariant() == null) {
            return "DEFAULT";
        }
        return distribution.getAlbum().getDisplayVariant().trim().toUpperCase(Locale.US).replace('-', '_');
    }

    public boolean isCurrentShowTimeAndDate() {
        DevicePullResponse.DistributionItem distribution = getCurrentDistribution();
        return distribution != null
                && distribution.getAlbum() != null
                && Boolean.TRUE.equals(distribution.getAlbum().getShowTimeAndDate());
    }

    public void nextBgm() {
        List<DevicePullResponse.BgmItem> list = getCurrentBgmList();
        if (list.isEmpty()) {
            currentBgmIndex = 0;
            return;
        }
        currentBgmIndex = (currentBgmIndex + 1) % list.size();
    }

    public boolean hasPlayableContent() {
        return !getEnabledDistributions().isEmpty();
    }

    public int getCurrentDistributionIndex() {
        return currentDistributionIndex;
    }

    private String normalizeTransitionStyle(String style) {
        if (style == null || style.trim().isEmpty()) {
            return "NONE";
        }
        String normalized = style.trim().toUpperCase(Locale.US);
        if ("NONE".equals(normalized)
                || "FADE".equals(normalized)
                || "SLIDE".equals(normalized)
                || "CUBE".equals(normalized)
                || "REVEAL".equals(normalized)
                || "FLIP".equals(normalized)
                || "BENTO".equals(normalized)
                || "FRAME_WALL".equals(normalized)
                || "FRAMEWALL".equals(normalized)
                || "CAROUSEL".equals(normalized)
                || "RANDOM".equals(normalized)) {
            return normalized;
        }
        return "NONE";
    }

    private String normalizeDisplayStyle(String style) {
        if (style == null || style.trim().isEmpty()) {
            return "SINGLE";
        }
        String normalized = style.trim().toUpperCase(Locale.US);
        if ("BENTO".equals(normalized)
                || "FRAME_WALL".equals(normalized)
                || "FRAMEWALL".equals(normalized)
                || "CAROUSEL".equals(normalized)
                || "CALENDAR".equals(normalized)) {
            return normalized;
        }
        return "SINGLE";
    }

    private List<DevicePullResponse.MediaItem> getPlayableMediaList(DevicePullResponse.DistributionItem distribution) {
        if (distribution == null || distribution.getMediaList() == null) {
            return Collections.emptyList();
        }
        List<DevicePullResponse.MediaItem> sorted = new ArrayList<>(distribution.getMediaList());
        Collections.sort(sorted, new Comparator<DevicePullResponse.MediaItem>() {
            @Override
            public int compare(DevicePullResponse.MediaItem left, DevicePullResponse.MediaItem right) {
                int leftSort = left.getSortOrder() == null ? 0 : left.getSortOrder();
                int rightSort = right.getSortOrder() == null ? 0 : right.getSortOrder();
                return leftSort - rightSort;
            }
        });
        if (Boolean.TRUE.equals(distribution.getShuffle())) {
            sorted = stableShuffle(sorted, distribution.getId());
        }
        return sorted;
    }

    private void restoreSelection(long previousDistributionId, String previousMediaIdentity) {
        List<DevicePullResponse.DistributionItem> enabled = getEnabledDistributions();
        if (enabled.isEmpty()) {
            resetIndices();
            return;
        }

        if (previousDistributionId <= 0L) {
            resetIndices();
            return;
        }
        for (int i = 0; i < enabled.size(); i += 1) {
            DevicePullResponse.DistributionItem distribution = enabled.get(i);
            if (distribution.getId() != previousDistributionId) {
                continue;
            }
            currentDistributionIndex = i;
            List<DevicePullResponse.MediaItem> list = getPlayableMediaList(distribution);
            currentMediaIndex = 0;
            for (int j = 0; j < list.size(); j += 1) {
                if (resolveMediaIdentity(list.get(j)).equals(previousMediaIdentity)) {
                    currentMediaIndex = j;
                    break;
                }
            }
            return;
        }
        resetIndices();
    }

    private void restoreBgmSelection(String previousBgmIdentity) {
        List<DevicePullResponse.BgmItem> list = getCurrentBgmList();
        if (list.isEmpty()) {
            currentBgmIndex = 0;
            return;
        }
        currentBgmIndex = 0;
        if (previousBgmIdentity == null || previousBgmIdentity.isEmpty()) {
            return;
        }
        for (int i = 0; i < list.size(); i += 1) {
            if (resolveBgmIdentity(list.get(i)).equals(previousBgmIdentity)) {
                currentBgmIndex = i;
                return;
            }
        }
    }

    private void resetIndices() {
        currentDistributionIndex = 0;
        currentMediaIndex = 0;
        currentBgmIndex = 0;
    }

    private List<DevicePullResponse.DistributionItem> getEnabledDistributions() {
        List<DevicePullResponse.DistributionItem> enabled = new ArrayList<DevicePullResponse.DistributionItem>();
        for (DevicePullResponse.DistributionItem distribution : distributions) {
            if (!isDistributionEnabled(distribution)) {
                continue;
            }
            if (!getPlayableMediaList(distribution).isEmpty()) {
                enabled.add(distribution);
            }
        }
        return enabled;
    }

    private boolean isDistributionEnabled(DevicePullResponse.DistributionItem distribution) {
        return distribution != null && !disabledDistributionIds.contains(String.valueOf(distribution.getId()));
    }

    private int getNextDistributionIndex() {
        List<DevicePullResponse.DistributionItem> enabled = getEnabledDistributions();
        if (enabled.isEmpty()) {
            return -1;
        }
        if (enabled.size() == 1) {
            return 0;
        }
        int nextIndex = currentDistributionIndex + 1;
        if (nextIndex >= enabled.size()) {
            nextIndex = 0;
        }
        return nextIndex;
    }

    private List<DevicePullResponse.MediaItem> stableShuffle(List<DevicePullResponse.MediaItem> input, long seedValue) {
        List<DevicePullResponse.MediaItem> output = new ArrayList<>(input);
        long seed = Math.abs(seedValue) == 0 ? 1 : Math.abs(seedValue);
        for (int i = output.size() - 1; i > 0; i -= 1) {
            seed = (seed * 1664525 + 1013904223) % 4294967296L;
            int j = (int) (seed % (i + 1));
            DevicePullResponse.MediaItem temp = output.get(i);
            output.set(i, output.get(j));
            output.set(j, temp);
        }
        return output;
    }

    public static String resolveMediaIdentity(DevicePullResponse.MediaItem media) {
        if (media == null) {
            return "";
        }
        if (media.getId() != null) {
            return "id:" + media.getId();
        }
        if (media.getExternalMediaKey() != null && !media.getExternalMediaKey().isEmpty()) {
            return "external:" + media.getExternalMediaKey();
        }
        if (media.getUrl() != null && !media.getUrl().isEmpty()) {
            return "url:" + media.getUrl();
        }
        return (media.getMediaType() == null ? "media" : media.getMediaType()) + ":" + (media.getFileName() == null ? "" : media.getFileName());
    }

    public static String resolveBgmIdentity(DevicePullResponse.BgmItem bgm) {
        if (bgm == null) {
            return "";
        }
        if (bgm.getMediaId() != null) {
            return "id:" + bgm.getMediaId();
        }
        if (bgm.getExternalMediaKey() != null && !bgm.getExternalMediaKey().isEmpty()) {
            return "external:" + bgm.getExternalMediaKey();
        }
        if (bgm.getUrl() != null && !bgm.getUrl().isEmpty()) {
            return "url:" + bgm.getUrl();
        }
        return bgm.getFileName() == null ? "" : bgm.getFileName();
    }
}
