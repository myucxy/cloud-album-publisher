package com.cloudalbum.publisher.album.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("t_album_media")
public class AlbumMedia {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long albumId;

    /** 关联 t_media，由模块B维护 */
    private Long mediaId;

    private Long sourceId;

    private String sourceType;

    private String sourceName;

    private String externalMediaKey;

    private String filePath;

    private String fileName;

    private String contentType;

    private String mediaType;

    private Integer sortOrder;

    /** 展示时长（秒） */
    private Integer duration;

    /** 焦点 X 坐标 (0.0-1.0) */
    private Double focalPointX;

    /** 焦点 Y 坐标 (0.0-1.0) */
    private Double focalPointY;

    /** 焦点检测 Provider */
    private String focalPointProvider;

    /** 焦点检测置信度 (0.0-1.0) */
    private Double focalPointConfidence;

    /** 焦点区域类型: FACE / SALIENCY / CENTER */
    private String focalPointRegionType;

    /** 焦点区域宽度 (0.0-1.0) */
    private Double focalPointRegionWidth;

    /** 焦点区域高度 (0.0-1.0) */
    private Double focalPointRegionHeight;

    /** 焦点最后更新时间 */
    private LocalDateTime focalPointUpdatedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public boolean isExternal() {
        return StringUtils.hasText(externalMediaKey);
    }

    public boolean isInternal() {
        return mediaId != null;
    }
}
