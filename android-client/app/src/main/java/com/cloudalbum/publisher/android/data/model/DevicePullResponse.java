package com.cloudalbum.publisher.android.data.model;

import java.util.List;

public class DevicePullResponse {
    private DeviceResponse device;
    private List<DistributionItem> distributions;
    private String pulledAt;

    public DeviceResponse getDevice() {
        return device;
    }

    public List<DistributionItem> getDistributions() {
        return distributions;
    }

    public String getPulledAt() {
        return pulledAt;
    }

    public static class DistributionItem {
        private long id;
        private String name;
        private String status;
        private Boolean loopPlay;
        private Boolean shuffle;
        private Integer itemDuration;
        private AlbumItem album;
        private List<MediaItem> mediaList;

        public long getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getStatus() {
            return status;
        }

        public Boolean getLoopPlay() {
            return loopPlay;
        }

        public Boolean getShuffle() {
            return shuffle;
        }

        public Integer getItemDuration() {
            return itemDuration;
        }

        public AlbumItem getAlbum() {
            return album;
        }

        public List<MediaItem> getMediaList() {
            return mediaList;
        }
    }

    public static class AlbumItem {
        private long id;
        private String title;
        private String description;
        private String coverUrl;
        private String bgmUrl;
        private List<BgmItem> bgmList;
        private Integer bgmVolume;
        private String transitionStyle;
        private String displayStyle;
        private String visibility;

        public long getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        public String getDescription() {
            return description;
        }

        public String getCoverUrl() {
            return coverUrl;
        }

        public String getBgmUrl() {
            return bgmUrl;
        }

        public List<BgmItem> getBgmList() {
            return bgmList;
        }

        public Integer getBgmVolume() {
            return bgmVolume;
        }

        public String getTransitionStyle() {
            return transitionStyle;
        }

        public String getDisplayStyle() {
            return displayStyle;
        }

        public String getVisibility() {
            return visibility;
        }
    }

    public static class BgmItem {
        private long id;
        private Long mediaId;
        private String externalMediaKey;
        private Long sourceId;
        private String sourceType;
        private String fileName;
        private String mediaType;
        private String contentType;
        private String url;
        private Integer sortOrder;

        public long getId() {
            return id;
        }

        public Long getMediaId() {
            return mediaId;
        }

        public String getExternalMediaKey() {
            return externalMediaKey;
        }

        public Long getSourceId() {
            return sourceId;
        }

        public String getSourceType() {
            return sourceType;
        }

        public String getFileName() {
            return fileName;
        }

        public String getMediaType() {
            return mediaType;
        }

        public String getContentType() {
            return contentType;
        }

        public String getUrl() {
            return url;
        }

        public Integer getSortOrder() {
            return sortOrder;
        }
    }

    public static class MediaItem {
        private Long id;
        private String externalMediaKey;
        private Long sourceId;
        private String sourceType;
        private String fileName;
        private String mediaType;
        private String contentType;
        private String url;
        private String thumbnailUrl;
        private Integer durationSec;
        private Integer width;
        private Integer height;
        private Integer sortOrder;
        private Integer itemDuration;

        public Long getId() {
            return id;
        }

        public String getExternalMediaKey() {
            return externalMediaKey;
        }

        public Long getSourceId() {
            return sourceId;
        }

        public String getSourceType() {
            return sourceType;
        }

        public String getFileName() {
            return fileName;
        }

        public String getMediaType() {
            return mediaType;
        }

        public String getContentType() {
            return contentType;
        }

        public String getUrl() {
            return url;
        }

        public String getThumbnailUrl() {
            return thumbnailUrl;
        }

        public Integer getDurationSec() {
            return durationSec;
        }

        public Integer getWidth() {
            return width;
        }

        public Integer getHeight() {
            return height;
        }

        public Integer getSortOrder() {
            return sortOrder;
        }

        public Integer getItemDuration() {
            return itemDuration;
        }
    }
}
