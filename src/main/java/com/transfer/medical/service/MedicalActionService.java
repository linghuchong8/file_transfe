package com.transfer.medical.service;

import com.google.common.base.Preconditions;
import com.google.common.io.Files;
import com.transfer.medical.mapper.MedicalDataShareMessageMapper;
import com.transfer.medical.mapper.MedicalSendFileInformationMapper;
import com.transfer.medical.mapper.MedicalVerifyContentMapper;
import com.transfer.medical.pojo.MedicalDataShareMessage;
import com.transfer.medical.pojo.MedicalDataVerifyMessage;
import com.transfer.medical.pojo.MedicalSendFileInformation;
import com.transfer.medical.pojo.MedicalVerifyContent;
import com.transfer.medical.util.MedicalAppPropertiesUtil;
import com.transfer.medical.util.MedicalFlowStatus;
import com.transfer.medical.util.MedicalFileEncodingDetector;
import com.transfer.medical.util.MedicalFileTools;
import com.transfer.medical.util.MedicalHdfsFileClient;
import com.transfer.medical.util.MedicalStringHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文件下载、处理、上传与下发的核心业务服务。
 */
@Service
@Slf4j
public class MedicalActionService {

    /** 校验文件每行的文件名格式。 */
    private static final String VERF_LINE_REGEX =
            "^(\\d{11})_(.+)_(\\d{4})_(\\d{8})_([ZAI])_(\\d{4})_(\\d{4}).(.+)";

    /** 医保结算系统发送方系统号。 */
    private static final String FINANCE_SYS_CODE = "99370000000";

    @Autowired
    private GetFileService fileService;
    @Autowired
    private MedicalDataShareMessageMapper shareMsgMapper;
    @Autowired
    private MedicalPutService putService;
    @Autowired
    private MedicalSendFileInformationMapper sendFileMapper;
    @Autowired
    private MedicalVerifyContentMapper verifyContentMapper;

