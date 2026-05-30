package com.cloudalbum.publisher.focalpoint.provider;

import com.cloudalbum.publisher.common.enums.ResultCode;
import com.cloudalbum.publisher.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class FocalPointProviderRegistry {

    private final List<FocalPointProvider> providers;

    public FocalPointProvider getProvider(String providerType) {
        return providers.stream()
                .filter(p -> Objects.equals(p.getProviderType(), providerType))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ResultCode.BAD_REQUEST,
                        "焦点检测 Provider 不存在: " + providerType));
    }

    public List<String> getAvailableProviderTypes() {
        return providers.stream()
                .map(FocalPointProvider::getProviderType)
                .collect(Collectors.toList());
    }
}
