package com.transfer.medical.task;

import com.transfer.medical.util.MedicalAppPropertiesUtil;
import com.transfer.medical.util.MedicalStringHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 过期文件目录清理任务。
 */
@Component
@EnableScheduling
@Slf4j
public class MedicalCleanUpJob implements MedicalJob {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Scheduled(initialDelayString = "${scheduled.task.cleanup.initialDelay}",
            fixedRateString = "${scheduled.task.cleanup.fixedRate}")
    @Override
    public synchronized void run() {
        log.info("开始扫描可清理的过期目录");

        int keepDays = Integer.parseInt(MedicalAppPropertiesUtil.getProperty("delete.local.data.interval", "3"));
        String rootDir = MedicalStringHelper.addEndSeparator(
                MedicalAppPropertiesUtil.getProperty("data.share.download.dir.prefix"));

        for (String staleDate : staleDates(LocalDate.now(), keepDays)) {
            File staleDir = new File(rootDir + staleDate);
            if (!staleDir.exists() || !staleDir.isDirectory()) {
                continue;
            }
            log.info("即将删除过期目录 {}", staleDir);
            try {
                FileUtils.forceDelete(staleDir);
            } catch (IOException e) {
                log.error("删除目录失败: {}", staleDir, e);
            }
        }
    }

    /**
     * 计算需要清理的日期列表。
     *
     * @param today    当前日期
     * @param interval 保留区间
     * @return 过期日期（yyyyMMdd）
     */
    private List<String> staleDates(LocalDate today, int interval) {
        List<String> dates = new ArrayList<>(interval);
        for (int offset = 1; offset <= interval; offset++) {
            dates.add(today.minusDays(offset + interval).format(DATE_FORMAT));
        }
        return dates;
    }
}
