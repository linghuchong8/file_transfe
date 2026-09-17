package com.transfer.medical.service;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.transfer.medical.mapper.MedicalDataShareMessageMapper;
import com.transfer.medical.util.MedicalAppPropertiesUtil;
import com.transfer.medical.util.MedicalFlowStatus;
import com.transfer.medical.util.MedicalHdfsFileClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Map;
import java.util.Objects;

/**
 * HDFS 上传服务。
 *
 * 负责把本地文件/目录同步到 HDFS 并更新数据库状态。
 *
 * @author yuanjihong
 */
@Service
@Slf4j
public class MedicalHdfsActionService {

    /** 需要追加通配路径的系统号。 */
    private static final String WILDCARD_SYS_CODE = "99710950000";

    private final MedicalDataShareMessageMapper shareMsgMapper;

    public MedicalHdfsActionService(MedicalDataShareMessageMapper shareMsgMapper) {
        this.shareMsgMapper = shareMsgMapper;
    }

    /**
     * 上传本地文件到 HDFS 并更新状态。
     *
     * @param sourcePath 本地源路径
     * @param targetPath HDFS 目标路径
     * @param params     状态更新参数
     * @throws Exception 上传失败时抛出
     */
    public synchronized void upload(String sourcePath, String targetPath,
                                    Map<String, Object> params) throws Exception {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(sourcePath), "源路径不能为空");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(targetPath), "目标路径不能为空");

        File source = new File(sourcePath);
        String kind = source.isFile() ? "文件" : "目录";
        log.info("开始上传{}【{}】", kind, source);

        params.put("sendHdfsFlag", MedicalFlowStatus.RUNNING);
        shareMsgMapper.updateMessage(params);

        try (MedicalHdfsFileClient client = new MedicalHdfsFileClient()) {
            boolean deleteSource = Boolean.parseBoolean(
                    MedicalAppPropertiesUtil.getProperty("send.hdfs.delete.src.flag", "true"));
            client.copyFromLocalFile(deleteSource, true, sourcePath, targetPath);

            params.put("sendHdfsFlag", MedicalFlowStatus.DONE);
            if (Objects.equals(params.get("sendSysCode"), WILDCARD_SYS_CODE)) {
                targetPath += "/*/*";
            }
            params.put("sendHdfsPath", targetPath);
            params.put("logMsg", "Send to hdfs successfully");
            shareMsgMapper.updateMessage(params);
            log.info("上传{}成功【{}】", kind, source);
        } catch (Exception e) {
            log.error("上传{}失败【{}】", kind, source, e);
            params.put("sendHdfsFlag", MedicalFlowStatus.ERROR);
            params.put("sendHdfsPath", null);
            params.put("logMsg", "Send to hdfs failed : " + e.getMessage());
            shareMsgMapper.updateMessage(params);
            throw e;
        }
    }
}
