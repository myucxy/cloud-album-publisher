package com.cloudalbum.publisher.review.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("t_review_setting")
public class ReviewSetting {

    @TableId
    private Long id;

    private Boolean autoApproveEnabled;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
