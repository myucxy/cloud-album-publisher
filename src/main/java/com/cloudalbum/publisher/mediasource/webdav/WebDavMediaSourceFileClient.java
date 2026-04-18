package com.cloudalbum.publisher.mediasource.webdav;

import com.github.sardine.Sardine;
import com.github.sardine.SardineFactory;
import com.github.sardine.impl.SardineException;
import com.github.sardine.DavResource;
import com.cloudalbum.publisher.mediasource.smb.MediaSourceFileClient;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.FilterInputStream;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Component
public class WebDavMediaSourceFileClient implements MediaSourceFileClient {

    @Override
    public String getSourceType() {
        return "WEBDAV";
    }

    @Override
    public List<Entry> list(MediaSourceConnection connection, String path) throws Exception {
        Sardine sardine = SardineFactory.begin(connection.getUsername(), connection.getPassword());
        String targetPath = normalizeRemotePath(path);
        List<DavResource> resources = sardine.list(buildUrl(connection, targetPath));
        List<Entry> entries = new ArrayList<>();
        for (DavResource resource : resources) {
            String href = normalizeRemotePath(resource.getPath());
            if (targetPath.equals(href)) {
                continue;
            }
            String name = fileName(href);
            boolean directory = resource.isDirectory();
            entries.add(Entry.builder()
                    .path(href)
                    .name(name)
                    .directory(directory)
                    .size(directory ? null : resource.getContentLength())
                    .modifiedAt(resource.getModified() == null ? null : LocalDateTime.ofInstant(resource.getModified().toInstant(), ZoneId.systemDefault()))
                    .hasChildren(directory && hasChildren(sardine, connection, href))
                    .build());
        }
        return entries;
    }

    @Override
    public InputStream open(MediaSourceConnection connection, String path) throws Exception {
        Sardine sardine = SardineFactory.begin(connection.getUsername(), connection.getPassword());
        return new WebDavInputStream(sardine.get(buildUrl(connection, normalizeRemotePath(path))), sardine);
    }

    private boolean hasChildren(Sardine sardine, MediaSourceConnection connection, String path) throws Exception {
        try {
            List<DavResource> resources = sardine.list(buildUrl(connection, path));
            for (DavResource resource : resources) {
                String href = normalizeRemotePath(resource.getPath());
                if (!path.equals(href)) {
                    return true;
                }
            }
            return false;
        } catch (SardineException ex) {
            return false;
        }
    }

    private String buildUrl(MediaSourceConnection connection, String path) {
        String scheme = Boolean.TRUE.equals(connection.getSecure()) ? "https" : "http";
        String normalizedPath = normalizeRemotePath(path);
        String[] segments = normalizedPath.split("/");
        StringBuilder builder = new StringBuilder();
        builder.append(scheme)
                .append("://")
                .append(connection.getHost())
                .append(":")
                .append(connection.getPort());
        for (String segment : segments) {
            if (!StringUtils.hasText(segment)) {
                continue;
            }
            builder.append('/').append(URLEncoder.encode(segment, StandardCharsets.UTF_8));
        }
        if (builder.charAt(builder.length() - 1) == ':' || builder.charAt(builder.length() - 1) == connection.getPort().toString().charAt(connection.getPort().toString().length() - 1)) {
            if ("/".equals(normalizedPath)) {
                builder.append('/');
            }
        }
        return builder.toString();
    }

    private String normalizeRemotePath(String path) {
        if (!StringUtils.hasText(path)) {
            return "/";
        }
        String normalized = path.trim().replace('\\', '/');
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

    private String fileName(String path) {
        String normalized = normalizeRemotePath(path);
        int index = normalized.lastIndexOf('/');
        return index >= 0 ? normalized.substring(index + 1) : normalized;
    }

    private static final class WebDavInputStream extends FilterInputStream {

        private final Sardine sardine;

        private WebDavInputStream(InputStream delegate, Sardine sardine) {
            super(delegate);
            this.sardine = sardine;
        }

        @Override
        public void close() throws java.io.IOException {
            try {
                super.close();
            } finally {
                try {
                    sardine.shutdown();
                } catch (Exception ignored) {
                }
            }
        }
    }
}
