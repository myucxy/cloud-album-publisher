package com.cloudalbum.publisher.media.content;

import java.io.InputStream;

public interface MediaContentResource {

    String getContentType();

    long getContentLength();

    InputStream open() throws Exception;

    InputStream open(long offset, long length) throws Exception;
}
