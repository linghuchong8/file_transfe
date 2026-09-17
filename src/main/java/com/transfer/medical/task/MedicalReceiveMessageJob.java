package com.transfer.medical.task;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.transfer.medical.enums.MedicalDataFileCategory;
import com.transfer.medical.mapper.MedicalDataShareMessageMapper;
import com.transfer.medical.pojo.MedicalDataShareMessage;
import com.transfer.medical.service.MedicalHttpClientService;
import com.transfer.medical.util.MedicalAppPropertiesUtil;
import com.transfer.medical.util.MedicalStringHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 共享消息接收任务。
 *
 * 定时调用 EDB 客户端接口拉取文件通知，解析后写入 pm_data_share_msg 并回执。
 *
 * @author yuanjihong
 */
@Component
@EnableScheduling
@Slf4j
public class MedicalReceiveMessageJob implements MedicalJob {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss-SSS");
    private static final String ACK_BODY = "{\"messageAckId\":\"%s\",\"sysKey\":\"%s\"}";

    @Autowired
    private MedicalDataShareMessageMapper shareMsgMapper;
    @Autowired
    private MedicalHttpClientService httpClientService;

    @Value("${sys.key}")
    private String sysKey;
    @Value("${dest.name}")
    private String destName;
    @Value("${server.name}")
    private String serverName;
    @Value("${autoReceiveMessage.interface}")
    private String autoReceiveInterface;
    @Value("${receiveMessage.interface}")
    private String manualReceiveInterface;
    @Value("${is.autoReceiveMessage}")
    private String autoAckSwitch;

    private final String downloadRoot;

