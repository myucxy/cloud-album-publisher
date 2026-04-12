package com.cloudalbum.publisher.distribution.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("t_distribution_device")
public class DistributionDevice {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long distributionId;

    private Long deviceId;

    private Long groupId;

    private LocalDateTime createdAt;
}
