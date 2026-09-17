package com.transfer.psbcx.service;

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.transfer.psbcx.mapper.TransferDataShareMessageMapper;
import com.transfer.psbcx.mapper.TransferSendFileInformationMapper;
import com.transfer.psbcx.pojo.TransferDataShareMessage;
import com.transfer.psbcx.pojo.TransferDownloadFileRequest;
import com.transfer.psbcx.pojo.TransferSendFileInformation;
import com.transfer.psbcx.util.TransferAppPropertiesUtil;
import com.transfer.psbcx.util.TransferFlowStatus;
import com.transfer.psbcx.util.TransferHdfsFileClient;
import com.transfer.psbcx.util.TransferStringHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * 基于 company_three 客户端接口的下载与下发服务。
 *
 * @author yuanjihong
 */
@Service
@Slf4j
public class TransferTransferRestActionService {

    @Autowired
    private TransferDataShareMessageMapper shareMsgMapper;
    @Autowired
    private TransferSendFileInformationMapper sendFileMapper;
    @Autowired
    private TransferTransferHttpClientService httpClientService;

    @Value("${recv.sys.code}")
    private String recvSysCode;
    @Value("${edb.sys.code}")
    private String edbSysCode;
    @Value("${server.name}")
    private String serverName;
    @Value("${sys.key}")
    private String sysKey;
    /** 0-文件路径方式下载 1-文件流方式下载。 */
    @Value("${receiveFile.stream.status}")
    private String receiveFileStreamStatus;
    /** 文件上传方式：1-文件路径 2-文件流。 */
    @Value("${uploadFiles.stream.status}")
    private String uploadStreamStatus;

