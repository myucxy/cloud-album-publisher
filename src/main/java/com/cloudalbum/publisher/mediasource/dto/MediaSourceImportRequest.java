package com.cloudalbum.publisher.mediasource.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class MediaSourceImportRequest {

    private List<String> paths;

    private String directoryPath;
}
