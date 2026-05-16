package com.cloudalbum.publisher.mediasource.smb;

import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;
import com.hierynomus.smbj.share.File;
import com.hierynomus.smbj.share.Open;
import jcifs.CIFSContext;
import jcifs.config.PropertyConfiguration;
import jcifs.context.BaseContext;
import jcifs.smb.NtlmPasswordAuthenticator;
import jcifs.smb.SmbFile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Properties;

import static com.hierynomus.msdtyp.AccessMask.FILE_READ_ATTRIBUTES;
import static com.hierynomus.msdtyp.AccessMask.FILE_READ_DATA;
import static com.hierynomus.msfscc.FileAttributes.FILE_ATTRIBUTE_DIRECTORY;
import static com.hierynomus.msfscc.FileAttributes.FILE_ATTRIBUTE_NORMAL;
import static com.hierynomus.mssmb2.SMB2CreateDisposition.FILE_OPEN;
import static com.hierynomus.mssmb2.SMB2ShareAccess.ALL;

@Component
public class SmbMediaSourceFileClient implements MediaSourceFileClient {

    @Override
    public String getSourceType() {
        return "SMB";
    }

    @Override
    public List<Entry> list(MediaSourceConnection connection, String path) throws Exception {
        String shareName = resolveShareName(connection, path);
        if (!StringUtils.hasText(shareName)) {
            return listShares(connection);
        }
        String directoryPath = getPathWithinShare(connection, path);

        try (SMBClient client = new SMBClient();
             Connection smbConnection = client.connect(connection.getHost(), connection.getPort());
             Session session = smbConnection.authenticate(new AuthenticationContext(
                     connection.getUsername(),
                     connection.getPassword().toCharArray(),
                     null));
             DiskShare share = (DiskShare) session.connectShare(shareName)) {
            String smbPath = toSmbPath(directoryPath);
            List<Entry> items = new ArrayList<>();
            for (FileIdBothDirectoryInformation information : share.list(smbPath)) {
                String name = information.getFileName();
                if (".".equals(name) || "..".equals(name)) {
                    continue;
                }
                boolean directory = isDirectory(information);
                String childPath = joinPath(path, name);
                items.add(Entry.builder()
                        .path(childPath)
                        .name(name)
                        .directory(directory)
                        .size(directory ? null : information.getEndOfFile())
                        .modifiedAt(LocalDateTime.ofInstant(
                                information.getLastWriteTime().toInstant(),
                                ZoneId.systemDefault()))
                        .hasChildren(directory && hasChildren(share, directoryPath + "/" + name))
                        .build());
            }
            return items;
        }
    }

    @Override
    public InputStream open(MediaSourceConnection connection, String path) throws Exception {
        String shareName = resolveShareName(connection, path);
        String directoryPath = getPathWithinShare(connection, path);

        SMBClient client = new SMBClient();
        Connection smbConnection;
        Session session;
        DiskShare share;

        try {
            smbConnection = client.connect(connection.getHost(), connection.getPort());
        } catch (Exception e) {
            client.close();
            throw e;
        }

        try {
            session = smbConnection.authenticate(new AuthenticationContext(
                    connection.getUsername(),
                    connection.getPassword().toCharArray(),
                    null));
        } catch (Exception e) {
            smbConnection.close();
            client.close();
            throw e;
        }

        try {
            share = (DiskShare) session.connectShare(shareName);
        } catch (Exception e) {
            session.close();
            smbConnection.close();
            client.close();
            throw e;
        }

        try {
            File file = share.openFile(
                    toSmbPath(directoryPath),
                    EnumSet.of(FILE_READ_DATA, FILE_READ_ATTRIBUTES),
                    EnumSet.of(FILE_ATTRIBUTE_NORMAL),
                    ALL,
                    FILE_OPEN,
                    null);
            InputStream inputStream = file.getInputStream();
            return new SmbInputStream(inputStream, file, share, session, smbConnection, client);
        } catch (Exception e) {
            share.close();
            session.close();
            smbConnection.close();
            client.close();
            throw e;
        }
    }

