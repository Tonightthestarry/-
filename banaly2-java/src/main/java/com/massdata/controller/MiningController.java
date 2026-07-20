package com.massdata.controller;

import com.massdata.entity.AnalysisResult;
import com.massdata.service.DataMiningService;
import com.massdata.util.R;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 挖掘分析控制器 - 调度分析/查看结果/导出
 */
@RestController
@RequestMapping("/api/mining")
public class MiningController {

    private final DataMiningService miningService;

    public MiningController(DataMiningService miningService) {
        this.miningService = miningService;
    }

    /**
     * 执行挖掘分析
     * POST /api/mining/execute?dataType=traffic&taskType=cluster&dateStr=2026-06-09
     */
    @PostMapping("/execute")
    public R executeMining(@RequestParam String dataType,
                           @RequestParam(defaultValue = "statistic") String taskType,
                           @RequestParam(defaultValue = "") String dateStr,
                           HttpServletRequest request) {
        if (dateStr.isEmpty()) {
            dateStr = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date());
        }

        String role = (String) request.getAttribute("role");
        if (!"admin".equals(role)) {
            return R.error(403, "需要管理员权限才能执行挖掘分析");
        }

        Map<String, Object> result = miningService.executeMining(dataType, taskType, dateStr);
        if (result.containsKey("error")) {
            return R.error(result.get("error").toString());
        }
        return R.ok("分析完成", result);
    }

    /**
     * 执行全部五类挖掘
     * POST /api/mining/execute-all?dateStr=2026-06-09
     */
    @PostMapping("/execute-all")
    public R executeAll(@RequestParam(defaultValue = "") String dateStr,
                        HttpServletRequest request) {
        if (dateStr.isEmpty()) {
            dateStr = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date());
        }

        String role = (String) request.getAttribute("role");
        if (!"admin".equals(role)) {
            return R.error(403, "需要管理员权限");
        }

        return R.ok(miningService.executeAllMining(dateStr));
    }

    /**
     * 获取分析结果列表
     * GET /api/mining/results?dataType=traffic
     */
    @GetMapping("/results")
    public R getResults(@RequestParam(defaultValue = "") String dataType) {
        List<AnalysisResult> results;
        if (dataType.isEmpty()) {
            results = miningService.getRecentResults();
        } else {
            results = miningService.getAnalysisHistory(dataType);
        }
        return R.ok(results);
    }

    /**
     * 获取单个分析详情
     * GET /api/mining/result/{taskId}
     */
    @GetMapping("/result/{taskId}")
    public R getResult(@PathVariable String taskId) {
        AnalysisResult result = miningService.getResult(taskId);
        if (result == null) {
            return R.error("分析任务不存在");
        }
        return R.ok(result);
    }

    /**
     * 查询单个任务状态/进度
     * GET /api/mining/status/{taskId}
     */
    @GetMapping("/status/{taskId}")
    public R taskStatus(@PathVariable String taskId) {
        Map<String, Object> status = miningService.getTaskStatus(taskId);
        if (status == null) {
            return R.error("任务不存在: " + taskId);
        }
        return R.ok(status);
    }

    /**
     * 停止/取消一个正在运行的任务
     * POST /api/mining/stop/{taskId}
     */
    @PostMapping("/stop/{taskId}")
    public R stopTask(@PathVariable String taskId, HttpServletRequest request) {
        String role = (String) request.getAttribute("role");
        if (!"admin".equals(role)) {
            return R.error(403, "需要管理员权限");
        }
        boolean ok = miningService.stopTask(taskId);
        if (!ok) {
            return R.error("任务不存在或不是running状态");
        }
        return R.ok("任务已取消: " + taskId);
    }

    /**
     * 历史挖掘结果回溯(按日/月/年维度)
     * GET /api/mining/history?dimension=day|month|year&value=2026-06-09&dataType=&taskType=
     */
    @GetMapping("/history")
    public R history(@RequestParam(defaultValue = "day") String dimension,
                     @RequestParam String value,
                     @RequestParam(defaultValue = "") String dataType,
                     @RequestParam(defaultValue = "") String taskType) {
        if (value == null || value.trim().isEmpty()) {
            return R.error("请提供 value 参数(对应 dimension 的日期前缀)");
        }
        List<AnalysisResult> results = miningService.queryHistory(dimension, value.trim(), dataType, taskType);
        java.util.Map<String, Object> meta = new java.util.LinkedHashMap<>();
        meta.put("dimension", dimension);
        meta.put("value", value);
        meta.put("count", results.size());
        meta.put("dataType", dataType);
        meta.put("taskType", taskType);
        java.util.Map<String, Object> resp = new java.util.LinkedHashMap<>();
        resp.put("meta", meta);
        resp.put("data", results);
        return R.ok(resp);
    }

    /**
     * AI业务建议 - 根据挖掘分析结果调用DeepSeek生成结论
     * GET /api/mining/conclusion?dataType=traffic&taskType=cluster
     */
    @GetMapping("/conclusion")
    public R conclusion(@RequestParam String dataType,
                        @RequestParam(defaultValue = "statistic") String taskType) {
        String dateStr = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date());
        Map<String, Object> result = miningService.executeMining(dataType, taskType, dateStr);
        Map<String, Object> resp = new java.util.LinkedHashMap<>();
        resp.put("dataType", dataType);
        resp.put("taskType", taskType);
        resp.put("analysisResult", result);
        resp.put("conclusion", miningService.generateConclusion(dataType, taskType, result));
        return R.ok(resp);
    }
}
