package com.cloudalbum.publisher.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("t_role")
public class Role {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** ROLE_USER / ROLE_ADMIN */
    private String code;

    private String name;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
