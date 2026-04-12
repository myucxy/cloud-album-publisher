package com.cloudalbum.publisher.media.content;

import com.cloudalbum.publisher.media.entity.Media;

public interface MediaContentResolver {

    boolean supports(Media media);

    MediaContentResource resolve(Media media, boolean thumbnail);
}
