package com.transfer.medical.controller;

import com.transfer.medical.mapper.MedicalDataShareMessageMapper;
import com.transfer.medical.pojo.MedicalDataShareMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 文件交换服务对外查询接口。
 *
 * 提供共享消息的列表查询与记录删除能力。
 */
@RestController
@RequestMapping("transfer")
@Slf4j
public class MedicalRestController {

    @Autowired
    private MedicalDataShareMessageMapper shareMsgMapper;

    /**
     * 按条件查询共享消息列表。
     *
     * @param condition 查询条件（如 unitName、txDate）
     * @return 消息列表
     */
    @RequestMapping("/list")
    public List<MedicalDataShareMessage> queryMessages(@RequestParam Map<String, Object> condition) {
        return shareMsgMapper.queryByCondition(condition);
    }

    /**
     * 按条件删除共享消息记录。
     *
     * @param condition 删除条件
     * @return 受影响行数
     */
    @RequestMapping("/deleteMsg")
    public Integer removeMessages(@RequestBody Map<String, Object> condition) {
        return shareMsgMapper.removeMessage(condition);
    }
}
