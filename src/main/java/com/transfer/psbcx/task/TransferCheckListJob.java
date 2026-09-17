package com.transfer.psbcx.task;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.transfer.psbcx.mapper.TransferDataShareMessageMapper;
import com.transfer.psbcx.service.TransferTransferHttpClientService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

/**
 * EDB 客户端插件探测任务。
 *
 * 定时调度默认关闭，需要时可在配置中开启。
 *
 * @author luyuanyuan
 */
@Component
@Slf4j
public class TransferCheckListJob implements TransferJob {

    @Value("${checklist.interface}")
    private String checklistInterface;

    @Autowired
    private TransferDataShareMessageMapper shareMsgMapper;

    @Autowired
    private TransferTransferHttpClientService httpClientService;

    /**
     * 探测客户端服务的上传/下载/消息插件名称、版本与状态。
     */
    @Override
    public synchronized void run() {
        int probeFlag = 0;
        try {
            Map<String, String> queryParams = new HashMap<>();
            String probeUrl = "http://" + InetAddress.getLocalHost().getHostAddress()
                    + ":9021" + checklistInterface;
            log.info("探测接口地址: {}", probeUrl);

            JSONObject body = JSONObject.parseObject(httpClientService.sendGetRequest(probeUrl, queryParams));
            JSONArray plugins = (JSONArray) body.get("data");
            probeFlag = shareMsgMapper.countProbeFlag();

            if (plugins == null || plugins.isEmpty()) {
                log.info("探测接口未返回插件信息");
                return;
            }
            for (Object item : plugins) {
                JSONObject plugin = (JSONObject) item;
                String name = plugin.getString("pluginName");
                String version = plugin.getString("pluginVersion");
                String state = plugin.getString("pluginState");
                log.info("插件名称: {}, 版本号: {}, 状态: {}", name, version, state);
                if (!"STARTED".equals(state) && probeFlag == 0) {
                    log.info("插件状态异常, 名称: {}, 版本号: {}, 状态: {}", name, version, state);
                }
            }
        } catch (Exception e) {
            log.error("探测接口调用失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 重启 Transfer 服务的辅助方法（预留）。
     */
    public static void restartTransferService() {
        log.info("准备执行重启 Transfer 服务的 Shell 命令");
        String scriptPath = System.getenv("HOME") + "/transfer/service.sh";
        log.info("脚本路径: {}", scriptPath);
        String command = "sh " + scriptPath + " stop ";
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(command);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("脚本输出: {}", line);
                }
                int exitCode = process.waitFor();
                log.info("脚本退出码: {}, 命令: {}", exitCode, command);
            }
        } catch (InterruptedException | IOException e) {
            log.error("重启 Transfer 服务失败", e);
            Thread.currentThread().interrupt();
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }
}
