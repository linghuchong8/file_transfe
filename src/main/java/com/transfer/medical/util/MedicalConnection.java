package com.transfer.medical.util;

import com.nantian.eftp.app.EFTP_APP;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ResourceBundle;

/**
 * Medical 连接管理。
 */
public class MedicalConnection {

    private static final Logger log = LoggerFactory.getLogger(MedicalConnection.class);

    /** 数据共享系统编码。 */
    private static final String DATASHARE_SYS_CODE = "99700070000";

    private static final String SERVER_NAME = "server1";

    private final String username;
    private String password;

    public MedicalConnection() {
        log.info("初始化 Medical 连接参数");
        ResourceBundle bundle = ResourceBundle.getBundle("cpdsmsgj2");
        this.username = bundle.getString("server1.username");
    }

    /**
     * 使用数据共享系统编码建立连接。
     */
    public int getConnection(EFTP_APP transfer) {
        return getConnection(transfer, DATASHARE_SYS_CODE);
    }

    /**
     * 使用指定系统编码建立连接。
     */
    public int getConnection(EFTP_APP transfer, String sysCode) {
        int openResult = transfer.eftp_open(SERVER_NAME, username, password, sysCode);
        if (openResult > 0) {
            log.info("连接成功");
        } else {
            log.info("连接失败，重置连接" + openResult);
        }
        return openResult;
    }

    /**
     * 关闭 Medical 连接。
     */
    public void closeConn(EFTP_APP transfer, int result) {
        int res = transfer.eftp_close(result);
        if (res == 0) {
            log.info("Medical连接关闭成功");
        } else {
            log.info("Medical连接关闭失败" + res);
        }
    }
}
