package com.massdata.controller;

import com.massdata.service.HdfsService;
import com.massdata.util.R;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * HDFS 归档与恢复控制器
 *
 * ┌──────────────────────────────────────────────────────┐
 * │  接口                         │ 方法  │ 功能        │
 * ├──────────────────────────────────────────────────────┤
 * │  /api/hdfs/status             │ GET   │ HDFS状态     │
 * │  /api/hdfs/files              │ GET   │ 列出归档文件  │
 * │  /api/hdfs/archive/results    │ POST  │ 归档挖掘结果  │
 * │  /api/hdfs/archive/data       │ POST  │ 归档城市数据  │
 * │  /api/hdfs/restore            │ POST  │ 从HDFS恢复   │
 * └──────────────────────────────────────────────────────┘
 */
@RestController
@RequestMapping("/api/hdfs")
public class HdfsController {

    private final HdfsService hdfsService;

    public HdfsController(HdfsService hdfsService) {
        this.hdfsService = hdfsService;
    }

    @GetMapping("/status")
    public R status() {
        return R.ok(hdfsService.getStatus());
    }

    @GetMapping("/files")
    public R files() {
        return R.ok(hdfsService.listFiles());
    }

    @PostMapping("/archive/results")
    public R archiveResults(HttpServletRequest request) {
        if (!"admin".equals(request.getAttribute("role"))) return R.error(403, "需要管理员权限才能归档");
        Map<String, Object> r = hdfsService.archiveAnalysisResults();
        if (Boolean.TRUE.equals(r.get("success"))) {
            return R.ok(r.get("message").toString(), r);
        }
        return R.error(r.get("message").toString());
    }

    @PostMapping("/archive/data")
    public R archiveData(@RequestParam(required = false) String dataType, HttpServletRequest request) {
        if (!"admin".equals(request.getAttribute("role"))) return R.error(403, "需要管理员权限才能归档");
        Map<String, Object> r = hdfsService.archiveCityData(dataType);
        if (Boolean.TRUE.equals(r.get("success"))) {
            return R.ok(r.get("message").toString(), r);
        }
        return R.error(r.get("message").toString());
    }

    @PostMapping("/restore")
    public R restore(@RequestBody Map<String, String> body, HttpServletRequest request) {
        if (!"admin".equals(request.getAttribute("role"))) return R.error(403, "需要管理员权限才能恢复");
        String hdfsPath = body.get("hdfsPath");
        String collection = body.get("collection");
        if (hdfsPath == null || collection == null) {
            return R.error("请指定 hdfsPath 和 collection");
        }
        Map<String, Object> r = hdfsService.restore(hdfsPath, collection);
        if (Boolean.TRUE.equals(r.get("success"))) {
            return R.ok(r.get("message").toString(), r);
        }
        return R.error(r.get("message").toString());
    }
}
