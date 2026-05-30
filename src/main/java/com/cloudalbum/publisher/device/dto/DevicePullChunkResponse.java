package com.cloudalbum.publisher.device.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "设备分批拉取内容响应")
public class DevicePullChunkResponse extends DevicePullResponse {

    @Schema(description = "本次全量拉取的快照标识")
    private String snapshotId;

    @Schema(description = "下一批游标，首次请求为空")
    private String cursor;

    @Schema(description = "是否还有下一批")
    private boolean hasMore;

    @Schema(description = "是否为最后一批")
    private boolean finalChunk;

    @Schema(description = "已下发的分发数量")
    private int returnedDistributionCount;

    @Schema(description = "已下发的媒体数量")
    private int returnedMediaCount;
}
