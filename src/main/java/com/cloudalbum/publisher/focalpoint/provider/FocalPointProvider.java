package com.cloudalbum.publisher.focalpoint.provider;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

public interface FocalPointProvider {

    String getProviderType();

    List<FocalPointResult> detect(InputStream imageInputStream, Map<String, Object> extraConfig);
}
