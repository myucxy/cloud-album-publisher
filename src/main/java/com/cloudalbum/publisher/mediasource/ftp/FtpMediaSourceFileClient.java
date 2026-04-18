package com.cloudalbum.publisher.mediasource.ftp;

import com.cloudalbum.publisher.mediasource.smb.MediaSourceFileClient;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Component
public class FtpMediaSourceFileClient implements MediaSourceFileClient {

    @Override
    public String getSourceType() {
        return "FTP";
    }

    @Override
    public List<Entry> list(MediaSourceConnection connection, String path) throws Exception {
        FTPClient client = openClient(connection);
        try {
            String targetPath = normalizeRemotePath(path);
            FTPFile[] files = client.listFiles(targetPath);
            List<Entry> entries = new ArrayList<>();
            if (files == null) {
                return entries;
            }
            for (FTPFile file : files) {
                String name = file.getName();
                if (!StringUtils.hasText(name) || ".".equals(name) || "..".equals(name)) {
                    continue;
                }
                boolean directory = file.isDirectory();
                String childPath = joinPath(targetPath, name);
                entries.add(Entry.builder()
                        .path(childPath)
                        .name(name)
                        .directory(directory)
                        .size(directory ? null : file.getSize())
                        .modifiedAt(toLocalDateTime(file.getTimestamp() == null ? null : file.getTimestamp().toInstant()))
                        .hasChildren(directory && hasChildren(client, childPath))
                        .build());
            }
            return entries;
        } finally {
            disconnectQuietly(client);
        }
    }

    @Override
    public InputStream open(MediaSourceConnection connection, String path) throws Exception {
        FTPClient client = openClient(connection);
        String targetPath = normalizeRemotePath(path);
        InputStream stream = client.retrieveFileStream(targetPath);
        if (stream == null) {
            disconnectQuietly(client);
            throw new IOException("无法读取文件: " + targetPath);
        }
        return new FtpInputStream(stream, client);
    }

    private FTPClient openClient(MediaSourceConnection connection) throws Exception {
        FTPClient client = new FTPClient();
        client.connect(connection.getHost(), connection.getPort());
        if (!client.login(connection.getUsername(), connection.getPassword())) {
            disconnectQuietly(client);
            throw new IOException("FTP 登录失败");
        }
        client.enterLocalPassiveMode();
        client.setFileType(FTP.BINARY_FILE_TYPE);
        return client;
    }

    private boolean hasChildren(FTPClient client, String path) throws IOException {
        FTPFile[] files = client.listFiles(path);
        if (files == null) {
            return false;
        }
        for (FTPFile file : files) {
            String name = file.getName();
            if (StringUtils.hasText(name) && !".".equals(name) && !"..".equals(name)) {
                return true;
            }
        }
        return false;
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

    private String joinPath(String parent, String name) {
        if (!StringUtils.hasText(parent) || "/".equals(parent)) {
            return "/" + name;
        }
        return parent + "/" + name;
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        if (instant == null) {
            return null;
        }
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }

    private void disconnectQuietly(FTPClient client) {
        if (client == null) {
            return;
        }
        try {
            if (client.isConnected()) {
                client.logout();
                client.disconnect();
            }
        } catch (Exception ignored) {
        }
    }

    private static final class FtpInputStream extends FilterInputStream {

        private final FTPClient client;

        private FtpInputStream(InputStream delegate, FTPClient client) {
            super(delegate);
            this.client = client;
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } finally {
                try {
                    client.completePendingCommand();
                } finally {
                    if (client.isConnected()) {
                        try {
                            client.logout();
                        } finally {
                            client.disconnect();
                        }
                    }
                }
            }
        }
    }
}
