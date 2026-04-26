package com.cloudalbum.publisher.clientupdate.service;

import com.cloudalbum.publisher.clientupdate.dto.ClientDownloadListResponse;
import com.cloudalbum.publisher.clientupdate.dto.ClientUpdateResponse;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ClientUpdateService {

    private final ObjectMapper objectMapper;

    @Value("${release.manifest-path:./releases/manifest.json}")
    private String manifestPath;

    public ClientUpdateResponse checkUpdate(String platform, String currentVersion, Integer currentVersionCode, String channel) {
        String normalizedPlatform = normalize(platform, "pc");
        String normalizedChannel = normalize(channel, "stable");
        ManifestRelease release = findRelease(normalizedPlatform, normalizedChannel);

        ClientUpdateResponse response = new ClientUpdateResponse();
        response.setPlatform(normalizedPlatform);
        response.setChannel(normalizedChannel);
        response.setCurrentVersion(currentVersion);
        response.setCurrentVersionCode(currentVersionCode);
        response.setHasUpdate(false);
        response.setForceUpdate(false);

        if (release == null) {
            return response;
        }

        response.setLatestVersion(release.getVersion());
        response.setLatestVersionCode(release.getVersionCode());
        response.setHasUpdate(hasUpdate(release, currentVersion, currentVersionCode));
        response.setForceUpdate(Boolean.TRUE.equals(release.getForceUpdate()));
        response.setDownloadUrl(resolveDownloadUrl(release.getDownloadUrl()));
        response.setFileName(release.getFileName());
        response.setSha256(release.getSha256());
        response.setSize(release.getSize());
        response.setReleaseNotes(release.getReleaseNotes());
        response.setPublishedAt(release.getPublishedAt());
        return response;
    }

    public ClientDownloadListResponse listDownloads() {
        ClientDownloadListResponse response = new ClientDownloadListResponse();
        Manifest manifest = readManifest();
        if (manifest == null || manifest.getPlatforms() == null || manifest.getPlatforms().isEmpty()) {
            return response;
        }

        manifest.getPlatforms().entrySet().stream()
                .sorted(Comparator.comparing(entry -> platformSortKey(entry.getKey())))
                .forEach(platformEntry -> {
                    PlatformRelease platformRelease = platformEntry.getValue();
                    if (platformRelease == null || platformRelease.getChannels() == null || platformRelease.getChannels().isEmpty()) {
                        return;
                    }
                    ClientDownloadListResponse.PlatformDownloads platformDownloads = new ClientDownloadListResponse.PlatformDownloads();
                    platformDownloads.setPlatform(platformEntry.getKey());
                    platformRelease.getChannels().entrySet().stream()
                            .sorted(Comparator.comparing(entry -> channelSortKey(entry.getKey())))
                            .forEach(channelEntry -> platformDownloads.getChannels().add(toDownloadItem(channelEntry.getKey(), channelEntry.getValue())));
                    if (!platformDownloads.getChannels().isEmpty()) {
                        response.getPlatforms().add(platformDownloads);
                    }
                });
        return response;
    }

    private ClientDownloadListResponse.DownloadItem toDownloadItem(String channel, ManifestRelease release) {
        ClientDownloadListResponse.DownloadItem item = new ClientDownloadListResponse.DownloadItem();
        item.setChannel(channel);
        if (release == null) {
            return item;
        }
        item.setVersion(release.getVersion());
        item.setVersionCode(release.getVersionCode());
        item.setForceUpdate(Boolean.TRUE.equals(release.getForceUpdate()));
        item.setDownloadUrl(resolveDownloadUrl(release.getDownloadUrl()));
        item.setFileName(release.getFileName());
        item.setSha256(release.getSha256());
        item.setSize(release.getSize());
        item.setReleaseNotes(release.getReleaseNotes());
        item.setPublishedAt(release.getPublishedAt());
        return item;
    }

    private String platformSortKey(String platform) {
        String normalized = normalize(platform, "");
        if ("pc".equals(normalized)) {
            return "0";
        }
        if ("android".equals(normalized)) {
            return "1";
        }
        return "2" + normalized;
    }

    private String channelSortKey(String channel) {
        String normalized = normalize(channel, "");
        return "stable".equals(normalized) ? "0" : "1" + normalized;
    }

    private ManifestRelease findRelease(String platform, String channel) {
        Manifest manifest = readManifest();
        if (manifest == null || manifest.getPlatforms() == null) {
            return null;
        }
        PlatformRelease platformRelease = manifest.getPlatforms().get(platform);
        if (platformRelease == null || platformRelease.getChannels() == null) {
            return null;
        }
        ManifestRelease release = platformRelease.getChannels().get(channel);
        if (release != null) {
            return release;
        }
        return platformRelease.getChannels().get("stable");
    }

    private Manifest readManifest() {
        Path path = Path.of(manifestPath);
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try {
            return objectMapper.readValue(path.toFile(), Manifest.class);
        } catch (IOException ignored) {
            return null;
        }
    }

    private boolean hasUpdate(ManifestRelease release, String currentVersion, Integer currentVersionCode) {
        Integer latestCode = release.getVersionCode();
        if (latestCode != null && currentVersionCode != null) {
            return latestCode > currentVersionCode;
        }
        return compareVersion(release.getVersion(), currentVersion) > 0;
    }

    private int compareVersion(String latestVersion, String currentVersion) {
        List<Integer> latest = parseVersion(latestVersion);
        List<Integer> current = parseVersion(currentVersion);
        int max = Math.max(latest.size(), current.size());
        for (int i = 0; i < max; i++) {
            int left = i < latest.size() ? latest.get(i) : 0;
            int right = i < current.size() ? current.get(i) : 0;
            if (left != right) {
                return Integer.compare(left, right);
            }
        }
        return 0;
    }

    private List<Integer> parseVersion(String value) {
        if (!StringUtils.hasText(value)) {
            return Collections.emptyList();
        }
        String normalized = value.trim();
        int suffixIndex = normalized.indexOf('-');
        if (suffixIndex >= 0) {
            normalized = normalized.substring(0, suffixIndex);
        }
        String[] segments = normalized.split("\\.");
        return java.util.Arrays.stream(segments)
                .map(this::parseVersionSegment)
                .toList();
    }

    private Integer parseVersionSegment(String value) {
        String digits = value == null ? "" : value.replaceAll("[^0-9]", "");
        if (!StringUtils.hasText(digits)) {
            return 0;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private String resolveDownloadUrl(String downloadUrl) {
        if (!StringUtils.hasText(downloadUrl)) {
            return null;
        }
        String trimmed = downloadUrl.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .path(trimmed.startsWith("/") ? trimmed : "/" + trimmed)
                .toUriString();
    }

    private String normalize(String value, String fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Manifest {
        private Map<String, PlatformRelease> platforms = new HashMap<>();
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class PlatformRelease {
        private Map<String, ManifestRelease> channels = new HashMap<>();
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ManifestRelease {
        private String version;
        private Integer versionCode;
        private Boolean forceUpdate;
        private String downloadUrl;
        private String fileName;
        private String sha256;
        private Long size;
        private String releaseNotes;
        private String publishedAt;
    }
}
