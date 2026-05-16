package com.cloudalbum.publisher.mediasource.sftp;

import com.cloudalbum.publisher.mediasource.smb.MediaSourceFileClient;
import lombok.extern.slf4j.Slf4j;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.RemoteResourceInfo;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
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

@Slf4j
@Component
public class SftpMediaSourceFileClient implements MediaSourceFileClient {

    @Override
    public String getSourceType() {
        return "SFTP";
    }

    @Override
    public List<Entry> list(MediaSourceConnection connection, String path) throws Exception {
        SSHClient sshClient = openClient(connection);
        SFTPClient sftpClient = sshClient.newSFTPClient();
        try {
            String targetPath = normalizeRemotePath(path);
            List<RemoteResourceInfo> resources = sftpClient.ls(targetPath);
            List<Entry> entries = new ArrayList<>();
            for (RemoteResourceInfo resource : resources) {
                String name = resource.getName();
                if (!StringUtils.hasText(name) || ".".equals(name) || "..".equals(name)) {
                    continue;
                }
                boolean directory = resource.isDirectory();
                String childPath = joinPath(targetPath, name);
                entries.add(Entry.builder()
                        .path(childPath)
                        .name(name)
                        .directory(directory)
                        .size(directory ? null : resource.getAttributes().getSize())
                        .modifiedAt(toLocalDateTime(resource.getAttributes().getMtime()))
                        .hasChildren(directory && hasChildren(sftpClient, childPath))
                        .build());
            }
            return entries;
        } finally {
            try {
                sftpClient.close();
            } finally {
                disconnectQuietly(sshClient);
            }
        }
    }

    @Override
    public InputStream open(MediaSourceConnection connection, String path) throws Exception {
        SSHClient sshClient = openClient(connection);
        SFTPClient sftpClient;
        try {
            sftpClient = sshClient.newSFTPClient();
        } catch (Exception e) {
            disconnectQuietly(sshClient);
            throw e;
        }

        try {
            InputStream stream = sftpClient.open(normalizeRemotePath(path)).new RemoteFileInputStream();
            return new SftpInputStream(stream, sftpClient, sshClient);
        } catch (Exception e) {
            try {
                sftpClient.close();
            } catch (Exception ignored) {
            }
            disconnectQuietly(sshClient);
            throw e;
        }
    }

    private SSHClient openClient(MediaSourceConnection connection) throws Exception {
        SSHClient client = new SSHClient();
        log.debug("SFTP connection to {}:{}", connection.getHost(), connection.getPort());
        client.addHostKeyVerifier(new PromiscuousVerifier());
        client.connect(connection.getHost(), connection.getPort());
        client.authPassword(connection.getUsername(), connection.getPassword());
        return client;
    }

    private boolean hasChildren(SFTPClient client, String path) throws IOException {
        List<RemoteResourceInfo> resources = client.ls(path);
        for (RemoteResourceInfo resource : resources) {
            String name = resource.getName();
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

    private LocalDateTime toLocalDateTime(Long seconds) {
        if (seconds == null) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(seconds), ZoneId.systemDefault());
    }

    private void disconnectQuietly(SSHClient client) {
        if (client == null) {
            return;
        }
        try {
            client.disconnect();
        } catch (Exception ignored) {
        }
        try {
            client.close();
        } catch (Exception ignored) {
        }
    }

    private static final class SftpInputStream extends FilterInputStream {

        private final SFTPClient sftpClient;
        private final SSHClient sshClient;

        private SftpInputStream(InputStream delegate, SFTPClient sftpClient, SSHClient sshClient) {
            super(delegate);
            this.sftpClient = sftpClient;
            this.sshClient = sshClient;
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } finally {
                try {
                    sftpClient.close();
                } catch (Exception ignored) {
                }
                try {
                    sshClient.disconnect();
                } catch (Exception ignored) {
                }
                try {
                    sshClient.close();
                } catch (Exception ignored) {
                }
            }
        }
    }
}
