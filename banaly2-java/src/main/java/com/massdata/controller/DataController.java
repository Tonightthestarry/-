package com.massdata.controller;

import com.massdata.entity.CityData;
import com.massdata.service.DataCollectionService;
import com.massdata.util.R;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/data")
public class DataController {

    private final DataCollectionService collectionService;

    public DataController(DataCollectionService collectionService) {
        this.collectionService = collectionService;
    }

    @PostMapping("/collect")
    public R collect(@RequestParam String dataType,
                     @RequestParam(defaultValue = "上海") String city,
                     @RequestParam(defaultValue = "500") int count,
                     HttpServletRequest request) {
        if (!isAdmin(request)) return R.error(403, "需要管理员权限才能执行采集");
        Map<String, Object> result = collectionService.collect(dataType, city, count);
        if ((boolean) result.get("success")) {
            return R.ok("采集完成,共" + result.get("count") + "条", result);
        }
        return R.error(result.get("error").toString());
    }

    @PostMapping("/collect-all")
    public R collectAll(@RequestParam(defaultValue = "上海") String city,
                        @RequestParam(defaultValue = "500") int count,
                        HttpServletRequest request) {
        if (!isAdmin(request)) return R.error(403, "需要管理员权限才能执行采集");
        return R.ok(collectionService.collectAll(city, count));
    }

    @PostMapping("/collect-with-keyword")
    public R collectWithKeyword(@RequestParam String dataType,
                                 @RequestParam(defaultValue = "上海") String city,
                                 @RequestParam(defaultValue = "500") int count,
                                 @RequestParam(defaultValue = "新闻") String keyword,
                                 HttpServletRequest request) {
        if (!isAdmin(request)) return R.error(403, "需要管理员权限才能执行采集");
        Map<String, Object> result = collectionService.collectWithKeyword(dataType, city, count, keyword);
        if ((boolean) result.get("success")) {
            int realCount = result.containsKey("realCount") ? (int) result.get("realCount") : 0;
            return R.ok("采集完成,共" + result.get("count") + "条(含API " + realCount + "条)", result);
        }
        return R.error(result.get("error").toString());
    }

    @PostMapping("/collect-all-with-keyword")
    public R collectAllWithKeyword(@RequestParam(defaultValue = "上海") String city,
                                    @RequestParam(defaultValue = "500") int count,
                                    @RequestParam(defaultValue = "新闻") String keyword,
                                    HttpServletRequest request) {
        if (!isAdmin(request)) return R.error(403, "需要管理员权限才能执行采集");
        return R.ok(collectionService.collectAllWithKeyword(city, count, keyword));
    }

    @GetMapping("/query")
    public R query(@RequestParam String dataType,
                   @RequestParam(defaultValue = "") String dateStr) {
        if (dateStr.isEmpty()) {
            dateStr = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date());
        }
        List<CityData> data = collectionService.queryByType(dataType, dateStr);
        return R.ok(data);
    }

    @GetMapping("/latest")
    public R latest(@RequestParam String dataType,
                    @RequestParam(defaultValue = "50") int limit) {
        return R.ok(collectionService.queryLatest(dataType, limit));
    }

    @GetMapping("/collect-status")
    public R collectStatus() {
        return R.ok(collectionService.getCollectStatus());
    }

    private boolean isAdmin(HttpServletRequest request) {
        Object role = request.getAttribute("role");
        return "admin".equals(role);
    }
}
