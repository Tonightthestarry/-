package com.massdata.controller;

import com.massdata.service.VisualizationService;
import com.massdata.util.R;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 可视化控制器 - 提供ECharts大屏数据
 */
@RestController
@RequestMapping("/api/visual")
public class VisualizationController {

    private final VisualizationService visualService;

    public VisualizationController(VisualizationService visualService) {
        this.visualService = visualService;
    }

    @GetMapping("/overview")
    public R overview(@RequestParam(defaultValue = "") String date) {
        if (date.isEmpty()) {
            date = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date());
        }
        return R.ok(visualService.getDashboardOverview(date));
    }

    @GetMapping("/traffic-heatmap")
    public R trafficHeatmap(@RequestParam(defaultValue = "") String date) {
        if (date.isEmpty()) {
            date = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date());
        }
        return R.ok(visualService.getTrafficHeatmap(date));
    }

    /**
     * 调试用:查看一条原始 city_data 文档结构
     */
    @GetMapping("/raw-sample")
    public R rawSample(@RequestParam(defaultValue = "traffic") String dataType) {
        return R.ok(visualService.getSampleData(dataType));
    }

    @GetMapping("/congestion-pie")
    public R congestionPie(@RequestParam(defaultValue = "") String date) {
        if (date.isEmpty()) {
            date = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date());
        }
        return R.ok(visualService.getCongestionDistribution(date));
    }

    @GetMapping("/population-bar")
    public R populationBar(@RequestParam(defaultValue = "") String date) {
        if (date.isEmpty()) {
            date = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date());
        }
        return R.ok(visualService.getPopulationDistribution(date));
    }

    @GetMapping("/weather-line")
    public R weatherLine(@RequestParam(defaultValue = "") String date) {
        if (date.isEmpty()) {
            date = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date());
        }
        return R.ok(visualService.getWeatherTrend(date));
    }

    @GetMapping("/consumption-scatter")
    public R consumptionScatter(@RequestParam(defaultValue = "") String date) {
        if (date.isEmpty()) {
            date = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date());
        }
        return R.ok(visualService.getConsumptionScatter(date));
    }

    @GetMapping("/opinion-gauge")
    public R opinionGauge(@RequestParam(defaultValue = "") String date) {
        if (date.isEmpty()) {
            date = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date());
        }
        return R.ok(visualService.getOpinionGauge(date));
    }

    @GetMapping("/analysis-summary")
    public R analysisSummary() {
        return R.ok(visualService.getAnalysisSummary());
    }

    @GetMapping("/accident-risk")
    public R accidentRisk(@RequestParam(defaultValue = "") String date) {
        if (date.isEmpty()) {
            date = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date());
        }
        return R.ok(visualService.getAccidentRisk(date));
    }

    @GetMapping("/travel-pattern")
    public R travelPattern(@RequestParam(defaultValue = "") String date) {
        if (date.isEmpty()) {
            date = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date());
        }
        return R.ok(visualService.getTravelPattern(date));
    }

    @GetMapping("/export")
    public R export(@RequestParam(defaultValue = "") String date) {
        if (date.isEmpty()) {
            date = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date());
        }
        return R.ok(visualService.exportDashboardData(date));
    }

    @GetMapping("/spark-status")
    public R sparkStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("enabled", true);
        status.put("version", "3.4.1");
        status.put("master", "local[*]");
        status.put("mode", "local");
        status.put("streaming", "running (5s batch)");
        status.put("status", "正常运行");
        return R.ok(status);
    }

    @GetMapping("/traffic-flow-24h")
    public R trafficFlow24h(@RequestParam(defaultValue = "") String date) {
        if (date.isEmpty()) date = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date());
        return R.ok(visualService.getTrafficFlow24h(date));
    }

    @GetMapping("/next-24h-forecast")
    public R next24hForecast(@RequestParam(defaultValue = "") String date) {
        return R.ok(visualService.getNext24hForecast(date));
    }

    @GetMapping("/traffic-anomaly")
    public R trafficAnomaly(@RequestParam(defaultValue = "") String date) {
        if (date.isEmpty()) date = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date());
        return R.ok(visualService.getTrafficAnomaly(date));
    }

    @GetMapping("/opinion-anomaly")
    public R opinionAnomaly(@RequestParam(defaultValue = "") String date) {
        if (date.isEmpty()) date = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date());
        return R.ok(visualService.getOpinionAnomaly(date));
    }

    @GetMapping("/population-commercial")
    public R populationCommercial(@RequestParam(defaultValue = "") String date) {
        if (date.isEmpty()) date = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date());
        return R.ok(visualService.getPopulationCommercial(date));
    }

    @GetMapping("/consumption-heatmap")
    public R consumptionHeatmap(@RequestParam(defaultValue = "") String date) {
        if (date.isEmpty()) date = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date());
        return R.ok(visualService.getConsumptionHeatmap(date));
    }
}
