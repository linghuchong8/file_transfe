package com.transfer.psbcx.service;

import com.nantian.eftp.app.EFTP_APP;
import com.nantian.eftp.ems.MsgPropertiesInfo;
import com.transfer.psbcx.util.TransferTransferConnection;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Transfer 文件传送服务。
 *
 * 封装 Transfer 连接建立、文件上传与断链重连逻辑。
 *
 * @author wanglei
 */
@Service
@Slf4j
public class TransferTransferPutService {

    /** 长链接断开返回值。 */
    private static final int CONNECTION_BROKEN = -3;

    /** Transfer 上传动作。 */
    @FunctionalInterface
    private interface TransferPutAction {
        int apply(EFTP_APP app, int handle) throws Exception;
    }

    public int transferPut(String filePathAndName) {
        EFTP_APP app = new EFTP_APP();
        TransferTransferConnection connection = new TransferTransferConnection();
        int handle = connection.getConnection(app);
        return transfer(connection, app, handle, (a, h) -> a.eftp_put(h, filePathAndName));
    }

    public int transferPut(String filePathAndName, String sysCode) {
        EFTP_APP app = new EFTP_APP();
        TransferTransferConnection connection = new TransferTransferConnection();
        int handle = connection.getConnection(app);
        return transfer(connection, app, handle, (a, h) -> a.eftp_put(h, filePathAndName));
    }

    public int transferPut(String filePathAndName, MsgPropertiesInfo msgProperties) {
        EFTP_APP app = new EFTP_APP();
        TransferTransferConnection connection = new TransferTransferConnection();
        int handle = connection.getConnection(app);
        return transfer(connection, app, handle, (a, h) -> a.eftp_put(h, msgProperties));
    }

    public int transferPutConn(String filePathAndName) {
        EFTP_APP app = new EFTP_APP();
        TransferTransferConnection connection = new TransferTransferConnection();
        int handle = connectWithRetry(connection, app);
        return transfer(connection, app, handle, (a, h) -> a.eftp_put(h, filePathAndName));
    }

    /**
     * 执行一次上传，处理断链重连。
     */
    private int transfer(TransferTransferConnection connection, EFTP_APP app, int handle, TransferPutAction action) {
        int result = -1;
        try {
            if (handle <= 0) {
                System.out.println("open failed!");
                return result;
            }
            System.out.println("open successed!");

            int putCode = action.apply(app, handle);
            if (putCode == 0) {
                result = putCode;
                System.out.println("syn info put successed!");
            } else if (putCode == CONNECTION_BROKEN) {
                System.out.println("conn disconnect!");
                connection.closeConn(app, handle);
                handle = connection.getConnection(app);
                if (handle > 0) {
                    System.out.println("reconnect successed!");
                    action.apply(app, handle);
                } else {
                    System.out.println("reconnect failed!");
                }
            } else {
                System.out.println("syn info put failed!");
            }
            connection.closeConn(app, handle);
        } catch (Exception e) {
            log.error(e.getMessage());
        } finally {
            connection.closeConn(app, handle);
        }
        return result;
    }

    /**
     * 连接失败时循环重试，直到成功。
     */
    private int connectWithRetry(TransferTransferConnection connection, EFTP_APP app) {
        int handle = connection.getConnection(app);
        int attempt = 0;
        while (handle <= 0) {
            try {
                attempt++;
                System.out.println("open failed!openResult:" + handle);
                Thread.sleep(1000);
                System.out.println("create connection again!times[;" + attempt + "]");
                handle = connection.getConnection(app);
            } catch (InterruptedException e) {
                System.out.println("checkConn exception!" + e.getMessage());
                Thread.currentThread().interrupt();
            }
        }
        return handle;
    }
}
