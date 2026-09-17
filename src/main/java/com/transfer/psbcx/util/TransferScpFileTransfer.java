package com.transfer.psbcx.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileSystemManager;
import org.apache.commons.vfs2.FileSystemOptions;
import org.apache.commons.vfs2.Selectors;
import org.apache.commons.vfs2.VFS;
import org.apache.commons.vfs2.auth.StaticUserAuthenticator;
import org.apache.commons.vfs2.impl.DefaultFileSystemConfigBuilder;
import org.apache.commons.vfs2.impl.DefaultFileSystemManager;
import org.apache.commons.vfs2.provider.sftp.SftpFileSystemConfigBuilder;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 基于 commons-vfs2 的 SFTP 文件传输。
 *
 * @author xiehao
 */
@Slf4j
public class TransferScpFileTransfer {

    private static final int BUFFER_SIZE = 256 * 1024;

    private final FileSystemManager fsManager;
    private final FileSystemOptions fsOptions;
    private final String remoteIp;
    private final String remoteUser;

    public TransferScpFileTransfer(String privateKeyPath, String remoteIp, String remoteUser) throws FileSystemException {
        this.fsManager = VFS.getManager();
        this.fsOptions = new FileSystemOptions();
        this.remoteIp = remoteIp;
        this.remoteUser = remoteUser;

        SftpFileSystemConfigBuilder sftpBuilder = SftpFileSystemConfigBuilder.getInstance();
        sftpBuilder.setStrictHostKeyChecking(fsOptions, "no");
        sftpBuilder.setUserDirIsRoot(fsOptions, false);
        sftpBuilder.setIdentities(fsOptions, new File[]{new File(privateKeyPath)});
        DefaultFileSystemConfigBuilder.getInstance().setUserAuthenticator(
                fsOptions, new StaticUserAuthenticator(null, null, null));
    }

    public boolean remoteExists(String remotePath) throws FileSystemException {
        FileObject remoteFile = null;
        try {
            remoteFile = fsManager.resolveFile(remotePath, fsOptions);
            return remoteFile.exists();
        } finally {
            closeQuietly(remoteFile);
        }
    }

    public boolean localExists(String localPath) {
        return Files.exists(Paths.get(localPath));
    }

    public void uploadFile(String localFilePath, String remoteFilePath) throws IOException {
        String targetUri = toSftpUri(remoteFilePath);
        FileObject localFile = null;
        FileObject remoteFile = null;
        FileObject remoteParent = null;
        try {
            if (!localExists(localFilePath)) {
                throw new FileSystemException("本地文件不存在: " + localFilePath);
            }
            remoteFile = fsManager.resolveFile(targetUri, fsOptions);
            remoteParent = remoteFile.getParent();
            if (!remoteParent.exists()) {
                log.info("远程目录不存在, 创建: {}", remoteParent.getName().getPath());
                remoteParent.createFolder();
            }
            if (remoteFile.exists()) {
                log.info("删除已存在的远程文件: {}", remoteFile);
                remoteFile.delete(Selectors.SELECT_SELF);
            }
            localFile = fsManager.resolveFile(localFilePath);
            remoteFile = fsManager.resolveFile(targetUri, fsOptions);
            copy(localFile.getContent().getInputStream(), remoteFile.getContent().getOutputStream());
            log.info("文件上传成功: {} -> {}", localFilePath, targetUri);
        } finally {
            closeQuietly(localFile);
            closeQuietly(remoteFile);
            closeQuietly(remoteParent);
        }
    }

    public void downloadFile(String remoteFilePath, String localFilePath) throws IOException {
        String sourceUri = toSftpUri(remoteFilePath);
        FileObject localFile = null;
        FileObject remoteFile = null;
        try {
            if (!remoteExists(sourceUri)) {
                throw new FileSystemException("远程文件不存在: " + sourceUri);
            }
            prepareLocalFile(localFilePath);
            localFile = fsManager.resolveFile(localFilePath);
            remoteFile = fsManager.resolveFile(sourceUri, fsOptions);
            copy(remoteFile.getContent().getInputStream(), localFile.getContent().getOutputStream());
            log.info("文件下载成功: {} -> {}", sourceUri, localFilePath);
        } finally {
            closeQuietly(localFile);
            closeQuietly(remoteFile);
        }
    }

    private void prepareLocalFile(String localFilePath) throws IOException {
        Path path = Paths.get(localFilePath);
        if (Files.exists(path)) {
            Files.delete(path);
            log.info("本地文件已存在, 删除: {}", localFilePath);
        }
        Path parent = path.getParent();
        if (parent != null && !Files.exists(parent)) {
            log.info("本地目录不存在, 创建: {}", parent);
            Files.createDirectories(parent);
        }
    }

    private void copy(InputStream in, OutputStream out) throws IOException {
        try (InputStream input = new BufferedInputStream(in); OutputStream output = out) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        }
    }

    private String toSftpUri(String remotePath) {
        return String.format("sftp://%s@%s:22", remoteUser, remoteIp) + remotePath;
    }

    private void closeQuietly(FileObject fileObject) {
        if (fileObject == null) {
            return;
        }
        try {
            fileObject.close();
        } catch (FileSystemException e) {
            log.error("关闭文件资源报错: " + e.getMessage());
        }
    }

    public void close() {
        ((DefaultFileSystemManager) fsManager).close();
    }
}
