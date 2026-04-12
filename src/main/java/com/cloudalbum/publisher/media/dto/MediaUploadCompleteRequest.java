package com.cloudalbum.publisher.media.dto;

import jakarta.validation.Valid;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class MediaUploadCompleteRequest {

    @Valid
    private List<UploadPartRequestItem> parts;
}
