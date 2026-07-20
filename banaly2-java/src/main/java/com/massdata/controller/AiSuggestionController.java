package com.massdata.controller;

import com.massdata.service.AiSuggestionService;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI便民建议控制器 - 模块六增强
 * 扫描当日真实数据, 生成普通人能看懂的便民建议
 */
@RestController
@RequestMapping("/api/ai")
public class AiSuggestionController {

    private final AiSuggestionService suggestionService;

    public AiSuggestionController(AiSuggestionService suggestionService) {
        this.suggestionService = suggestionService;
    }

    /**
     * 获取便民建议
     * GET /api/ai/suggestion?date=2026-06-15
     */
    @GetMapping("/suggestion")
    public Map<String, Object> suggestion(
            @RequestParam(defaultValue = "") String date) {
        return suggestionService.generate(date);
    }

    /**
     * 获取最紧急事件列表(按区域汇总,按紧急度排序)
     * GET /api/ai/urgent-alerts?date=2026-06-15
     */
    @GetMapping("/urgent-alerts")
    public Map<String, Object> urgentAlerts(
            @RequestParam(defaultValue = "") String date) {
        return suggestionService.getUrgentAlerts(date);
    }

    /**
     * 获取生活化预测建议(给人看的"明天带伞"类)
     * GET /api/ai/life-advice?date=2026-06-15
     */
    @GetMapping("/life-advice")
    public Map<String, Object> lifeAdvice(
            @RequestParam(defaultValue = "") String date) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("advice", suggestionService.genLifeAdvice(date));
        return r;
    }
}