    private List<Entry> listShares(MediaSourceConnection connection) throws Exception {
        Properties properties = new Properties();
        properties.setProperty("jcifs.smb.client.responseTimeout", "5000");
        properties.setProperty("jcifs.smb.client.soTimeout", "5000");
        BaseContext baseContext = new BaseContext(new PropertyConfiguration(properties));
        try {
            CIFSContext context = baseContext.withCredentials(new NtlmPasswordAuthenticator(
                    null,
                    connection.getUsername(),
                    connection.getPassword()));
            try (SmbFile root = new SmbFile("smb://" + connection.getHost() + "/", context)) {
                List<Entry> items = new ArrayList<>();
                for (SmbFile share : root.listFiles()) {
                    String name = share.getName();
                    try {
                        if (!StringUtils.hasText(name)) {
                            continue;
                        }
                        String cleanName = name.endsWith("/") ? name.substring(0, name.length() - 1) : name;
                        items.add(Entry.builder()
                                .path("/" + cleanName)
                                .name(cleanName)
                                .directory(true)
                                .size(null)
                                .modifiedAt(toLocalDateTime(share.lastModified()))
                                .hasChildren(true)
                                .build());
                    } finally {
                        share.close();
                    }
                }
                return items;
            }
        } finally {
            baseContext.close();
        }
    }

    private LocalDateTime toLocalDateTime(long millis) {
        return millis > 0 ? LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault()) : null;
    }

    private boolean hasChildren(DiskShare share, String path) {
        for (FileIdBothDirectoryInformation information : share.list(toSmbPath(path))) {
            String name = information.getFileName();
            if (!".".equals(name) && !"..".equals(name)) {
                return true;
            }
        }
        return false;
    }

    private boolean isDirectory(FileIdBothDirectoryInformation information) {
        return (information.getFileAttributes() & FILE_ATTRIBUTE_DIRECTORY.getValue()) != 0;
    }

    private String toSmbPath(String path) {
        if (!StringUtils.hasText(path) || "/".equals(path)) {
            return "";
        }
        return path.startsWith("/") ? path.substring(1).replace('/', '\\') : path.replace('/', '\\');
    }

    private String resolveShareName(MediaSourceConnection connection, String path) {
        if (StringUtils.hasText(connection.getShareName())) {
            return connection.getShareName();
        }
        return extractShareNameFromPath(path);
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

    private String getPathWithinShare(MediaSourceConnection connection, String path) {
        if (StringUtils.hasText(connection.getShareName())) {
            return stripSharePrefix(path, connection.getShareName());
        }
        return getPathWithoutShare(path);
    }

    private String stripSharePrefix(String path, String shareName) {
        if (!StringUtils.hasText(path) || "/".equals(path)) {
            return "";
        }
        String cleanPath = path.startsWith("/") ? path.substring(1) : path;
        String normalizedShareName = shareName.startsWith("/") ? shareName.substring(1) : shareName;
        if (cleanPath.equals(normalizedShareName)) {
            return "";
        }
        if (cleanPath.startsWith(normalizedShareName + "/")) {
            return cleanPath.substring(normalizedShareName.length() + 1);
        }
        return cleanPath;
    }

    private String getPathWithoutShare(String path) {
        if (!StringUtils.hasText(path) || "/".equals(path)) {
            return "";
        }
        String cleanPath = path;
        if (cleanPath.startsWith("/")) {
            cleanPath = cleanPath.substring(1);
        }
        if (cleanPath.contains("/")) {
            return cleanPath.substring(cleanPath.indexOf("/") + 1);
        }
        return "";
    }

    private String joinPath(String parent, String name) {
        if (!StringUtils.hasText(parent) || "/".equals(parent)) {
            return "/" + name;
        }
        return parent + "/" + name;
    }

    private static final class SmbInputStream extends InputStream {

        private final InputStream delegate;
        private final Open file;
        private final DiskShare share;
        private final Session session;
        private final Connection connection;
        private final SMBClient client;

        private SmbInputStream(InputStream delegate,
                               Open file,
                               DiskShare share,
                               Session session,
                               Connection connection,
                               SMBClient client) {
            this.delegate = delegate;
            this.file = file;
            this.share = share;
            this.session = session;
            this.connection = connection;
            this.client = client;
        }

        @Override
        public int read() throws java.io.IOException {
            return delegate.read();
        }

        @Override
        public int read(byte[] b, int off, int len) throws java.io.IOException {
            return delegate.read(b, off, len);
        }

        @Override
        public void close() throws java.io.IOException {
            try {
                delegate.close();
            } finally {
                try {
                    file.close();
                } finally {
                    try {
                        share.close();
                    } finally {
                        try {
                            session.close();
                        } finally {
                            try {
                                connection.close();
                            } finally {
                                client.close();
                            }
                        }
                    }
                }
            }
        }
    }
}
