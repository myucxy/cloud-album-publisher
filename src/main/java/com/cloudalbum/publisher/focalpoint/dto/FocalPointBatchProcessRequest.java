package com.cloudalbum.publisher.focalpoint.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class FocalPointBatchProcessRequest {

    private String providerType;

    private List<Long> contentIds;

    private Long visionLlmConfigId;
}
