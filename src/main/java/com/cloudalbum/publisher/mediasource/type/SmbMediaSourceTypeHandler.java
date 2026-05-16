package com.cloudalbum.publisher.mediasource.type;

import com.cloudalbum.publisher.common.enums.ResultCode;
import com.cloudalbum.publisher.common.exception.BusinessException;
import com.cloudalbum.publisher.common.security.CredentialCryptoService;
import com.cloudalbum.publisher.mediasource.entity.MediaSource;
import com.cloudalbum.publisher.mediasource.smb.MediaSourceFileClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class SmbMediaSourceTypeHandler implements MediaSourceTypeHandler {

    private static final String SOURCE_TYPE = "SMB";
    private static final int DEFAULT_PORT = 445;

    private final CredentialCryptoService credentialCryptoService;

    @Override
    public String getSourceType() {
        return SOURCE_TYPE;
    }

    @Override
    public Map<String, Object> normalizeConfig(Map<String, Object> config) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("host", requireMapText(config, "host", "主机地址不能为空"));
        normalized.put("port", resolvePort(asInteger(config == null ? null : config.get("port"))));
        normalized.put("rootPath", normalizeRootPath(asText(config == null ? null : config.get("rootPath"))));
        normalized.put("scanSubdirectories", asBoolean(config == null ? null : config.get("scanSubdirectories")));
        return normalized;
    }

    @Override
    public Map<String, Object> normalizeCredentials(Map<String, Object> credentials, boolean requirePassword) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("username", requireMapText(credentials, "username", "用户名不能为空"));
        String password = asText(credentials == null ? null : credentials.get("password"));
        if (requirePassword && !StringUtils.hasText(password)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "密码不能为空");
        }
        if (StringUtils.hasText(password)) {
            normalized.put("password", password.trim());
        }
        return normalized;
    }

    @Override
    public MediaSourceFileClient.MediaSourceConnection buildConnection(MediaSource mediaSource,
                                                                       Map<String, Object> config,
                                                                       Map<String, Object> credentials) {
        String rootPath = normalizeRootPath(asText(config.get("rootPath")));
        String shareName = extractShareNameFromPath(rootPath);
        return MediaSourceFileClient.MediaSourceConnection.builder()
                .host(requireMapText(config, "host", "主机地址不能为空"))
                .port(resolvePort(asInteger(config.get("port"))))
                .shareName(shareName)
                .username(requireMapText(credentials, "username", "用户名不能为空"))
                .password(requireMapText(credentials, "password", "密码不能为空"))
                .build();
    }

    @Override
    public Map<String, Object> summarizeConfig(Map<String, Object> config) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("host", config.get("host"));
        summary.put("port", config.get("port"));
        summary.put("rootPath", config.get("rootPath"));
        summary.put("scanSubdirectories", asBoolean(config.get("scanSubdirectories")));
        return summary;
    }

    @Override
    public void applyPersistentFields(MediaSource mediaSource,
                                      Map<String, Object> config,
                                      Map<String, Object> credentials) {
        mediaSource.setHost(asText(config.get("host")));
        mediaSource.setPort(resolvePort(asInteger(config.get("port"))));
        mediaSource.setRootPath(normalizeRootPath(asText(config.get("rootPath"))));
        mediaSource.setUsername(asText(credentials.get("username")));
        String password = asText(credentials.get("password"));
        mediaSource.setPasswordCiphertext(StringUtils.hasText(password)
                ? credentialCryptoService.encrypt(password)
                : mediaSource.getPasswordCiphertext());
    }

    private Integer resolvePort(Integer port) {
        return port == null || port <= 0 ? DEFAULT_PORT : port;
    }

    private String requireMapText(Map<String, Object> map, String key, String message) {
        String value = asText(map == null ? null : map.get(key));
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, message);
        }
        return value.trim();
    }

    private String asText(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = asText(value);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ex) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "端口范围无效");
        }
    }

    private boolean asBoolean(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private String normalizeRootPath(String rootPath) {
        if (!StringUtils.hasText(rootPath)) {
            return "/";
        }
        String normalized = rootPath.trim().replace('\\', '/');
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        while (normalized.contains("//")) {
            normalized = normalized.replace("//", "/");
        }
        if (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String extractShareNameFromPath(String path) {
        if (!StringUtils.hasText(path) || "/".equals(path)) {
            return "";
        }
        String cleanPath = path;
        if (cleanPath.startsWith("/")) {
            cleanPath = cleanPath.substring(1);
        }
        if (cleanPath.contains("/")) {
            return cleanPath.substring(0, cleanPath.indexOf("/"));
        }
        return cleanPath;
    }
}
