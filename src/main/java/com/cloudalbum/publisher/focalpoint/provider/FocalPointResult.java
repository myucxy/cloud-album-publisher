package com.cloudalbum.publisher.focalpoint.provider;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class FocalPointResult {
    private final double x;
    private final double y;
    private final double confidence;
    private final String regionType;
    private final double regionWidth;
    private final double regionHeight;
}
