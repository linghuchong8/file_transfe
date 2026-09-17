package com.transfer.medical.util;

import com.google.common.base.Strings;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Vector;
import java.util.stream.Collectors;

/**
 * SFTP 传输工具（基于 jsch）。
 *
 * @author yuanjihong
 */
public class MedicalSftpTools implements AutoCloseable {

    private final transient Logger logger = LoggerFactory.getLogger(MedicalSftpTools.class);

    private ChannelSftp sftp;
    private Session session;
    private String username;
    private String password;
    private String privateKey;
    private String host;
    private int port;

    public MedicalSftpTools(final String username, final String password, final String host, final int port) {
        this.username = username;
        this.password = password;
        this.host = host;
        this.port = port;
        login();
    }

    public MedicalSftpTools(final String username, final String host, final int port, final String privateKey) {
        this.username = username;
        this.host = host;
        this.port = port;
        this.privateKey = privateKey;
        login();
    }

    public MedicalSftpTools() {
    }

    public void login() {
        final JSch jsch = new JSch();
        try {
            if (!Strings.isNullOrEmpty(privateKey)) {
                jsch.addIdentity(privateKey);
            }
            session = jsch.getSession(username, host, port);
            if (!Strings.isNullOrEmpty(password)) {
                session.setPassword(password);
            }
            final Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.connect();

            Channel channel = session.openChannel("sftp");
            channel.connect();
            sftp = (ChannelSftp) channel;
            logger.info("sftp 连接成功");
        } catch (JSchException e) {
            throw new RuntimeException("sftp 登陆失败 : " + e);
        }
    }

    @Override
    public void close() {
        if (sftp != null && sftp.isConnected()) {
            sftp.disconnect();
        }
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
        logger.info("sftp 关闭成功");
    }

    /**
     * 上传输入流数据，完整路径为 basePath + directory。
     */
    public void upload(final String basePath, final String directory,
                       final String sftpFileName, final InputStream input) throws SftpException {
        try {
            sftp.cd(basePath);
            sftp.cd(directory);
        } catch (SftpException e) {
            final String fullPath = MedicalStringHelper.addEndSeparator(basePath) + directory;
            final File fullPathFile = new File(fullPath);
            if (!fullPathFile.exists()) {
                fullPathFile.mkdirs();
            }
            sftp.cd(fullPath);
        }
        sftp.put(input, sftpFileName);
    }

    /**
     * 下载单个文件。
     */
    public void download(String directory, String remoteFileName,
                         String saveFile) throws SftpException, FileNotFoundException {
        if (!Strings.isNullOrEmpty(directory)) {
            sftp.cd(directory);
        }
        sftp.get(remoteFileName, new FileOutputStream(new File(saveFile)));
    }

    /**
     * 下载目录下的全部文件。
     */
    public void download(String directory, String saveDirectory) throws SftpException {
        if (!Strings.isNullOrEmpty(directory)) {
            sftp.cd(directory);
        }
        String localSaveDir = MedicalStringHelper.getPathByOS(saveDirectory);
        for (String fileName : listFiles(directory)) {
            sftp.get(fileName, localSaveDir);
        }
    }

    public void downloadWithPartition(String directory, String partitionDirectory,
                                      String saveDirectory) throws SftpException {
        if (!Strings.isNullOrEmpty(directory)) {
            sftp.cd(directory);
        }
        if (!Strings.isNullOrEmpty(partitionDirectory)) {
            try {
                sftp.cd(partitionDirectory);
            } catch (SftpException se) {
                logger.info(se.getMessage());
                return;
            }
        }
        String localSaveDir = MedicalStringHelper.getPathByOS(saveDirectory);
        for (String fileName : listFiles(directory + "/" + partitionDirectory)) {
            sftp.get(fileName, localSaveDir);
        }
    }

    public void delete(String directory, String deleteFile) throws SftpException {
        sftp.cd(directory);
        sftp.rm(deleteFile);
    }

    public ChannelSftp getSftp() {
        return sftp;
    }

    /**
     * 列出目录下所有文件。
     */
    public List<String> listFiles(String directory) throws SftpException {
        Vector<ChannelSftp.LsEntry> entries = sftp.ls(directory);
        return entries.stream()
                .filter(v -> !Objects.equals(v.getFilename(), ".") && !Objects.equals(v.getFilename(), ".."))
                .filter(v -> !v.getFilename().startsWith("."))
                .map(ChannelSftp.LsEntry::getFilename)
                .collect(Collectors.toList());
    }

    public void setSftp(ChannelSftp sftp) {
        this.sftp = sftp;
    }

    public Session getSession() {
        return session;
    }

    public void setSession(Session session) {
        this.session = session;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }
}