    /**
     * 下载单个文件。
     *
     * @param msg 消息记录
     */
    @Async
    public void download(TransferDataShareMessage msg) {
        Preconditions.checkArgument(msg != null, "消息不能为空");
        String fileName = msg.getFileName();
        Preconditions.checkArgument(fileName != null, "文件名不能为空");
        String sendSysCode = msg.getSendSysCode();
        Preconditions.checkArgument(sendSysCode != null, "发送方系统编号不能为空");
        String fileDir = msg.getFileDir();
        Preconditions.checkArgument(fileDir != null, "存储文件路径不能为空");

        Map<String, Object> params = new HashMap<>();
        params.put("fileName", fileName);
        params.put("fileDir", fileDir);

        fileDir = TransferStringHelper.addEndSeparator(fileDir);
        File dir = new File(fileDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        TransferDownloadFileRequest request = new TransferDownloadFileRequest();
        request.setServerName(serverName);
        request.setSendSysCode(sendSysCode);
        request.setFileName(fileName);
        request.setRecvSysCode(recvSysCode);
        request.setSysKey(sysKey);
        request.setRecvDir(fileDir);
        request.setReceiveFileStreamStatus(receiveFileStreamStatus);
        request.setFileId(msg.getFileId());

        String requestJson = new Gson().toJson(request);
        log.info("[{}] 下载请求参数: {}", fileName, requestJson);

        try {
            if (httpClientService.downloadFile(requestJson, fileDir, receiveFileStreamStatus, fileName)) {
                params.put("logMsg", "下载成功");
                params.put("downFlag", TransferFlowStatus.DONE);
                shareMsgMapper.updateMessage(params);
                log.info("[{}] 下载成功", fileName);
                return;
            }
            throw new IllegalStateException("下载失败:数据共享无法下载");
        } catch (Exception e) {
            String logMsg;
            if (e instanceof IllegalStateException) {
                log.error("【{}】: {}", fileName, e.getMessage());
                logMsg = e.getMessage();
            } else {
                log.error("【{}】 下载失败 : {}", fileName, e.getMessage());
                logMsg = "下载失败";
            }
            params.put("logMsg", logMsg);
            params.put("downFlag", TransferFlowStatus.ERROR);
            shareMsgMapper.updateMessage(params);
        } catch (Throwable t) {
            log.error("【{}】 下载异常 : {}", fileName, t.getMessage());
            params.put("logMsg", "下载异常, 重试");
            params.put("downFlag", TransferFlowStatus.READY);
            shareMsgMapper.updateMessage(params);
        }
    }

    /**
     * 下发文件给其他系统。
     *
     * @param file 待下发文件记录
     */
    @Async
    public void push(TransferSendFileInformation file) {
        Preconditions.checkArgument(file != null, "文件记录不能为空");
        String fileName = file.getFileName();
        Preconditions.checkArgument(fileName != null, "文件名不能为空");
        String filePath = file.getFilePath();
        Preconditions.checkArgument(filePath != null, "文件路径不能为空");
        String sysCode = file.getSysCode();

        Map<String, Object> params = new HashMap<>();
        params.put("fileName", fileName);

        String interfaceName = extractServiceName(fileName);
        log.info("即将下发文件 {}, 接口名: {}", fileName, interfaceName);

        String hdfsFilePath = TransferAppPropertiesUtil.getProperty("task.upload.local.path.prefix",
                "/user/hdfs/transfile/") + filePath + "/" + fileName;
        log.info("HDFS 文件路径 {}", hdfsFilePath);
        String localDir = TransferAppPropertiesUtil.getProperty("data.share.download.dir.prefix") + "/" + filePath;
        String localFilePath = TransferStringHelper.addEndSeparator(localDir) + fileName;

        try (TransferHdfsFileClient client = new TransferHdfsFileClient()) {
            File dir = new File(localDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            if ("upload".equals(TransferAppPropertiesUtil.getProperty("task.upload.local.flag"))) {
                if (!client.exists(hdfsFilePath)) {
                    params.put("sendFlag", "04");
                    sendFileMapper.updatePushStatus(params);
                    log.error("【{}】文件在 HDFS 上不存在", hdfsFilePath);
                    return;
                }
                client.copyToLocalFile(false, hdfsFilePath, localFilePath);
                log.info("HDFS -> 本地成功, 存储于 {}", localFilePath);
            }

            log.info("开始下发文件到数据共享");
            params.put("sendFlag", TransferFlowStatus.RUNNING);
            sendFileMapper.updatePushStatus(params);

            String uploadJson = buildUploadJson(file, fileName, interfaceName, localFilePath, localDir, sysCode);
            if (httpClientService.uploadFile(uploadJson)) {
                params.put("sendFlag", TransferFlowStatus.DONE);
                sendFileMapper.updatePushStatus(params);
                log.info("{} 推送成功", localDir);
            } else {
                throw new IllegalStateException("推送到transfer失败");
            }
        } catch (Exception e) {
            log.error("【{}】 发送失败 : {}", fileName, e.toString());
            params.put("sendFlag", TransferFlowStatus.ERROR);
            params.put("sendTimes", file.getSendTimes() + 1);
            sendFileMapper.updatePushStatus(params);
        } finally {
            File temp = new File(localFilePath);
            if (temp.exists()) {
                if (temp.delete()) {
                    log.info("删除临时文件【{}】成功", localFilePath);
                } else {
                    log.info("删除临时文件【{}】失败", localFilePath);
                }
            }
        }
    }

    private String buildUploadJson(TransferSendFileInformation file, String fileName, String interfaceName,
                                   String localFilePath, String localDir, String sysCode) {
        return "{\n" +
                "\"serverName\": \"" + serverName + "\",\n" +
                "\"userName\": \"" + recvSysCode + "\",\n" +
                "\"sysKey\": \"" + sysKey + "\",\n" +
                "\"recvSysCode\": \"" + sysCode + "\",\n" +
                "\"uploadType\": \"" + uploadStreamStatus + "\",\n" +
                "\"filePath\": \"" + localFilePath + "\",\n" +
                "\"msgPropertiesInfo\": {\n" +
                "\"send_sys_code\": \"" + recvSysCode + "\",\n" +
                "\"interface_name\": \"" + interfaceName + "\",\n" +
                "\"data_name\": \"" + interfaceName + "\",\n" +
                "\"dateStr\": \"" + file.getSendDate() + "\",\n" +
                "\"timeStr\": \"" + file.getStartTime().substring(8) + "\",\n" +
                "\"file_dir\": \"" + localDir + "\",\n" +
                "\"han_type\": \"" + extractHanType(fileName) + "\"\n" +
                "\t\t\t\t}\n" +
                "\t}";
    }

    /**
     * 从文件名中提取增量标识（han_type）。
     *
     * 文件名格式：{sys_code}_{service_name}_{date}_{flag}_{seq1}_{seq2}.{ext}
     *
     * @param fileName 文件名
     * @return 增量标识（A=增量，I=全量），解析失败返回空串
     */
    private String extractHanType(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            log.warn("文件名为空, 无法提取增量标识");
            return "";
        }
        try {
            String nameWithoutExt = fileName.contains(".")
                    ? fileName.substring(0, fileName.lastIndexOf("."))
                    : fileName;
            String[] parts = nameWithoutExt.split("_");
            if (parts.length < 3) {
                log.warn("文件名格式不符合预期, 无法提取增量标识: {}", fileName);
                return "";
            }
            String hanType = parts[parts.length - 3];
            log.debug("从文件名 {} 中提取增量标识: {}", fileName, hanType);
            return hanType;
        } catch (Exception e) {
            log.error("提取增量标识失败, 文件名: {}", fileName, e);
            return "";
        }
    }

    /**
     * 从文件名中提取接口名称（service_name）。
     *
     * service_name 可能包含下划线，通过固定后缀段定位。
     *
     * @param fileName 文件名
     * @return 接口名称，格式不合法返回 null
     */
    public static String extractServiceName(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return null;
        }
        int dotIndex = fileName.lastIndexOf(".");
        if (dotIndex == -1) {
            return null;
        }
        String nameWithoutExt = fileName.substring(0, dotIndex);

        int idx = nameWithoutExt.length();
        for (int i = 0; i < 5; i++) {
            idx = nameWithoutExt.lastIndexOf('_', idx - 1);
            if (idx == -1) {
                return null;
            }
        }
        int firstUnderscore = nameWithoutExt.indexOf('_');
        if (firstUnderscore == -1 || firstUnderscore >= idx) {
            return null;
        }
        return nameWithoutExt.substring(firstUnderscore + 1, idx);
    }
}
