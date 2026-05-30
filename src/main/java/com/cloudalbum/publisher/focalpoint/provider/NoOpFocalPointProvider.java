package com.cloudalbum.publisher.focalpoint.provider;

import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class NoOpFocalPointProvider implements FocalPointProvider {

    @Override
    public String getProviderType() {
        return "NOOP";
    }

    @Override
    public List<FocalPointResult> detect(InputStream imageInputStream, Map<String, Object> extraConfig) {
        return Collections.emptyList();
    }
}
