package com.cloudalbum.publisher.media.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class MediaLibraryGroupsResponse {

    private List<SourceGroup> sourceGroups = new ArrayList<>();

    private List<FacetCount> mediaTypeGroups = new ArrayList<>();

    @Getter
    @Setter
    public static class SourceGroup {
        private String sourceType;
        private Long sourceId;
        private String sourceName;
        private Integer mediaCount = 0;
        private List<FolderGroup> folders = new ArrayList<>();
    }

    @Getter
    @Setter
    public static class FolderGroup {
        private String folderPath;
        private String title;
        private Integer mediaCount = 0;
    }

    @Getter
    @Setter
    public static class FacetCount {
        private String value;
        private String label;
        private Integer count = 0;
    }
}
