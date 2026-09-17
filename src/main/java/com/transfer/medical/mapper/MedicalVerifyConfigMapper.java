package com.transfer.medical.mapper;

import com.transfer.medical.pojo.MedicalVerifyConfig;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 校验策略配置数据访问接口。
 */
@Repository
public interface MedicalVerifyConfigMapper {

    /** 查询按校验文件核对的策略。 */
    List<MedicalVerifyConfig> selectVerfStrategies();

    /** 查询按消息头文件数核对的策略。 */
    List<MedicalVerifyConfig> selectFileNumStrategies();

    /** 查询固定分发次数的策略。 */
    List<MedicalVerifyConfig> selectManyStrategies();
}
