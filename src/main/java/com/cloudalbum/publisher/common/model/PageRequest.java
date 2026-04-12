package com.cloudalbum.publisher.common.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PageRequest {

    @Min(value = 1, message = "页码最小为1")
    private int page = 1;

    @Min(value = 1, message = "每页最少1条")
    @Max(value = 100, message = "每页最多100条")
    private int size = 20;

    /** 排序字段（可选，由各模块自行校验合法值） */
    private String sortBy;

    /** 排序方向：asc / desc */
    private String sortDir = "desc";

    /** MyBatis-Plus offset */
    public long getOffset() {
        return (long) (page - 1) * size;
    }
}
