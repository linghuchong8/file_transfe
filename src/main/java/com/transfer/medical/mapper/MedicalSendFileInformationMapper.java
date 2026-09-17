package com.transfer.medical.mapper;

import com.transfer.medical.pojo.MedicalSendFileInformation;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * 待下发文件数据访问接口。
 */
@Repository
public interface MedicalSendFileInformationMapper {

    /** 查询待下发文件。 */
    List<MedicalSendFileInformation> selectPendingPush();

    /** 更新下发状态。 */
    void updatePushStatus(Map<String, Object> params);
}
