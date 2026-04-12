package com.cloudalbum.publisher.mediasource.type;

import com.cloudalbum.publisher.mediasource.entity.MediaSource;
import com.cloudalbum.publisher.mediasource.smb.MediaSourceFileClient;

import java.util.Map;

public interface MediaSourceTypeHandler {

    String getSourceType();

    Map<String, Object> normalizeConfig(Map<String, Object> config);

    Map<String, Object> normalizeCredentials(Map<String, Object> credentials, boolean requirePassword);

    MediaSourceFileClient.MediaSourceConnection buildConnection(MediaSource mediaSource,
                                                                Map<String, Object> config,
                                                                Map<String, Object> credentials);

    Map<String, Object> summarizeConfig(Map<String, Object> config);

    void applyPersistentFields(MediaSource mediaSource,
                               Map<String, Object> config,
                               Map<String, Object> credentials);
}
