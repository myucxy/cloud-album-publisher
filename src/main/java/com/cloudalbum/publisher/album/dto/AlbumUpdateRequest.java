package com.cloudalbum.publisher.album.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AlbumUpdateRequest {

    @Size(max = 200, message = "相册名称最长200位")
    private String title;

    private String description;

    private String transitionStyle;

    private String visibility;

    private String status;

    private Integer sortOrder;
}
