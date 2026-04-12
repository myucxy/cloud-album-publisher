package com.cloudalbum.publisher.media.content;

import com.cloudalbum.publisher.common.enums.ResultCode;
import com.cloudalbum.publisher.common.exception.BusinessException;
import com.cloudalbum.publisher.media.entity.Media;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.InputStream;

@Component
@RequiredArgsConstructor
public class ObjectStorageMediaContentResolver implements MediaContentResolver {

    private final MinioClient minioClient;

    @Value("${minio.bucket}")
    private String bucket;

    @Override
    public boolean supports(Media media) {
        return media != null && StringUtils.hasText(media.getObjectKey());
    }

    @Override
    public MediaContentResource resolve(Media media, boolean thumbnail) {
        String objectKey = thumbnail ? media.getThumbnailKey() : media.getObjectKey();
        return resolveObject(
                media.getBucketName(),
                objectKey,
                thumbnail ? "application/octet-stream" : media.getContentType(),
                thumbnail ? "缩略图不存在" : "媒体内容不存在");
    }

    public MediaContentResource resolveObject(String bucketName,
                                              String objectKey,
                                              String defaultContentType,
                                              String notFoundMessage) {
        if (!StringUtils.hasText(objectKey)) {
            throw new BusinessException(ResultCode.NOT_FOUND, notFoundMessage);
        }
        String resolvedBucketName = StringUtils.hasText(bucketName) ? bucketName : bucket;
        try {
            StatObjectResponse stat = minioClient.statObject(StatObjectArgs.builder()
                    .bucket(resolvedBucketName)
                    .object(objectKey)
                    .build());
            String contentType = StringUtils.hasText(stat.contentType()) ? stat.contentType() : defaultContentType;
            return new ObjectStorageMediaContentResource(minioClient, resolvedBucketName, objectKey, stat.size(), contentType);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "读取媒体存储对象失败");
        }
    }

    private record ObjectStorageMediaContentResource(MinioClient minioClient,
                                                     String bucketName,
                                                     String objectKey,
                                                     long contentLength,
                                                     String contentType) implements MediaContentResource {

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public long getContentLength() {
            return contentLength;
        }

        @Override
        public InputStream open() throws Exception {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectKey)
                    .build());
        }

        @Override
        public InputStream open(long offset, long length) throws Exception {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectKey)
                    .offset(offset)
                    .length(length)
                    .build());
        }
    }
}
