package com.transfer.psbcx.mapper;

import com.transfer.psbcx.pojo.TransferVerifyContent;
import org.springframework.stereotype.Repository;

import java.util.Map;

/**
 * 校验文件明细数据访问接口。
 */
@Repository
public interface TransferVerifyContentMapper {

    /** 新增校验明细。 */
    void insertDetail(TransferVerifyContent content);

    /** 按工作流+日期统计校验明细数。 */
    Integer countDetailByUnitDate(Map<String, Object> params);
}
