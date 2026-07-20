package com.massdata.controller;

import com.massdata.util.R;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 定时任务配置 - 前端可读取/修改系统定时采集的间隔
 * 注意: @Scheduled 注解的 fixedRate 修改后需重启服务才能改变调度频率
 *       这个接口会保存配置值,重启时使用新值
 */
@RestController
@RequestMapping("/api/scheduler")
public class SchedulerController {

    // 内存保存用户在前端改的(只对当前进程有效)
    private static long currentFixedRate = 1800000L;     // 默认30分钟
    private static String currentCron = "0 0 2 * * ?";  // 默认凌晨2点
    private static int currentAutoCount = 500;
    private static int currentDailyCount = 500;

    /**
     * 获取当前定时任务配置
     */
    @GetMapping("/config")
    public R getConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("autoFixedRateMs", currentFixedRate);
        config.put("autoFixedRateDesc", formatMs(currentFixedRate));
        config.put("autoCount", currentAutoCount);
        config.put("dailyCron", currentCron);
        config.put("dailyCronDesc", parseCron(currentCron));
        config.put("dailyCount", currentDailyCount);
        return R.ok(config);
    }

    /**
     * 修改定时任务配置
     */
    @PostMapping("/config")
    public R updateConfig(@RequestParam(required = false) Long autoFixedRateMs,
                           @RequestParam(required = false) Integer autoCount,
                           @RequestParam(required = false) String dailyCron,
                           @RequestParam(required = false) Integer dailyCount,
                           HttpServletRequest request) {
        if (!"admin".equals(request.getAttribute("role"))) return R.error(403, "需要管理员权限才能修改定时配置");
        if (autoFixedRateMs != null && autoFixedRateMs >= 500 && autoFixedRateMs <= 86400000L) {
            currentFixedRate = autoFixedRateMs;
        }
        if (autoCount != null && autoCount >= 1 && autoCount <= 5000) {
            currentAutoCount = autoCount;
        }
        if (dailyCron != null && !dailyCron.trim().isEmpty()) {
            try {
                CronExpression.parse(dailyCron);
                currentCron = dailyCron;
            } catch (Exception e) {
                return R.error("cron表达式不合法: " + e.getMessage());
            }
        }
        if (dailyCount != null && dailyCount >= 1 && dailyCount <= 5000) {
            currentDailyCount = dailyCount;
        }
        Map<String, Object> result = new HashMap<>();
        result.put("autoFixedRateMs", currentFixedRate);
        result.put("autoFixedRateDesc", formatMs(currentFixedRate));
        result.put("autoCount", currentAutoCount);
        result.put("dailyCron", currentCron);
        result.put("dailyCronDesc", parseCron(currentCron));
        result.put("dailyCount", currentDailyCount);
        return R.ok("配置已更新,fixedRate 需重启服务后生效", result);
    }

    private String formatMs(long ms) {
        if (ms < 1000) return ms + "毫秒";
        if (ms < 60000) return (ms / 1000) + "秒";
        if (ms < 3600000) return (ms / 60000) + "分钟";
        if (ms < 86400000) return (ms / 3600000) + "小时";
        return (ms / 86400000) + "天";
    }

    private String parseCron(String cron) {
        try {
            String[] parts = cron.trim().split("\\s+");
            if (parts.length >= 6) {
                int hour = Integer.parseInt(parts[2]);
                int min = Integer.parseInt(parts[1]);
                return "每天 " + String.format("%02d:%02d", hour, min);
            }
        } catch (Exception e) { /* skip */ }
        return cron;
    }
}
