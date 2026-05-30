package com.cloudalbum.publisher.focalpoint.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FocalPointUpdateRequest {

    @DecimalMin("0.0") @DecimalMax("1.0")
    private double x;

    @DecimalMin("0.0") @DecimalMax("1.0")
    private double y;
}
