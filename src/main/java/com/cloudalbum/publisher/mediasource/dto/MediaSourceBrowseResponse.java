package com.cloudalbum.publisher.mediasource.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class MediaSourceBrowseResponse {

    private Long sourceId;
    private String sourceType;
    private String sourceName;
    private String rootPath;
    private String currentPath;
    private String boundPath;
    private String boundPathName;
    private List<MediaSourceBrowseItemResponse> items = new ArrayList<>();
}
