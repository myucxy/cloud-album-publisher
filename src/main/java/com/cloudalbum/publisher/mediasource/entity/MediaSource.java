package com.cloudalbum.publisher.mediasource.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("t_media_source")
public class MediaSource {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String name;

    private String sourceType;

    private String configJson;

    private String credentialCiphertext;

    private String boundPath;

    private String boundPathName;

    private String host;

    private Integer port;

    private String shareName;

    private String rootPath;

    private String username;

    private String passwordCiphertext;

    private Boolean enabled;

    private LocalDateTime lastScanAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
