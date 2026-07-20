package com.massdata.controller;

import com.massdata.dao.mongo.AnalysisResultRepository;
import com.massdata.entity.AnalysisResult;
import com.massdata.util.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sun.management.OperatingSystemMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.util.*;

/**
 * 任务可监控 - 实时任务/系统指标
 * - /api/monitor/overview        全局概览
 * - /api/monitor/jvm             JVM与系统CPU/内存
 * - /api/monitor/running-tasks   正在运行的挖掘任务
 * - /api/monitor/task-stats      任务统计(近24h)
 * - /api/monitor/recent-tasks    最近N条任务
 * - /api/monitor/collect-stats   数据同步状态(近24h)
 */
@RestController
@RequestMapping("/api/monitor")
public class MonitorController {

    @Autowired
    private AnalysisResultRepository resultRepo;

    @Autowired
    private com.massdata.dao.mongo.CityDataRepository cityDataRepo;

    @Autowired
    private com.massdata.service.SparkStreamingStatusService sparkStreamingStatus;

    /**
     * 监控概览(一次返回所有面板需要的核心数据)
     */
    @GetMapping("/overview")
    public R overview() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("jvm", jvmInfo());
        data.put("runningTasks", doRunningTasks());
        data.put("taskStats", doTaskStats());
        data.put("collectStats", doCollectStats());
        data.put("timestamp", System.currentTimeMillis());
        return R.ok(data);
    }

    /**
     * JVM 与系统 CPU/内存指标
     */
    @GetMapping("/jvm")
    public R jvm() {
        return R.ok(jvmInfo());
    }

    /**
     * 正在运行的挖掘任务(status=running)
     */
    @GetMapping("/running-tasks")
    public R runningTasks() {
        return R.ok(doRunningTasks());
    }

    /**
     * 任务统计
     */
    @GetMapping("/task-stats")
    public R taskStats() {
        return R.ok(doTaskStats());
    }

    /**
     * 最近N条任务
     */
    @GetMapping("/recent-tasks")
    public R recentTasks(@RequestParam(defaultValue = "20") int limit) {
        if (limit > 100) limit = 100;
        if (limit < 1) limit = 1;
        // 不分页,直接取top 100然后手动截取
        List<AnalysisResult> all = resultRepo.findTop20ByOrderByCreateTimeDesc();
        List<AnalysisResult> slice = all.size() > limit ? all.subList(0, limit) : all;
        return R.ok(slice);
    }

    /**
     * 数据同步状态(MongoDB 中各类型数据量)
     */
    @GetMapping("/collect-stats")
    public R collectStats() {
        return R.ok(doCollectStats());
    }

    /**
     * Spark Streaming 接入点状态 (独立模块, 不影响现有实时流)
     */
    @GetMapping("/streaming-status")
    public R streamingStatus() {
        return R.ok(sparkStreamingStatus.status());
    }

    // ============== 私有实现 ==============

    private Map<String, Object> jvmInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        // JVM 内存
        MemoryMXBean mem = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = mem.getHeapMemoryUsage();
        MemoryUsage nonHeap = mem.getNonHeapMemoryUsage();
        Map<String, Object> heapMap = new LinkedHashMap<>();
        heapMap.put("used", heap.getUsed() / 1024 / 1024);
        heapMap.put("max", heap.getMax() / 1024 / 1024);
        heapMap.put("committed", heap.getCommitted() / 1024 / 1024);
        heapMap.put("usedPercent", heap.getMax() > 0 ? (heap.getUsed() * 100.0 / heap.getMax()) : 0);
        info.put("heap", heapMap);

        Map<String, Object> nonHeapMap = new LinkedHashMap<>();
        nonHeapMap.put("used", nonHeap.getUsed() / 1024 / 1024);
        nonHeapMap.put("max", nonHeap.getMax() / 1024 / 1024);
        nonHeapMap.put("committed", nonHeap.getCommitted() / 1024 / 1024);
        info.put("nonHeap", nonHeapMap);

        // 系统 CPU
        OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        Map<String, Object> cpu = new LinkedHashMap<>();
        cpu.put("processCpuLoad", osBean.getProcessCpuLoad() < 0 ? 0 : osBean.getProcessCpuLoad() * 100);
        cpu.put("systemCpuLoad", osBean.getCpuLoad() < 0 ? 0 : osBean.getCpuLoad() * 100);
        cpu.put("availableProcessors", osBean.getAvailableProcessors());
        cpu.put("systemLoadAverage", osBean.getSystemLoadAverage());
        info.put("cpu", cpu);

        // 运行信息
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        Map<String, Object> runtimeMap = new LinkedHashMap<>();
        runtimeMap.put("uptime", runtime.getUptime());
        runtimeMap.put("uptimeDesc", formatUptime(runtime.getUptime()));
        runtimeMap.put("startTime", runtime.getStartTime());
        runtimeMap.put("jvmName", runtime.getVmName());
        runtimeMap.put("jvmVersion", runtime.getVmVersion());
        info.put("runtime", runtimeMap);

        // 线程
        java.lang.management.ThreadMXBean thread = ManagementFactory.getThreadMXBean();
        info.put("threads", thread.getThreadCount());
        info.put("daemonThreads", thread.getDaemonThreadCount());

        return info;
    }

    private List<Map<String, Object>> doRunningTasks() {
        // 用全表扫描后过滤(数据量小,MongoDB 也好)
        List<AnalysisResult> all = resultRepo.findAll();
        List<Map<String, Object>> running = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (AnalysisResult r : all) {
            if ("running".equalsIgnoreCase(r.getStatus())) {
                Map<String, Object> t = new LinkedHashMap<>();
                t.put("taskId", r.getTaskId());
                t.put("dataType", r.getDataType());
                t.put("taskType", r.getTaskType());
                t.put("dateStr", r.getDateStr());
                t.put("createTime", r.getCreateTime());
                t.put("elapsed", r.getCreateTime() != null ? now - parseTime(r.getCreateTime()) : 0);
                running.add(t);
            }
        }
        // 按开始时间倒序
        running.sort((a, b) -> Long.compare((Long) b.getOrDefault("elapsed", 0L), (Long) a.getOrDefault("elapsed", 0L)));
        return running;
    }

    private Map<String, Object> doTaskStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        List<AnalysisResult> all = resultRepo.findAll();
        int total = all.size();
        int success = 0, failed = 0, running = 0;
        Map<String, Integer> byType = new LinkedHashMap<>();
        long totalDuration = 0;
        for (AnalysisResult r : all) {
            String s = r.getStatus();
            if ("success".equalsIgnoreCase(s)) success++;
            else if ("failed".equalsIgnoreCase(s)) failed++;
            else if ("running".equalsIgnoreCase(s)) running++;
            byType.merge(r.getTaskType() == null ? "unknown" : r.getTaskType(), 1, Integer::sum);
            totalDuration += r.getDuration();
        }
        stats.put("total", total);
        stats.put("success", success);
        stats.put("failed", failed);
        stats.put("running", running);
        stats.put("successRate", total > 0 ? (success * 100.0 / total) : 0);
        stats.put("totalDuration", totalDuration);
        stats.put("avgDuration", total > 0 ? (totalDuration / total) : 0);
        stats.put("byType", byType);
        return stats;
    }

    private Map<String, Object> doCollectStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        String[] types = {"traffic", "weather", "opinion", "consumption", "population"};
        String[] labels = {"交通", "气象", "舆情", "消费", "人口"};
        Map<String, Integer> byType = new LinkedHashMap<>();
        Map<String, Integer> byTypeTotal = new LinkedHashMap<>();
        int total = 0;
        for (int i = 0; i < types.length; i++) {
            try {
                int cnt = (int) cityDataRepo.countByDataType(types[i]);
                byType.put(labels[i], cnt);
                byTypeTotal.put(labels[i], cnt);
                total += cnt;
            } catch (Exception e) {
                byType.put(labels[i], -1);
            }
        }
        stats.put("total", total);
        stats.put("byType", byType);
        return stats;
    }

    private String formatUptime(long ms) {
        long s = ms / 1000;
        long m = s / 60;
        long h = m / 60;
        long d = h / 24;
        if (d > 0) return d + "天" + (h % 24) + "小时";
        if (h > 0) return h + "小时" + (m % 60) + "分钟";
        if (m > 0) return m + "分钟" + (s % 60) + "秒";
        return s + "秒";
    }

    private long parseTime(String s) {
        try {
            return java.time.LocalDateTime.parse(s, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }
}
