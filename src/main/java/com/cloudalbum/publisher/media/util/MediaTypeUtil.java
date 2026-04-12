package com.cloudalbum.publisher.media.util;

import com.cloudalbum.publisher.common.enums.MediaType;
import org.springframework.util.StringUtils;

import java.util.Locale;

public final class MediaTypeUtil {

    private MediaTypeUtil() {}

    public static MediaType detect(String contentType, String fileName) {
        if (StringUtils.hasText(contentType)) {
            if (contentType.startsWith("image/")) return MediaType.IMAGE;
            if (contentType.startsWith("video/")) return MediaType.VIDEO;
            if (contentType.startsWith("audio/")) return MediaType.AUDIO;
        }
        String ext = extension(fileName);
        if ("jpg".equals(ext) || "jpeg".equals(ext) || "png".equals(ext) || "webp".equals(ext)) return MediaType.IMAGE;
        if ("mp4".equals(ext) || "mov".equals(ext) || "avi".equals(ext) || "mkv".equals(ext)) return MediaType.VIDEO;
        if ("mp3".equals(ext) || "wav".equals(ext) || "flac".equals(ext) || "aac".equals(ext)) return MediaType.AUDIO;
        return MediaType.OTHER;
    }

    private static String extension(String fileName) {
        if (!StringUtils.hasText(fileName) || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }
}
