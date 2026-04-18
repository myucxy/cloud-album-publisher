package com.cloudalbum.publisher.mediasource.smb;

import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;
import com.hierynomus.smbj.share.File;
import com.hierynomus.smbj.share.Open;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

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
        try (SMBClient client = new SMBClient();
             Connection smbConnection = client.connect(connection.getHost(), connection.getPort());
             Session session = smbConnection.authenticate(new AuthenticationContext(
                     connection.getUsername(),
                     connection.getPassword().toCharArray(),
                     null));
             DiskShare share = (DiskShare) session.connectShare(connection.getShareName())) {
            String smbPath = toSmbPath(path);
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
                        .hasChildren(directory && hasChildren(share, childPath))
                        .build());
            }
            return items;
        }
    }

    @Override
    public InputStream open(MediaSourceConnection connection, String path) throws Exception {
        SMBClient client = new SMBClient();
        Connection smbConnection = client.connect(connection.getHost(), connection.getPort());
        Session session = smbConnection.authenticate(new AuthenticationContext(
                connection.getUsername(),
                connection.getPassword().toCharArray(),
                null));
        DiskShare share = (DiskShare) session.connectShare(connection.getShareName());
        File file = share.openFile(
                toSmbPath(path),
                EnumSet.of(FILE_READ_DATA, FILE_READ_ATTRIBUTES),
                EnumSet.of(FILE_ATTRIBUTE_NORMAL),
                ALL,
                FILE_OPEN,
                null);
        InputStream inputStream = file.getInputStream();
        return new SmbInputStream(inputStream, file, share, session, smbConnection, client);
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
