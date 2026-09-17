package com.transfer.medical.util;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.security.UserGroupInformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.PrivilegedExceptionAction;

/**
 * HDFS 客户端封装，支持 Kerberos 认证与 NameNode HA。
 */
public class MedicalHdfsFileClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(MedicalHdfsFileClient.class);

    private static final String NAMESERVICE = "namenodeHA";
    private static final String NAMENODES = "nn1,nn2";
    private static final String FAILOVER_PROVIDER =
            "org.apache.hadoop.hdfs.server.namenode.ha.ConfiguredFailoverProxyProvider";

    private FileSystem fileSystem;

    public MedicalHdfsFileClient() {
        Configuration configuration = new Configuration();
        System.setProperty("java.security.krb5.conf", MedicalAppPropertiesUtil.getProperty("hadoop.krb.path"));
        configuration.set("hadoop.security.authentication", "kerberos");
        configuration.set("fs.hdfs.impl", "org.apache.hadoop.hdfs.DistributedFileSystem");
        configuration.set("fs.defaultFS", MedicalAppPropertiesUtil.getProperty("hadoop.name.node"));
        configuration.set("dfs.nameservices", NAMESERVICE);
        configuration.set("dfs.ha.namenodes." + NAMESERVICE, NAMENODES);
        configuration.set("dfs.namenode.rpc-address." + NAMESERVICE + ".nn1",
                MedicalAppPropertiesUtil.getProperty("hadoop.name.host1"));
        configuration.set("dfs.namenode.rpc-address." + NAMESERVICE + ".nn2",
                MedicalAppPropertiesUtil.getProperty("hadoop.name.host2"));
        configuration.set("dfs.client.failover.proxy.provider." + NAMESERVICE, FAILOVER_PROVIDER);
        try {
            UserGroupInformation.setConfiguration(configuration);
            UserGroupInformation.loginUserFromKeytab(
                    MedicalAppPropertiesUtil.getProperty("hadoop.principal.name"),
                    MedicalAppPropertiesUtil.getProperty("hadoop.keytab.path"));
            UserGroupInformation loginUser = UserGroupInformation.getLoginUser();
            this.fileSystem = loginUser.doAs(
                    (PrivilegedExceptionAction<FileSystem>) () -> FileSystem.get(configuration));
        } catch (Exception e) {
            log.error("初始化 HDFS 客户端异常, 原因: " + e.getMessage());
        }
    }

    public void copyFromLocalFile(boolean delSrc, boolean overwrite, String src, String dst) throws IOException {
        if (exists(dst)) {
            delete(dst);
        }
        fileSystem.copyFromLocalFile(delSrc, overwrite, new Path(src), new Path(dst));
    }

    public void copyToLocalFile(boolean isDelete, String src, String dst) throws IOException {
        fileSystem.copyToLocalFile(isDelete, new Path(src), new Path(dst));
    }

    public boolean exists(String path) {
        try {
            return fileSystem.exists(new Path(path));
        } catch (IOException e) {
            throw new RuntimeException("copy fail from hdfs", e);
        }
    }

    @Override
    public void close() {
        try {
            if (fileSystem != null) {
                fileSystem.close();
            }
        } catch (IOException e) {
            log.error("文件系统关闭异常, 原因: " + e.getMessage());
        }
    }

    public boolean delete(String path) throws IOException {
        return fileSystem.delete(new Path(path), true);
    }

    public boolean mkdirs(String path) throws IOException {
        return fileSystem.mkdirs(new Path(path));
    }
}
