package com.transfer.psbcx.mapper;

import com.transfer.psbcx.pojo.TransferVerifyConfig;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 校验策略配置数据访问接口。
 */
@Repository
public interface TransferVerifyConfigMapper {

    /** 查询按校验文件核对的策略。 */
    List<TransferVerifyConfig> selectVerfStrategies();

    /** 查询按消息头文件数核对的策略。 */
    List<TransferVerifyConfig> selectFileNumStrategies();

    /** 查询固定分发次数的策略。 */
    List<TransferVerifyConfig> selectManyStrategies();
}
