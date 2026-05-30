package com.cloudalbum.publisher.album.support;

import com.cloudalbum.publisher.common.enums.ResultCode;
import com.cloudalbum.publisher.common.exception.BusinessException;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Set;

public final class AlbumDisplaySettingsSupport {

    private static final Set<String> SUPPORTED_TRANSITION_STYLES = Set.of(
            "NONE", "FADE", "SLIDE", "CUBE", "REVEAL", "FLIP", "RANDOM");
    private static final Set<String> SUPPORTED_DISPLAY_STYLES = Set.of(
            "SINGLE", "BENTO", "FRAME_WALL", "CAROUSEL", "CALENDAR");
    private static final Set<String> SUPPORTED_FRAME_WALL_VARIANTS = Set.of(
            "FRAME_WALL_2", "FRAME_WALL_4", "FRAME_WALL_6", "FRAME_WALL_8");

    private AlbumDisplaySettingsSupport() {
    }

    public static String normalizeAlbumTransitionStyle(String transitionStyle) {
        String normalized = normalizeTransitionStyleValue(transitionStyle);
        return normalized == null ? "NONE" : normalized;
    }

    public static String normalizeDistributionTransitionStyle(String transitionStyle) {
        return normalizeTransitionStyleValue(transitionStyle);
    }

    public static String normalizeAlbumDisplayStyle(String displayStyle) {
        String normalized = normalizeDisplayStyleValue(displayStyle);
        return normalized == null ? "SINGLE" : normalized;
    }

    public static String normalizeDistributionDisplayStyle(String displayStyle) {
        return normalizeDisplayStyleValue(displayStyle);
    }

    public static String normalizeAlbumDisplayVariant(String displayVariant, String displayStyle) {
        String normalizedDisplayStyle = StringUtils.hasText(displayStyle) ? displayStyle : "SINGLE";
        String normalized = normalizeDisplayVariantValue(displayVariant, normalizedDisplayStyle);
        return normalized == null ? defaultDisplayVariant(normalizedDisplayStyle) : normalized;
    }

    public static String normalizeDistributionDisplayVariant(String displayVariant, String effectiveDisplayStyle) {
        return normalizeDisplayVariantValue(displayVariant, StringUtils.hasText(effectiveDisplayStyle) ? effectiveDisplayStyle : "SINGLE");
    }

    public static String resolveTransitionStyle(String transitionStyle) {
        return StringUtils.hasText(transitionStyle) ? transitionStyle : "NONE";
    }

    public static String resolveDisplayStyle(String displayStyle) {
        return StringUtils.hasText(displayStyle) ? displayStyle : "SINGLE";
    }

    public static String resolveDisplayVariant(String displayVariant) {
        return StringUtils.hasText(displayVariant) ? displayVariant : "DEFAULT";
    }

    private static String normalizeTransitionStyleValue(String transitionStyle) {
        if (!StringUtils.hasText(transitionStyle)) {
            return null;
        }
        String normalized = transitionStyle.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_TRANSITION_STYLES.contains(normalized)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不支持的播放转场样式");
        }
        return normalized;
    }

    private static String normalizeDisplayStyleValue(String displayStyle) {
        if (!StringUtils.hasText(displayStyle)) {
            return null;
        }
        String normalized = displayStyle.trim().toUpperCase(Locale.ROOT);
        if ("FRAMEWALL".equals(normalized)) {
            normalized = "FRAME_WALL";
        }
        if (!SUPPORTED_DISPLAY_STYLES.contains(normalized)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不支持的展示布局样式");
        }
        return normalized;
    }

    private static String normalizeDisplayVariantValue(String displayVariant, String displayStyle) {
        if (!StringUtils.hasText(displayVariant)) {
            return null;
        }
        String normalized = displayVariant.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if (!normalized.matches("[A-Z0-9_]{1,32}")) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不支持的展示布局子样式");
        }
        if ("FRAME_WALL".equals(displayStyle)) {
            if ("DEFAULT".equals(normalized)) {
                return "FRAME_WALL_8";
            }
            if (!SUPPORTED_FRAME_WALL_VARIANTS.contains(normalized)) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "不支持的相框墙子样式");
            }
        }
        return normalized;
    }

    private static String defaultDisplayVariant(String displayStyle) {
        return "FRAME_WALL".equals(displayStyle) ? "FRAME_WALL_8" : "DEFAULT";
    }
}