    public MedicalReceiveMessageJob() {
        downloadRoot = MedicalAppPropertiesUtil.getProperty("data.share.download.dir.prefix");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(downloadRoot), "%s 不能为空", "filePathPrefix");
    }

    /**
     * 拉取并处理共享消息。
     */
    @Scheduled(initialDelayString = "${scheduled.task.receive.initialDelay}",
            fixedRateString = "${scheduled.task.receive.fixedRate}")
    @Override
    public synchronized void run() {
        try {
            JSONObject request = new JSONObject();
            request.put("serverName", serverName);
            request.put("sysKey", sysKey);
            request.put("destName", destName);

            String receiveInterface = "1".equals(autoAckSwitch) ? autoReceiveInterface : manualReceiveInterface;
            log.info("接收接口: {}, 自动回执开关: {}", receiveInterface, autoAckSwitch);

            int rounds = shareMsgMapper.countMessages();
            for (int round = 1; round <= rounds; round++) {
                JSONObject response = httpClientService.receiveMessages(request.toJSONString(), receiveInterface);
                if (response == null) {
                    log.info("消息响应体为空，结束本次接收");
                    return;
                }
                dispatchMessages(response);
            }
            log.info("本次消息接收循环结束, 预期轮次: {}", rounds);
        } catch (Exception e) {
            log.error("接收消息异常", e);
        }
    }

    /**
     * 处理单批消息。
     */
    private void dispatchMessages(JSONObject response) throws Exception {
        JSONArray messages = (JSONArray) response.get("data");
        if (messages == null || messages.isEmpty()) {
            log.info("没有可接收的消息");
            return;
        }
        JSONObject commonInfo = (JSONObject) response.get("commonInfo");
        String respServiceIp = commonInfo.getString("respServiceIp");
        log.info("消息来源服务地址: {}", respServiceIp);

        for (Object item : messages) {
            JSONObject message = (JSONObject) item;
            String fileName = message.getString("File_name");
            log.info("处理消息文件: {}", fileName);

            if (fileName.endsWith(".check")) {
                String ackId = message.getString("messageAckId");
                if (StringUtils.isNotEmpty(ackId)) {
                    sendAck(ackId, respServiceIp);
                }
                log.info("check 文件忽略: {}", fileName);
                continue;
            }

            String serviceName = MedicalStringHelper.getWorkflowCode(fileName, "", 2);
            String cityNo = MedicalStringHelper.getWorkflowCode(fileName, "", 3);
            String sendSysCode = resolveSendSysCode(message, fileName);
            String unitName = sendSysCode + "_" + serviceName + "_" + cityNo;
            String txDate = resolveTxDate(fileName, serviceName, unitName);

            LocalDateTime now = LocalDateTime.now();
            String createDate = now.format(DATE_FORMAT);
            String createTime = now.format(TIME_FORMAT);
            String fileNum = message.getString("File_num");
            log.info("接口 {} 日期 {} 文件 {} 个数 {}", unitName, txDate, fileName, fileNum);

            String fileType;
            String fileDir;
            if (fileName.endsWith(".verf")) {
                fileType = MedicalDataFileCategory.VERF.getCode();
                fileDir = MedicalStringHelper.addEndSeparator(downloadRoot) + txDate + "/VERF/"
                        + sendSysCode + "/" + unitName + "/" + createTime;
            } else {
                fileType = MedicalDataFileCategory.DATA.getCode();
                fileDir = MedicalStringHelper.addEndSeparator(downloadRoot) + txDate + "/"
                        + sendSysCode + "/" + unitName + "/" + createTime;
            }

            String messageAckId = message.getString("messageAckId");
            MedicalDataShareMessage entity = buildEntity(message, fileName, serviceName, sendSysCode,
                    unitName, txDate, createDate, createTime, fileDir, fileNum, fileType);

            if (isDuplicated(entity)) {
                log.info("消息已入库, 跳过重复入库, fileId={}", entity.getFileId());
                sendAck(messageAckId, respServiceIp);
                continue;
            }
            shareMsgMapper.insertMessage(entity);
            log.info("文件消息入库成功: {}", fileName);
            sendAck(messageAckId, respServiceIp);
        }
    }

    private MedicalDataShareMessage buildEntity(JSONObject message, String fileName, String serviceName,
                                         String sendSysCode, String unitName, String txDate,
                                         String createDate, String createTime, String fileDir,
                                         String fileNum, String fileType) {
        MedicalDataShareMessage entity = new MedicalDataShareMessage();
        entity.setFileName(fileName);
        entity.setServiceName(serviceName);
        entity.setSendSysCode(sendSysCode);
        entity.setUnitName(unitName);
        entity.setTxDate(txDate);
        entity.setCreateDate(createDate);
        entity.setCreateTime(createTime);
        entity.setFileDir(fileDir);
        entity.setFileNum(fileNum);
        entity.setFileType(fileType);
        entity.setFileId(message.getString("File_id"));
        entity.setRestartTimes(0);
        return entity;
    }

    /**
     * 依据消息 File_id 做幂等判断。
     */
    private boolean isDuplicated(MedicalDataShareMessage entity) {
        int distinctFileIds = shareMsgMapper.countDistinctFileId(entity);
        String storedFileId = shareMsgMapper.selectFileId(entity);
        return distinctFileIds > 1
                && !"".equals(entity.getFileId())
                && entity.getFileId().equals(storedFileId);
    }

    /**
     * 解析发送方系统号，缺失时按文件名映射补充。
     */
    private String resolveSendSysCode(JSONObject message, String fileName) {
        String sendSysCode = message.getString("Send_sys_code");
        if (!Strings.isNullOrEmpty(sendSysCode)) {
            return sendSysCode;
        }
        sendSysCode = MedicalStringHelper.getWorkflowCode(fileName, "", 1);
        String mappingJson = MedicalAppPropertiesUtil.getProperty("interface.filename.syscode.map");
        Map<String, String> mapping = new Gson().fromJson(mappingJson, HashMap.class);
        if (mapping != null) {
            for (Map.Entry<String, String> entry : mapping.entrySet()) {
                if (fileName.startsWith(entry.getKey())) {
                    return entry.getValue();
                }
            }
        }
        return sendSysCode;
    }

    /**
     * 解析业务日期，部分接口按 T-1 处理。
     */
    private String resolveTxDate(String fileName, String serviceName, String unitName) throws Exception {
        String txDate = MedicalStringHelper.getWorkflowCode(fileName, "", 4);
        boolean shiftDate = "99710930001_611120".equals(serviceName)
                || "JGQL".equals(serviceName)
                || "JGGL-RGLM".equals(serviceName)
                || "611170".equals(serviceName)
                || unitName.contains("99700060000");
        if (!shiftDate) {
            return txDate;
        }
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd");
        Date date = format.parse(txDate);
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.DATE, -1);
        String shifted = format.format(calendar.getTime());
        log.info("{} 文件日期调整为 {}", fileName, shifted);
        return shifted;
    }

    private void sendAck(String messageAckId, String respServiceIp) {
        httpClientService.ackMessage(String.format(ACK_BODY, messageAckId, sysKey), respServiceIp);
    }
}
