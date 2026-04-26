package com.cloudalbum.publisher.album.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AlbumCreateRequest {

    @NotBlank(message = "相册名称不能为空")
    @Size(max = 200, message = "相册名称最长200位")
    private String title;

    private String description;

    /** 播放转场样式，默认无转场 */
    private String transitionStyle = "NONE";

    /** 展示布局样式，默认单图 */
    private String displayStyle = "SINGLE";

    /** 展示布局子样式，默认自动 */
    private String displayVariant = "DEFAULT";

    /** 是否显示时间日期 */
    private Boolean showTimeAndDate = false;

    /** PUBLIC / PRIVATE / DEVICE_ONLY，默认PUBLIC */
    private String visibility = "PUBLIC";
}
