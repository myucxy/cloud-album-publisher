package com.cloudalbum.publisher.mediasource.smb;

import lombok.Builder;
import lombok.Getter;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;

public interface MediaSourceFileClient {

    List<Entry> list(MediaSourceConnection connection, String path) throws Exception;

    InputStream open(MediaSourceConnection connection, String path) throws Exception;

    @Getter
    @Builder
    class MediaSourceConnection {
        private final String host;
        private final Integer port;
        private final String shareName;
        private final String username;
        private final String password;
    }

    @Getter
    @Builder
    class Entry {
        private final String path;
        private final String name;
        private final boolean directory;
        private final Long size;
        private final LocalDateTime modifiedAt;
        private final boolean hasChildren;
    }
}
