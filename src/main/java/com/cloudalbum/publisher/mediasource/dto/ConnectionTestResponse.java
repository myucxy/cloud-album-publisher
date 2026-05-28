package com.cloudalbum.publisher.mediasource.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConnectionTestResponse {

    private boolean success;

    private String message;

    private Long latencyMs;
}
