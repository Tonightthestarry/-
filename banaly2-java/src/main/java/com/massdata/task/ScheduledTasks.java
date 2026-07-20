package com.massdata.task;

import com.massdata.service.DataCollectionService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 定时任务调度 - 定时采集 + 定时挖掘
 * 时间可在前端"系统定时任务"面板修改(部分需重启)
 */
@Component
public class ScheduledTasks {

    private final DataCollectionService collectionService;

    public ScheduledTasks(DataCollectionService collectionService) {
        this.collectionService = collectionService;
    }

    /**
     * 每30分钟自动采集一次(模拟实时数据)
     * 实际间隔可通过前端 /api/scheduler/config 修改
     */
    @Scheduled(fixedRate = 1800000)
    public void autoCollect() {
        String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
        System.out.println("[" + time + "] 定时采集开始...");
        try {
            // 每次随机采集1-2类数据，避免一次性压力过大
            String[] types = {"traffic", "weather", "opinion", "consumption", "population"};
            int idx = (int) (System.currentTimeMillis() % types.length);
            collectionService.collect(types[idx], "上海", 500);
            System.out.println("[" + time + "] 定时采集完成: " + types[idx]);
        } catch (Exception e) {
            System.out.println("[" + time + "] 定时采集失败: " + e.getMessage());
        }
    }

    /**
     * 每天凌晨2点全量采集
     * cron 可通过前端 /api/scheduler/config 修改
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void dailyFullCollect() {
        System.out.println("每日全量采集开始...");
        try {
            collectionService.collectAll("上海", 500);
            System.out.println("每日全量采集完成");
        } catch (Exception e) {
            System.out.println("每日全量采集失败: " + e.getMessage());
        }
    }
}