    /**
     * 下载文件。
     *
     * @param msg 消息记录
     */
    @Async
    public void download(MedicalDataShareMessage msg) {
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
        log.info("开始下载文件: {}", fileName);

        fileDir = MedicalStringHelper.addEndSeparator(fileDir);
        File dir = new File(fileDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        try {
            if (!fileService.getFile(fileName, fileDir, sendSysCode)) {
                throw new IllegalStateException("下载失败:数据共享无法下载");
            }
            params.put("logMsg", "下载成功");
            params.put("downFlag", MedicalFlowStatus.DONE);
            shareMsgMapper.updateMessage(params);
            log.info("[{}] 下载成功", fileName);
        } catch (Exception e) {
            params.put("logMsg", "下载失败");
            if (e instanceof IllegalStateException) {
                params.put("logMsg", e.getMessage());
            }
            log.error("【{}】 下载失败 : {}", fileName, e.getMessage());
            params.put("downFlag", MedicalFlowStatus.ERROR);
            shareMsgMapper.updateMessage(params);
        } catch (Throwable t) {
            log.error("【{}】 下载异常 : {}", fileName, t.getMessage());
            params.put("logMsg", "下载异常, 重试");
            params.put("downFlag", MedicalFlowStatus.READY);
            shareMsgMapper.updateMessage(params);
        }
    }

    /**
     * 上传文件到 HDFS。
     *
     * @param msg 消息记录
     */
    @Async
    public void sendHdfs(MedicalDataShareMessage msg) {
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
        log.info("开始上传文件 {}", fileName);

        String srcPath = MedicalStringHelper.addEndSeparator(fileDir).replace("D:", "");
        if (fileName.contains(".gz")) {
            fileName = fileName.replace(".gz", "");
        }
        String targetPath = MedicalAppPropertiesUtil.getProperty("task.process.hdfs.path.prefix", "/user/hdfs")
                + fileDir.substring(0, fileDir.lastIndexOf("/") + 1)
                + fileName.replace("." + Files.getFileExtension(fileName), ".txt");
        log.info("目标路径 {}", targetPath);

        srcPath = srcPath + fileName;
        if (srcPath.contains(".gz")) {
            srcPath = srcPath.replace(".gz", "");
        }
        srcPath = srcPath.replace("." + Files.getFileExtension(srcPath), ".txt");
        log.info("源路径 {}", srcPath);

        boolean deleteSource = Boolean.parseBoolean(
                MedicalAppPropertiesUtil.getProperty("send.hdfs.delete.src.flag", "true"));

        try (MedicalHdfsFileClient client = new MedicalHdfsFileClient()) {
            client.copyFromLocalFile(deleteSource, true, srcPath, targetPath);
            params.put("logMsg", "上传成功");
            params.put("sendHdfsFlag", MedicalFlowStatus.DONE);
            params.put("sendHdfsPath", targetPath);
            shareMsgMapper.updateMessage(params);
        } catch (Exception e) {
            log.error("文件 {} 上传失败", fileName, e);
            params.put("logMsg", "上传失败");
            params.put("sendHdfsFlag", MedicalFlowStatus.ERROR);
            shareMsgMapper.updateMessage(params);
        }
        log.info("[{}] 上传完毕", fileName);
    }

    /**
     * 处理文件：解压并转换为 txt。
     *
     * @param msg 消息记录
     */
    @Async
    public void process(MedicalDataShareMessage msg) {
        Preconditions.checkArgument(msg != null, "消息不能为空");
        String fileName = msg.getFileName();
        Preconditions.checkArgument(fileName != null, "文件名不能为空");
        String sendSysCode = msg.getSendSysCode();
        Preconditions.checkArgument(sendSysCode != null, "发送方系统编号不能为空");
        String fileDir = msg.getFileDir();
        Preconditions.checkArgument(fileDir != null, "存储文件路径不能为空");
        String txDate = msg.getTxDate();
        Preconditions.checkArgument(txDate != null, "日期不能为空");
        String unitName = msg.getUnitName();
        Preconditions.checkArgument(unitName != null, "unitName不能为空");
        String createDate = msg.getCreateDate();
        Preconditions.checkArgument(createDate != null, "createDate不能为空");
        String createTime = msg.getCreateTime();
        Preconditions.checkArgument(createTime != null, "createTime不能为空");

        Map<String, Object> params = new HashMap<>();
        params.put("fileName", fileName);
        params.put("fileDir", fileDir);
        log.info("开始处理文件 {}", fileName);

        try {
            String sourcePath = MedicalStringHelper.addEndSeparator(fileDir) + fileName;
            String targetPath = "";
            String fileCode = resolveFileCode(unitName);
            String specFile = resolveSpecialFile(unitName);
            boolean special = !"".equals(specFile);

            String extension = Files.getFileExtension(fileName);
            if (Objects.equals(extension, "gz")) {
                String xmlPath = sourcePath.replace(".gz", "");
                MedicalFileTools.decompress(sourcePath, xmlPath, true);
                targetPath = xmlPath.replace(".xml", ".txt");
                if (special) {
                    MedicalFileTools.xmlSpectxt(xmlPath, targetPath, fileCode, specFile);
                } else {
                    MedicalFileTools.xml2txt(xmlPath, targetPath, fileCode);
                }
            } else if (Objects.equals(extension, "dat")) {
                targetPath = sourcePath.replace(".dat", ".txt");
                MedicalFileTools.dat2txt(sourcePath, targetPath);
            } else if (Objects.equals(extension, "xml")) {
                targetPath = sourcePath.replace(".xml", ".txt");
                if (FINANCE_SYS_CODE.equals(sendSysCode) && !special) {
                    MedicalFileTools.xml2txtForFinance(sourcePath, targetPath, fileCode);
                } else if (special) {
                    MedicalFileTools.xmlSpectxt(sourcePath, targetPath, fileCode, specFile);
                } else {
                    MedicalFileTools.xml2txt(sourcePath, targetPath, fileCode);
                }
            } else if (Objects.equals(extension, "csv")) {
                targetPath = sourcePath.replace(".csv", ".txt");
                MedicalFileTools.csv2txt(sourcePath, targetPath, fileCode);
            } else if (Objects.equals(extension, "verf")) {
                parseVerfFile(sourcePath, fileName, fileCode, unitName, createDate, createTime);
            } else {
                throw new RuntimeException("文件名不合法");
            }

            long fileByte = 0;
            String processFlag = MedicalFlowStatus.DONE;
            String logMsg = "数据处理成功";
            if (!"".equals(targetPath)) {
                File targetFile = new File(targetPath);
                if (!targetFile.exists() || !targetFile.isFile()) {
                    fileByte = -1;
                    processFlag = MedicalFlowStatus.ERROR;
                    logMsg = "数据处理异常, 未生成目标txt文件";
                    log.error("数据处理异常, 未生成目标txt文件: {}", targetFile);
                } else {
                    fileByte = targetFile.length();
                    log.info("生成目标文件 {} 字节数 {}", targetFile, fileByte);
                }
            }

            params.put("logMsg", logMsg);
            params.put("processFlag", processFlag);
            params.put("fileByte", fileByte);
            shareMsgMapper.updateMessage(params);
            log.info("【{}】 处理成功", fileName);
        } catch (Exception e) {
            log.error("【{}】 处理失败 : {}", fileName, e.getMessage());
            params.put("logMsg", "数据处理失败");
            params.put("processFlag", MedicalFlowStatus.ERROR);
            shareMsgMapper.updateMessage(params);
        } catch (Throwable t) {
            log.error("【{}】 处理异常, 再次重试 : {}", fileName, t.getMessage());
            params.put("logMsg", "数据处理异常, 再次重试");
            params.put("processFlag", MedicalFlowStatus.READY);
            shareMsgMapper.updateMessage(params);
        }
        log.info("[{}] 处理完毕", fileName);
    }

    private String resolveFileCode(String unitName) {
        try {
            String code = shareMsgMapper.selectFileCode(unitName);
            if (!"".equals(code) && code != null) {
                return code;
            }
        } catch (Exception e) {
            log.error("查询文件字符集失败: {}", e.getMessage());
        }
        return "";
    }

    private String resolveSpecialFile(String unitName) {
        try {
            String file = shareMsgMapper.selectSpecialFile(unitName);
            if (!"".equals(file) && file != null) {
                return file;
            }
        } catch (Exception e) {
            log.error("查询特殊文件配置失败: {}", e.getMessage());
        }
        return "";
    }

    /**
     * 解析校验文件，把每行登记到 verf_content。
     */
    private void parseVerfFile(String sourcePath, String fileName, String fileCode,
                               String unitName, String createDate, String createTime) throws Exception {
        Pattern pattern = Pattern.compile(VERF_LINE_REGEX);
        List<String> lines = FileUtils.readLines(new File(sourcePath),
                MedicalFileEncodingDetector.getJavaEncode(sourcePath, fileCode));
        int lineNo = 0;
        for (String line : lines) {
            log.info("校验文件 {} 第 {} 行内容 {}", fileName, lineNo, line);
            if (!line.contains(".check") && line.trim().length() != 0) {
                String[] fields = line.split(" ", -1);
                if (fields.length < 3) {
                    log.error("{} 第 {} 行 {} 不满足 '文件名 字节大小 记录数' 格式", fileName, lineNo, line);
                } else {
                    String dataFileName = fields[0];
                    Matcher matcher = pattern.matcher(dataFileName);
                    if (matcher.matches()) {
                        MedicalVerifyContent content = new MedicalVerifyContent();
                        content.setUnitName(unitName);
                        content.setFileName(fileName);
                        content.setByteMum(fields[1]);
                        content.setRecordCount(fields[2]);
                        content.setDatestr(createDate);
                        content.setTimestr(createTime);
                        verifyContentMapper.insertDetail(content);
                    } else {
                        log.info("{} 第 {} 行文件名 {} 不符合命名规范", fileName, lineNo, dataFileName);
                    }
                }
            }
            lineNo++;
        }
    }

    /**
     * 通过 Medical 下发文件给其他系统。
     *
     * @param file 待下发文件记录
     */
    @Async
    public void push(MedicalSendFileInformation file) {
        Preconditions.checkArgument(file != null, "文件记录不能为空");
        String fileName = file.getFileName();
        Preconditions.checkArgument(fileName != null, "文件名不能为空");
        String filePath = file.getFilePath();
        Preconditions.checkArgument(filePath != null, "文件路径不能为空");

        Map<String, Object> params = new HashMap<>();
        params.put("fileName", fileName);

        String hdfsFilePath = MedicalAppPropertiesUtil.getProperty("task.upload.local.path.prefix",
                "/user/hdfs/transfile/") + filePath + "/" + fileName;
        log.info("HDFS 文件路径 {}", hdfsFilePath);
        String localDir = MedicalAppPropertiesUtil.getProperty("data.share.download.dir.prefix") + "/" + filePath;
        String localFilePath = MedicalStringHelper.addEndSeparator(localDir) + fileName;

        try (MedicalHdfsFileClient client = new MedicalHdfsFileClient()) {
            File dir = new File(localDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            if ("upload".equals(MedicalAppPropertiesUtil.getProperty("task.upload.local.flag"))) {
                if (!client.exists(hdfsFilePath)) {
                    params.put("sendFlag", "04");
                    sendFileMapper.updatePushStatus(params);
                    log.error("【{}】文件在 HDFS 上不存在", hdfsFilePath);
                    return;
                }
                client.copyToLocalFile(false, hdfsFilePath, localFilePath);
                log.info("HDFS -> 本地成功, 存储于 {}", localFilePath);
            }

            params.put("sendFlag", MedicalFlowStatus.RUNNING);
            sendFileMapper.updatePushStatus(params);

            if (putService.transferPut(localFilePath) == 0) {
                params.put("sendFlag", MedicalFlowStatus.DONE);
                sendFileMapper.updatePushStatus(params);
                log.info("{} 推送成功", localDir);
            } else {
                throw new IllegalStateException("推送到transfer失败");
            }
        } catch (Exception e) {
            log.error("【{}】 发送失败 : {}", fileName, e.toString());
            params.put("sendFlag", MedicalFlowStatus.ERROR);
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

    /**
     * 下载并解析校验文件。
     *
     * @param msg 校验消息
     */
    public void downloadVerify(MedicalDataVerifyMessage msg) {
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
        if (fileName.endsWith(".verf")) {
            params.put("fileType", "00");
        }
        params.put("unitName", msg.getUnitName());
        params.put("txDate", msg.getTxDate());
        params.put("logMsg", "下载中");
        params.put("downFlag", MedicalFlowStatus.RUNNING);
        shareMsgMapper.updateVerifyMessage(params);
        log.info("开始下载文件: {}", fileName);

        fileDir = MedicalStringHelper.addEndSeparator(fileDir);
        File dir = new File(fileDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        try {
            if (!fileService.getFile(fileName, fileDir, sendSysCode)) {
                throw new IllegalStateException("下载失败:数据共享无法下载");
            }
            String sourcePath = MedicalStringHelper.addEndSeparator(fileDir) + fileName;
            String fileDesc = collectVerfDescription(sourcePath, fileName);
            int fileNum = countVerfLines(fileDesc);

            params.put("fileDesc", fileDesc);
            params.put("fileNum", String.valueOf(fileNum));
            params.put("logMsg", "下载成功");
            params.put("downFlag", MedicalFlowStatus.DONE);
            shareMsgMapper.updateVerifyMessage(params);
            log.info("【{}】下载成功", fileName);
        } catch (Exception e) {
            String logMsg = "下载失败";
            if (e instanceof IllegalStateException) {
                logMsg = e.getMessage();
            }
            log.error("【{}】 下载失败 : {}", fileName, e.getMessage());
            params.put("logMsg", logMsg);
            params.put("downFlag", MedicalFlowStatus.ERROR);
            shareMsgMapper.updateVerifyMessage(params);
        } catch (Throwable t) {
            log.error("【{}】 下载异常 : {}", fileName, t.getMessage());
            params.put("logMsg", "下载异常, 重试");
            params.put("downFlag", MedicalFlowStatus.READY);
            shareMsgMapper.updateVerifyMessage(params);
        }
        log.info("[{}] 下载完毕", fileName);
    }

    private String collectVerfDescription(String sourcePath, String fileName) throws Exception {
        Pattern pattern = Pattern.compile(VERF_LINE_REGEX);
        List<String> lines = FileUtils.readLines(new File(sourcePath),
                MedicalFileEncodingDetector.getJavaEncode(sourcePath));
        StringBuilder description = new StringBuilder();
        for (String line : lines) {
            if (line.contains(".check") || line.trim().length() == 0) {
                continue;
            }
            String dataFileName = line.split(" ")[0];
            if (pattern.matcher(dataFileName).matches()) {
                description.append(line).append("\n");
            } else {
                log.info("{} 行内容 {} 中文件名 {} 不符合命名规范", fileName, line, dataFileName);
            }
        }
        return description.toString();
    }

    private int countVerfLines(String fileDesc) {
        int count = 0;
        for (String line : fileDesc.split("\n")) {
            if (!line.isEmpty()) {
                count++;
            }
        }
        return count;
    }
}
