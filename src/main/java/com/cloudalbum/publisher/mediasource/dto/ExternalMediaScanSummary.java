package com.cloudalbum.publisher.mediasource.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class ExternalMediaScanSummary {

    private Long sourceId;
    private String sourceType;
    private String sourceName;
    private String boundPath;
    private int mediaCount;
    private Map<String, Integer> mediaTypeCounts = new LinkedHashMap<>();
    private List<FolderSummary> folders = new ArrayList<>();
    private String warning;

    @Getter
    @Setter
    public static class FolderSummary {
        private String folderPath;
        private String title;
        private int mediaCount;
    }
}
