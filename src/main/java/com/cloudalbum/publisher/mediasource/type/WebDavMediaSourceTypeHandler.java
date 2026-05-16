package com.cloudalbum.publisher.mediasource.type;

import com.cloudalbum.publisher.common.enums.ResultCode;
import com.cloudalbum.publisher.common.exception.BusinessException;
import com.cloudalbum.publisher.common.security.CredentialCryptoService;
import com.cloudalbum.publisher.mediasource.entity.MediaSource;
import com.cloudalbum.publisher.mediasource.smb.MediaSourceFileClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class WebDavMediaSourceTypeHandler implements MediaSourceTypeHandler {

    private static final String SOURCE_TYPE = "WEBDAV";
    private static final int DEFAULT_HTTP_PORT = 80;
    private static final int DEFAULT_HTTPS_PORT = 443;

    private final CredentialCryptoService credentialCryptoService;

    @Override
    public String getSourceType() {
        return SOURCE_TYPE;
    }

    @Override
    public Map<String, Object> normalizeConfig(Map<String, Object> config) {
        Map<String, Object> parsed = parseConnectionInfo(config);
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("url", parsed.get("url"));
        normalized.put("host", parsed.get("host"));
        normalized.put("port", parsed.get("port"));
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
        Map<String, Object> parsed = parseConnectionInfo(config);
        return MediaSourceFileClient.MediaSourceConnection.builder()
                .sourceType(SOURCE_TYPE)
                .url((String) parsed.get("url"))
                .host((String) parsed.get("host"))
                .port((Integer) parsed.get("port"))
                .rootPath(normalizeRootPath(asText(config.get("rootPath"))))
                .username(requireMapText(credentials, "username", "用户名不能为空"))
                .password(requireMapText(credentials, "password", "密码不能为空"))
                .secure((Boolean) parsed.get("secure"))
                .build();
    }

    @Override
    public Map<String, Object> summarizeConfig(Map<String, Object> config) {
        Map<String, Object> parsed = parseConnectionInfo(config);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("url", parsed.get("url"));
        summary.put("host", parsed.get("host"));
        summary.put("port", parsed.get("port"));
        summary.put("rootPath", config.get("rootPath"));
        summary.put("scanSubdirectories", asBoolean(config.get("scanSubdirectories")));
        return summary;
    }

    @Override
    public void applyPersistentFields(MediaSource mediaSource,
                                      Map<String, Object> config,
                                      Map<String, Object> credentials) {
        Map<String, Object> parsed = parseConnectionInfo(config);
        mediaSource.setHost((String) parsed.get("host"));
        mediaSource.setPort((Integer) parsed.get("port"));
        mediaSource.setShareName(null);
        mediaSource.setRootPath(normalizeRootPath(asText(config.get("rootPath"))));
        mediaSource.setUsername(asText(credentials.get("username")));
        String password = asText(credentials.get("password"));
        mediaSource.setPasswordCiphertext(StringUtils.hasText(password)
                ? credentialCryptoService.encrypt(password)
                : mediaSource.getPasswordCiphertext());
    }

    private Map<String, Object> parseConnectionInfo(Map<String, Object> config) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (config != null && config.containsKey("url")) {
            String url = asText(config.get("url"));
            if (!StringUtils.hasText(url)) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "WebDAV URL 不能为空");
            }
            String fullUrl = url;
            if (!fullUrl.startsWith("http://") && !fullUrl.startsWith("https://")) {
                fullUrl = "http://" + fullUrl;
            }
            try {
                URI uri = URI.create(fullUrl);
                String host = uri.getHost();
                if (!StringUtils.hasText(host)) {
                    throw new BusinessException(ResultCode.BAD_REQUEST, "WebDAV URL 格式无效");
                }
                boolean secure = "https".equalsIgnoreCase(uri.getScheme());
                int port = uri.getPort();
                if (port <= 0) {
                    port = secure ? DEFAULT_HTTPS_PORT : DEFAULT_HTTP_PORT;
                }
                String normalizedUrl = String.format("%s://%s:%d%s", secure ? "https" : "http", host, port, normalizeUrlPath(uri.getRawPath()));
                result.put("url", normalizedUrl);
                result.put("host", host);
                result.put("port", port);
                result.put("secure", secure);
            } catch (IllegalArgumentException e) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "WebDAV URL 格式无效");
            }
        } else {
            String host = requireMapText(config, "host", "主机地址不能为空");
            boolean secure = false;
            if (config != null) {
                Object secureVal = config.get("secure");
                if (secureVal instanceof Boolean) {
                    secure = (Boolean) secureVal;
                } else if (secureVal != null) {
                    secure = Boolean.parseBoolean(String.valueOf(secureVal));
                }
            }
            Integer port = asInteger(config == null ? null : config.get("port"));
            if (port == null || port <= 0) {
                port = secure ? DEFAULT_HTTPS_PORT : DEFAULT_HTTP_PORT;
            }
            String url = String.format("%s://%s:%d", secure ? "https" : "http", host, port);
            result.put("url", url);
            result.put("host", host);
            result.put("port", port);
            result.put("secure", secure);
        }
        return result;
    }

    private String normalizeUrlPath(String rawPath) {
        if (!StringUtils.hasText(rawPath) || "/".equals(rawPath)) {
            return "";
        }
        return rawPath.startsWith("/") ? rawPath : "/" + rawPath;
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
}
