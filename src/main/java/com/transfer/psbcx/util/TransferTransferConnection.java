package com.transfer.psbcx.util;

import com.nantian.eftp.app.EFTP_APP;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ResourceBundle;

/**
 * Transfer 连接管理。
 */
public class TransferTransferConnection {

    private static final Logger log = LoggerFactory.getLogger(TransferTransferConnection.class);

    /** 数据共享系统编码。 */
    private static final String DATASHARE_SYS_CODE = "99700070000";

    private static final String SERVER_NAME = "server1";

    private final String username;
    private String password;

    public TransferTransferConnection() {
        log.info("初始化 Transfer 连接参数");
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
     * 关闭 Transfer 连接。
     */
    public void closeConn(EFTP_APP transfer, int result) {
        int res = transfer.eftp_close(result);
        if (res == 0) {
            log.info("Transfer连接关闭成功");
        } else {
            log.info("Transfer连接关闭失败" + res);
        }
    }
}
