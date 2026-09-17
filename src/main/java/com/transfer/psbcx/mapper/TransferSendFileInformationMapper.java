package com.transfer.psbcx.mapper;

import com.transfer.psbcx.pojo.TransferSendFileInformation;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * 待下发文件数据访问接口。
 */
@Repository
public interface TransferSendFileInformationMapper {

    /** 查询待下发文件。 */
    List<TransferSendFileInformation> selectPendingPush();

    /** 更新下发状态。 */
    void updatePushStatus(Map<String, Object> params);
}
