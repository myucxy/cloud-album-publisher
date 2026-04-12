package com.cloudalbum.publisher.media.task;

import com.cloudalbum.publisher.common.enums.MediaStatus;
import com.cloudalbum.publisher.common.enums.MediaType;
import com.cloudalbum.publisher.common.enums.ResultCode;
import com.cloudalbum.publisher.common.enums.TaskStatus;
import com.cloudalbum.publisher.common.exception.BusinessException;
import com.cloudalbum.publisher.media.entity.Media;
import com.cloudalbum.publisher.media.entity.MediaProcessTask;
import com.cloudalbum.publisher.media.mapper.MediaMapper;
import com.cloudalbum.publisher.media.mapper.MediaProcessTaskMapper;
import com.cloudalbum.publisher.review.service.ReviewService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TIFF;
import org.apache.tika.metadata.XMPDM;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.xml.sax.ContentHandler;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MediaTaskExecutor {

    private static final int IMAGE_THUMB_MAX_EDGE = 480;
    private static final String VIDEO_TRANSCODE_CONTENT_TYPE = "video/mp4";
    private static final String AUDIO_TRANSCODE_CONTENT_TYPE = "audio/mp4";

    private final MediaProcessTaskMapper mediaProcessTaskMapper;
    private final MediaMapper mediaMapper;
    private final MinioClient minioClient;
    private final ReviewService reviewService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${media.processing.ffmpeg-path:ffmpeg}")
    private String ffmpegPath;

    @Value("${media.processing.ffprobe-path:ffprobe}")
    private String ffprobePath;

    @Value("${media.processing.video-cover-second:1}")
    private int videoCoverSecond;

    public void executeTask(Long taskId) {
        MediaProcessTask task = mediaProcessTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ResultCode.TASK_NOT_FOUND);
        }
        if (!TaskStatus.PENDING.name().equals(task.getStatus())
                && !TaskStatus.RETRY_WAIT.name().equals(task.getStatus())) {
            return;
        }

        Media media = mediaMapper.selectById(task.getMediaId());
        if (media == null) {
            task.setStatus(TaskStatus.FAILED.name());
            task.setFinishedAt(LocalDateTime.now());
            task.setErrorMessage("media not found");
            mediaProcessTaskMapper.updateById(task);
            return;
        }

        task.setStatus(TaskStatus.RUNNING.name());
        task.setStartedAt(LocalDateTime.now());
        task.setErrorMessage(null);
        mediaProcessTaskMapper.updateById(task);

        media.setStatus(MediaStatus.PROCESSING.name());
        media.setErrorMessage(null);
        mediaMapper.updateById(media);

        try {
            processMedia(media);

            media.setStatus(MediaStatus.READY.name());
            media.setErrorMessage(null);
            mediaMapper.updateById(media);
            reviewService.submitReview(media.getId(), media.getUserId());

            task.setStatus(TaskStatus.SUCCESS.name());
            task.setFinishedAt(LocalDateTime.now());
            mediaProcessTaskMapper.updateById(task);
        } catch (Exception ex) {
            log.error("Execute media task failed, taskId={}, mediaId={}", task.getId(), media.getId(), ex);
            int nextRetry = (task.getRetryCount() == null ? 0 : task.getRetryCount()) + 1;
            int maxRetry = task.getMaxRetry() == null ? 3 : task.getMaxRetry();
            task.setRetryCount(nextRetry);
            task.setErrorMessage(ex.getMessage());
            if (nextRetry >= maxRetry) {
                task.setStatus(TaskStatus.FAILED.name());
                task.setFinishedAt(LocalDateTime.now());
                mediaProcessTaskMapper.updateById(task);

                media.setStatus(MediaStatus.FAILED.name());
                media.setErrorMessage(ex.getMessage());
                mediaMapper.updateById(media);
            } else {
                task.setStatus(TaskStatus.RETRY_WAIT.name());
                task.setNextRunAt(LocalDateTime.now().plusMinutes(backoffMinutes(nextRetry)));
                mediaProcessTaskMapper.updateById(task);
            }
        }
    }

    private void processMedia(Media media) throws Exception {
        MediaType type = MediaType.valueOf(media.getMediaType());
        switch (type) {
            case IMAGE -> processImage(media);
            case VIDEO -> processVideo(media);
            case AUDIO -> processAudio(media);
            case OTHER -> {
            }
        }
    }

    private void processImage(Media media) throws Exception {
        try (InputStream objectStream = minioClient.getObject(
                GetObjectArgs.builder().bucket(media.getBucketName()).object(media.getObjectKey()).build())) {
            BufferedImage source = ImageIO.read(objectStream);
            if (source == null) {
                throw new IllegalStateException("无法读取图片内容");
            }

            media.setWidth(source.getWidth());
            media.setHeight(source.getHeight());

            byte[] thumbnailBytes = buildThumbnailBytes(source);
            String thumbnailKey = media.getObjectKey() + ".thumb.jpg";
            uploadBinary(media, thumbnailKey, thumbnailBytes, "image/jpeg");
            media.setThumbnailKey(thumbnailKey);
        }
    }

    private void processVideo(Media media) throws Exception {
        Path sourcePath = null;
        Path transcodedPath = null;
        Path coverPath = null;
        try {
            sourcePath = downloadToTempFile(media, resolveTempSuffix(media.getFileName(), ".video"));
            applyProbeInfo(media, probeMedia(sourcePath));
            fillMissingStructuredMetadata(media);

            transcodedPath = Files.createTempFile("media-video-transcoded-", ".mp4");
            if (transcodeVideo(sourcePath, transcodedPath)) {
                replaceObject(media, transcodedPath, VIDEO_TRANSCODE_CONTENT_TYPE);
            }

            coverPath = Files.createTempFile("media-video-cover-", ".jpg");
            if (extractVideoCover(transcodedPath != null && Files.size(transcodedPath) > 0 ? transcodedPath : sourcePath, coverPath)) {
                String thumbnailKey = media.getObjectKey() + ".thumb.jpg";
                uploadFile(media, thumbnailKey, coverPath, "image/jpeg");
                media.setThumbnailKey(thumbnailKey);
            }
        } finally {
            deleteQuietly(sourcePath);
            deleteQuietly(transcodedPath);
            deleteQuietly(coverPath);
        }
    }

    private void processAudio(Media media) throws Exception {
        Path sourcePath = null;
        Path transcodedPath = null;
        Path coverPath = null;
        try {
            sourcePath = downloadToTempFile(media, resolveTempSuffix(media.getFileName(), ".audio"));
            applyProbeInfo(media, probeMedia(sourcePath));
            fillMissingStructuredMetadata(media);

            transcodedPath = Files.createTempFile("media-audio-transcoded-", ".m4a");
            if (transcodeAudio(sourcePath, transcodedPath)) {
                replaceObject(media, transcodedPath, AUDIO_TRANSCODE_CONTENT_TYPE);
            }

            coverPath = Files.createTempFile("media-audio-cover-", ".jpg");
            if (extractAudioCover(sourcePath, coverPath)) {
                String thumbnailKey = media.getObjectKey() + ".thumb.jpg";
                uploadFile(media, thumbnailKey, coverPath, "image/jpeg");
                media.setThumbnailKey(thumbnailKey);
            }
        } finally {
            deleteQuietly(sourcePath);
            deleteQuietly(transcodedPath);
            deleteQuietly(coverPath);
        }
    }

    private void fillMissingStructuredMetadata(Media media) throws Exception {
        if ((media.getDurationSec() != null && media.getDurationSec() > 0)
                && ((media.getWidth() != null && media.getWidth() > 0) || MediaType.AUDIO.name().equals(media.getMediaType()))) {
            return;
        }
        extractStructuredMetadata(media);
    }

    private void extractStructuredMetadata(Media media) throws Exception {
        Metadata metadata = new Metadata();
        AutoDetectParser parser = new AutoDetectParser();
        ContentHandler handler = new BodyContentHandler(-1);
        ParseContext context = new ParseContext();

        try (InputStream objectStream = minioClient.getObject(
                GetObjectArgs.builder().bucket(media.getBucketName()).object(media.getObjectKey()).build())) {
            parser.parse(objectStream, handler, metadata, context);
        }

        Integer width = parseInteger(metadata.get(TIFF.IMAGE_WIDTH));
        Integer height = parseInteger(metadata.get(TIFF.IMAGE_LENGTH));
        if (width == null) {
            width = parseInteger(metadata.get("width"));
        }
        if (height == null) {
            height = parseInteger(metadata.get("height"));
        }
        if (width != null && width > 0) {
            media.setWidth(width);
        }
        if (height != null && height > 0) {
            media.setHeight(height);
        }

        Integer durationSec = parseDurationSeconds(metadata);
        if (durationSec != null && durationSec > 0) {
            media.setDurationSec(durationSec);
        }
    }

    private MediaProbeInfo probeMedia(Path sourcePath) {
        CommandResult result = runCommand(List.of(
                ffprobePath,
                "-v", "error",
                "-show_entries", "stream=codec_type,width,height:format=duration",
                "-of", "json",
                sourcePath.toString()));
        if (!result.success() || result.output().isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(result.output());
            Integer durationSec = parseDecimalSeconds(root.path("format").path("duration").asText(null));
            Integer width = null;
            Integer height = null;
            for (JsonNode stream : root.path("streams")) {
                if ("video".equalsIgnoreCase(stream.path("codec_type").asText())) {
                    if (stream.hasNonNull("width") && stream.get("width").asInt() > 0) {
                        width = stream.get("width").asInt();
                    }
                    if (stream.hasNonNull("height") && stream.get("height").asInt() > 0) {
                        height = stream.get("height").asInt();
                    }
                    break;
                }
            }
            return new MediaProbeInfo(width, height, durationSec);
        } catch (Exception ex) {
            log.warn("Parse ffprobe output failed, sourcePath={}, output={}", sourcePath, shorten(result.output()), ex);
            return null;
        }
    }

    private void applyProbeInfo(Media media, MediaProbeInfo probeInfo) {
        if (probeInfo == null) {
            return;
        }
        if (probeInfo.width() != null && probeInfo.width() > 0) {
            media.setWidth(probeInfo.width());
        }
        if (probeInfo.height() != null && probeInfo.height() > 0) {
            media.setHeight(probeInfo.height());
        }
        if (probeInfo.durationSec() != null && probeInfo.durationSec() > 0) {
            media.setDurationSec(probeInfo.durationSec());
        }
    }

    private boolean transcodeVideo(Path sourcePath, Path outputPath) {
        CommandResult result = runCommand(List.of(
                ffmpegPath,
                "-y",
                "-i", sourcePath.toString(),
                "-map", "0:v:0",
                "-map", "0:a?",
                "-c:v", "libx264",
                "-preset", "veryfast",
                "-pix_fmt", "yuv420p",
                "-movflags", "+faststart",
                "-c:a", "aac",
                "-b:a", "192k",
                outputPath.toString()));
        if (result.success() && fileHasContent(outputPath)) {
            return true;
        }
        log.warn("Video transcode skipped, sourcePath={}, reason={}", sourcePath, shorten(result.output()));
        return false;
    }

    private boolean transcodeAudio(Path sourcePath, Path outputPath) {
        CommandResult result = runCommand(List.of(
                ffmpegPath,
                "-y",
                "-i", sourcePath.toString(),
                "-vn",
                "-c:a", "aac",
                "-b:a", "192k",
                "-movflags", "+faststart",
                outputPath.toString()));
        if (result.success() && fileHasContent(outputPath)) {
            return true;
        }
        log.warn("Audio transcode skipped, sourcePath={}, reason={}", sourcePath, shorten(result.output()));
        return false;
    }

    private boolean extractVideoCover(Path sourcePath, Path outputPath) {
        CommandResult result = runCommand(List.of(
                ffmpegPath,
                "-y",
                "-ss", String.valueOf(Math.max(videoCoverSecond, 0)),
                "-i", sourcePath.toString(),
                "-frames:v", "1",
                outputPath.toString()));
        if (result.success() && fileHasContent(outputPath)) {
            return true;
        }
        log.warn("Video cover extraction skipped, sourcePath={}, reason={}", sourcePath, shorten(result.output()));
        return false;
    }

    private boolean extractAudioCover(Path sourcePath, Path outputPath) {
        CommandResult result = runCommand(List.of(
                ffmpegPath,
                "-y",
                "-i", sourcePath.toString(),
                "-map", "0:v:0?",
                "-frames:v", "1",
                outputPath.toString()));
        if (result.success() && fileHasContent(outputPath)) {
            return true;
        }
        return false;
    }

    private CommandResult runCommand(List<String> command) {
        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            String output;
            try (InputStream stream = process.getInputStream()) {
                output = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
            int exitCode = process.waitFor();
            return new CommandResult(exitCode == 0, output);
        } catch (IOException ex) {
            return new CommandResult(false, ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new CommandResult(false, ex.getMessage());
        }
    }

    private Path downloadToTempFile(Media media, String suffix) throws Exception {
        Path tempFile = Files.createTempFile("media-source-", suffix);
        try (InputStream objectStream = minioClient.getObject(
                GetObjectArgs.builder().bucket(media.getBucketName()).object(media.getObjectKey()).build())) {
            Files.copy(objectStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
        }
        return tempFile;
    }

    private void replaceObject(Media media, Path file, String contentType) throws Exception {
        uploadFile(media, media.getObjectKey(), file, contentType);
        media.setContentType(contentType);
        media.setFileSize(Files.size(file));
    }

    private void uploadFile(Media media, String objectKey, Path file, String contentType) throws Exception {
        try (InputStream stream = Files.newInputStream(file)) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(media.getBucketName())
                            .object(objectKey)
                            .contentType(contentType)
                            .stream(stream, Files.size(file), -1)
                            .build()
            );
        }
    }

    private Integer parseDurationSeconds(Metadata metadata) {
        Integer fromXmpdm = parseDecimalSeconds(metadata.get(XMPDM.DURATION));
        if (fromXmpdm != null) {
            return fromXmpdm;
        }
        Integer fromGenericDuration = parseDecimalSeconds(metadata.get("duration"));
        if (fromGenericDuration != null) {
            return fromGenericDuration;
        }
        Integer fromMillis = parseMilliseconds(metadata.get("xmpDM:duration"));
        if (fromMillis != null) {
            return fromMillis;
        }
        return parseMilliseconds(metadata.get("meta:duration"));
    }

    private Integer parseInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String digits = value.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Integer parseDecimalSeconds(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.replaceAll("[^0-9.]", "");
        if (normalized.isBlank()) {
            return null;
        }
        try {
            return BigDecimal.valueOf(Double.parseDouble(normalized))
                    .setScale(0, RoundingMode.HALF_UP)
                    .intValue();
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Integer parseMilliseconds(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.replaceAll("[^0-9.]", "");
        if (normalized.isBlank()) {
            return null;
        }
        try {
            BigDecimal milliseconds = BigDecimal.valueOf(Double.parseDouble(normalized));
            return milliseconds.divide(BigDecimal.valueOf(1000), 0, RoundingMode.HALF_UP).intValue();
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private byte[] buildThumbnailBytes(BufferedImage source) throws Exception {
        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();
        int longestEdge = Math.max(sourceWidth, sourceHeight);
        double scale = longestEdge > IMAGE_THUMB_MAX_EDGE
                ? (double) IMAGE_THUMB_MAX_EDGE / longestEdge
                : 1D;

        int targetWidth = Math.max(1, (int) Math.round(sourceWidth * scale));
        int targetHeight = Math.max(1, (int) Math.round(sourceHeight * scale));

        BufferedImage thumbnail = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = thumbnail.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, targetWidth, targetHeight);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            if (!ImageIO.write(thumbnail, "jpg", outputStream)) {
                throw new IllegalStateException("无法生成图片缩略图");
            }
            return outputStream.toByteArray();
        }
    }

    private void uploadBinary(Media media, String objectKey, byte[] data, String contentType) throws Exception {
        try (ByteArrayInputStream stream = new ByteArrayInputStream(data)) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(media.getBucketName())
                            .object(objectKey)
                            .contentType(contentType)
                            .stream(stream, data.length, -1)
                            .build()
            );
        }
    }

    private String resolveTempSuffix(String fileName, String defaultSuffix) {
        if (fileName == null || fileName.isBlank()) {
            return defaultSuffix;
        }
        int index = fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            return defaultSuffix;
        }
        String suffix = fileName.substring(index);
        return suffix.length() <= 12 ? suffix : defaultSuffix;
    }

    private boolean fileHasContent(Path path) {
        try {
            return path != null && Files.exists(path) && Files.size(path) > 0;
        } catch (IOException ex) {
            return false;
        }
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            log.debug("Delete temp file failed, path={}", path, ex);
        }
    }

    private String shorten(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.length() > 300 ? text.substring(0, 300) + "..." : text;
    }

    private long backoffMinutes(int retry) {
        return Math.min(30L, (long) Math.pow(2, retry - 1));
    }

    private record MediaProbeInfo(Integer width, Integer height, Integer durationSec) {
    }

    private record CommandResult(boolean success, String output) {
    }
}
