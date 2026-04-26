package com.cloudalbum.publisher.album.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("t_album")
public class Album {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String title;

    private String description;

    private String coverUrl;

    private Long coverMediaId;

    private Long coverSourceId;

    private String coverSourceType;

    private String coverSourceName;

    private String coverExternalMediaKey;

    private String coverPath;

    private String coverFileName;

    private String coverContentType;

    private String coverMediaType;

    private String bgmUrl;

    private Long bgmMediaId;

    private Long bgmSourceId;

    private String bgmSourceType;

    private String bgmSourceName;

    private String bgmExternalMediaKey;

    private String bgmPath;

    private String bgmFileName;

    private String bgmContentType;

    private String bgmMediaType;

    /** 音量 0-100 */
    private Integer bgmVolume;

    /** 播放转场样式 */
    private String transitionStyle;

    /** 展示布局样式 */
    private String displayStyle;

    /** 展示布局子样式 */
    private String displayVariant;

    /** 是否显示时间日期 */
    private Boolean showTimeAndDate;

    /** PUBLIC / PRIVATE / DEVICE_ONLY */
    private String visibility;

    /** DRAFT / PUBLISHED */
    private String status;

    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
