package com.cloudalbum.publisher.focalpoint.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AlbumFocalPointSettingsRequest {

    private Boolean focalPointEnabled;

    private String focalPointProvider;
}
