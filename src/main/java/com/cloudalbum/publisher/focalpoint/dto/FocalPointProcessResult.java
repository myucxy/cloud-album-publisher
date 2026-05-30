package com.cloudalbum.publisher.focalpoint.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class FocalPointProcessResult {

    private int totalItems;
    private int processedItems;
    private int skippedItems;
    private int failedItems;
    private List<String> errors = new ArrayList<>();
}
