package com.cloudalbum.publisher.media.content;

import com.cloudalbum.publisher.common.enums.ResultCode;
import com.cloudalbum.publisher.common.exception.BusinessException;
import com.cloudalbum.publisher.media.entity.Media;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class MediaContentResolverRegistry {

    private final List<MediaContentResolver> resolvers;

    public MediaContentResource resolve(Media media, boolean thumbnail) {
        return resolvers.stream()
                .filter(resolver -> resolver.supports(media))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ResultCode.INTERNAL_ERROR, "未找到可用的媒体内容解析器"))
                .resolve(media, thumbnail);
    }
}
