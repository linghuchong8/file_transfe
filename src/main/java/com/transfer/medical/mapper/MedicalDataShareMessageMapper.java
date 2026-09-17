package com.transfer.medical.mapper;

import com.transfer.medical.pojo.MedicalDataShareMessage;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * 共享消息数据访问接口。
 */
@Repository
public interface MedicalDataShareMessageMapper {

    /** 按条件查询消息列表。 */
    List<MedicalDataShareMessage> queryByCondition(Map<String, Object> params);

    /** 按条件删除消息。 */
    Integer removeMessage(Map<String, Object> params);

    /** 查询接口指定的文件字符集。 */
    String selectFileCode(String unitName);

    /** 查询接口的特殊文件标识。 */
    String selectSpecialFile(String unitName);

    /** 按文件名统计数量。 */
    Integer countFileName(String fileName);

    /** 统计消息总数。 */
    Integer countMessages();

    /** 按工作流+日期统计去重后的文件 ID 数。 */
    Integer countDistinctFileId(MedicalDataShareMessage message);

    /** 统计探测标记数量。 */
    Integer countProbeFlag();

    /** 查询消息对应的文件 ID。 */
    String selectFileId(MedicalDataShareMessage message);

    /** 新增消息。 */
    void insertMessage(MedicalDataShareMessage message);

    /** 通用状态更新。 */
    int updateMessage(Map<String, Object> params);

    /** 抢占式更新处理状态。 */
    int claimProcessFlag(Map<String, Object> params);

    /** 按工作流+日期更新完成标志。 */
    void updateDoneByUnitDate(Map<String, Object> params);

    /** 按工作流+日期更新校验完成标志。 */
    void updateVerifyDoneByUnitDate(Map<String, Object> params);

    /** 更新校验文件信息。 */
    void updateVerifyMessage(Map<String, Object> params);

    /** 查询待下载消息。 */
    List<MedicalDataShareMessage> selectPendingDownload();

    /** 查询待处理消息。 */
    List<MedicalDataShareMessage> selectPendingProcess();

    /** 查询待上传 HDFS 消息。 */
    List<MedicalDataShareMessage> selectPendingHdfs();

    /** 查询待校验的批次汇总。 */
    List<MedicalDataShareMessage> selectReceiveSummary();

    /** 按工作流+日期统计消息数。 */
    Integer countByUnitDate(Map<String, Object> params);

    /** 重置全部完成标志。 */
    void resetAllDone();

    /** 重置全部下载状态。 */
    void resetAllDownFlag();

    /** 按工作流+日期统计已处理文件数。 */
    Integer countProcessedByUnitDate(Map<String, Object> params);

    /** 查询校验文件中尚未核对的数据文件。 */
    List<MedicalDataShareMessage> selectUnverifiedByUnitDate(Map<String, Object> params);

    /** 汇总消息头声明的文件数。 */
    Integer sumHeaderFileNum(Map<String, Object> params);

    /** 重置校验文件完成标志。 */
    void resetVerifyDone();

    /** 重置校验文件下载状态。 */
    void resetVerifyDownFlag();
}
