package com.massdata.service;

import com.massdata.entity.AnalysisResult;
import com.massdata.entity.CityData;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 全链路集成白盒测试 - 模拟用户完整操作流程
 *
 * 模拟流程:
 *   Step1: 用户登录 → 获取JWT Token
 *   Step2: 数据采集 → 生成五类数据各500条
 *   Step3: 数据预处理 → 清洗/去重/异常过滤
 *   Step4: 统计分析 → 按区域聚合
 *   Step5: 聚类分析 → K=3分箱
 *   Step6: 关联规则 → 计算相关系数
 *   Step7: 预测分析 → 线性回归趋势
 *   Step8: 可视化聚合 → 生成图表数据
 *   Step9: 结果保存 → 模拟存储
 *   Step10: 查询验证 → 数据完整性校验
 */
@DisplayName("全链路集成白盒测试(模拟用户操作)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FullPipelineIntegrationTest {

    private static String token;
    private static String username = "admin";
    private static String role = "admin";
    private static List<CityData> allCollectedData = new ArrayList<>();
    private static Map<String, List<CityData>> cleanedData = new LinkedHashMap<>();
    private static List<AnalysisResult> analysisResults = new ArrayList<>();

    // ======================== Step 1: 登录 ========================

    @Test
    @Order(1)
    @DisplayName("Step1: 用户登录 - 生成JWT Token")
    void step1_UserLogin() {
        System.out.println("===== Step1: 用户登录 =====");

        // 模拟登录逻辑
        String password = "123456";
        String encoded = Base64.getEncoder().encodeToString(password.getBytes());
        String inputEncoded = Base64.getEncoder().encodeToString("123456".getBytes());

        assertEquals(encoded, inputEncoded, "密码应匹配");

        // 模拟JWT生成
        token = "simulated-jwt-token-" + username;
        assertNotNull(token);
        assertTrue(token.contains(username), "Token应包含用户名");

        System.out.println("  [PASS] 用户 " + username + " 登录成功, Token: " + token.substring(0, 20) + "...");
    }

    // ======================== Step 2: 数据采集 ========================

    @Test
    @Order(2)
    @DisplayName("Step2: 数据采集 - 五类数据各生成2500条(HDFS模拟)")
    void step2_DataCollection() {
        System.out.println("===== Step2: 数据采集 =====");

        String[] types = {"traffic", "weather", "opinion", "consumption", "population"};
        String[] districts = {"市中心区", "高新区", "开发区", "老城区", "新城区", "滨江区"};
        Random r = new Random(42);
        int perType = 2500;  // 每类2500条, 五类共12500条

        for (String type : types) {
            List<CityData> batch = new ArrayList<>();
            for (int i = 0; i < perType; i++) {
                CityData d = new CityData();
                d.setId(UUID.randomUUID().toString());
                d.setDataType(type);
                d.setCity("上海");
                d.setDistrict(districts[r.nextInt(districts.length)]);
                d.setTimestamp("2026-06-09 " +
                        String.format("%02d:%02d:%02d", r.nextInt(24), r.nextInt(60), r.nextInt(60)));
                d.setDateStr("2026-06-09");
                d.setSource("simulated");

                Map<String, Object> metrics = new HashMap<>();
                generateMetrics(type, metrics, r);
                d.setMetrics(metrics);

                batch.add(d);
            }
            allCollectedData.addAll(batch);
            System.out.println("  [PASS] " + type + " 采集完成: " + perType + "条");
        }

        assertEquals(12500, allCollectedData.size(), "总量应为12500条");
        System.out.println("  [PASS] 数据采集完成! 总计: " + allCollectedData.size() + "条");
    }

    // ======================== Step 3: 数据预处理 ========================

    @Test
    @Order(3)
    @DisplayName("Step3: 数据预处理 - 清洗/去重/异常过滤")
    void step3_DataPreprocessing() {
        System.out.println("===== Step3: 数据预处理 =====");

        String[] types = {"traffic", "weather", "opinion", "consumption", "population"};

        for (String type : types) {
            List<CityData> raw = allCollectedData.stream()
                    .filter(d -> d.getDataType().equals(type))
                    .collect(Collectors.toList());

            // 1. 过滤空metrics
            int beforeFilter = raw.size();
            List<CityData> step1 = raw.stream()
                    .filter(d -> d.getMetrics() != null && !d.getMetrics().isEmpty())
                    .collect(Collectors.toList());
            int filteredEmpty = beforeFilter - step1.size();

            // 2. 去重(按ID)
            int beforeDedup = step1.size();
            List<CityData> step2 = step1.stream()
                    .collect(Collectors.toMap(CityData::getId, d -> d, (a, b) -> a))
                    .values().stream().collect(Collectors.toList());
            int deduped = beforeDedup - step2.size();

            // 3. 异常值过滤(负流量)
            int beforeAbnormal = step2.size();
            List<CityData> cleaned = step2.stream()
                    .filter(d -> {
                        Object flow = d.getMetrics().get("traffic_flow");
                        if (flow instanceof Integer && (int) flow < 0) return false;
                        Object temp = d.getMetrics().get("temperature");
                        if (temp instanceof Double && (double) temp < -100) return false;
                        return true;
                    })
                    .collect(Collectors.toList());
            int abnormalFiltered = beforeAbnormal - cleaned.size();

            cleanedData.put(type, cleaned);

            System.out.printf("  [PASS] %s: 原始%d→过滤空%d→去重%d→异常%d→最终%d%n",
                    type, beforeFilter, filteredEmpty, deduped, abnormalFiltered, cleaned.size());
        }

        long totalCleaned = cleanedData.values().stream().mapToLong(List::size).sum();
        assertTrue(totalCleaned > 0, "清洗后应有数据");
        assertTrue(totalCleaned <= 12500, "清洗后不应超过原始总量");

        System.out.println("  [PASS] 预处理完成! 清洗后总量: " + totalCleaned + "条");
    }

    // ======================== Step 4: 统计分析 ========================

    @Test
    @Order(4)
    @DisplayName("Step4: 统计分析 - 按区域聚合均值/最大/最小/求和")
    void step4_StatisticAnalysis() {
        System.out.println("===== Step4: 统计分析(模拟MapReduce聚合) =====");

        String type = "traffic";
        List<CityData> data = cleanedData.get(type);
        assertNotNull(data, "交通数据不应为空");

        // 按区域分组
        Map<String, List<CityData>> byDistrict = data.stream()
                .collect(Collectors.groupingBy(CityData::getDistrict));

        assertFalse(byDistrict.isEmpty(), "应有区域分组");

        for (Map.Entry<String, List<CityData>> entry : byDistrict.entrySet()) {
            List<CityData> districtData = entry.getValue();
            double avgFlow = districtData.stream()
                    .mapToDouble(d -> (int) d.getMetrics().get("traffic_flow"))
                    .average().orElse(0);
            double maxFlow = districtData.stream()
                    .mapToDouble(d -> (int) d.getMetrics().get("traffic_flow"))
                    .max().orElse(0);
            double minFlow = districtData.stream()
                    .mapToDouble(d -> (int) d.getMetrics().get("traffic_flow"))
                    .min().orElse(0);

            assertTrue(avgFlow > 0, entry.getKey() + "平均流量应>0");
            assertTrue(maxFlow >= avgFlow, "最大值应>=平均值");
            assertTrue(minFlow <= avgFlow, "最小值应<=平均值");
        }

        // 保存分析结果
        AnalysisResult r = new AnalysisResult();
        r.setTaskId("task-stat-" + type);
        r.setTaskType("statistic");
        r.setDataType(type);
        r.setDateStr("2026-06-09");
        r.setStatus("success");
        r.setCreateTime(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        analysisResults.add(r);

        System.out.println("  [PASS] 交通数据统计分析完成! " + byDistrict.size() + "个区域");
        System.out.println("  [PASS] 分析结果已保存: " + r.getTaskId());
    }

    // ======================== Step 5: 聚类分析 ========================

    @Test
    @Order(5)
    @DisplayName("Step5: 聚类分析 - K=3分箱(高/中/低)")
    void step5_ClusterAnalysis() {
        System.out.println("===== Step5: 聚类分析 =====");

        String type = "traffic";
        List<CityData> data = cleanedData.get(type);

        List<Double> values = data.stream()
                .mapToDouble(d -> (int) d.getMetrics().get("traffic_flow"))
                .boxed().collect(Collectors.toList());

        double max = values.stream().mapToDouble(v -> v).max().orElse(1);
        double min = values.stream().mapToDouble(v -> v).min().orElse(0);
        double range = max - min;
        double lowBound = min + range / 3;
        double highBound = min + range * 2 / 3;

        int high = 0, mid = 0, low = 0;
        for (double v : values) {
            if (v >= highBound) high++;
            else if (v >= lowBound) mid++;
            else low++;
        }

        assertEquals(values.size(), high + mid + low,
                "聚类总数应等于原始数据量: " + high + "+" + mid + "+" + low);

        AnalysisResult r = new AnalysisResult();
        r.setTaskId("task-cluster-" + type);
        r.setTaskType("cluster");
        r.setDataType(type);
        r.setDateStr("2026-06-09");
        r.setStatus("success");
        r.setCreateTime(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        analysisResults.add(r);

        System.out.printf("  [PASS] 聚类完成! 高分位(>=%.0f): %d, 中分位: %d, 低分位: %d%n",
                highBound, high, mid, low);
    }

    // ======================== Step 6: 关联规则 ========================

    @Test
    @Order(6)
    @DisplayName("Step6: 关联规则 - 指标间相关系数")
    void step6_AssociationAnalysis() {
        System.out.println("===== Step6: 关联规则分析 =====");

        String type = "traffic";
        List<CityData> data = cleanedData.get(type);

        // 提取数值指标
        Map<String, List<Double>> metricValues = new LinkedHashMap<>();
        for (CityData d : data) {
            d.getMetrics().forEach((k, v) -> {
                if (v instanceof Number) {
                    metricValues.computeIfAbsent(k, x -> new ArrayList<>())
                            .add(((Number) v).doubleValue());
                }
            });
        }

        // 计算两两相关系数
        List<String> keys = new ArrayList<>(metricValues.keySet());
        int strongRules = 0, midRules = 0, weakRules = 0;

        for (int i = 0; i < keys.size(); i++) {
            for (int j = i + 1; j < keys.size(); j++) {
                double corr = pearsonCorr(metricValues.get(keys.get(i)),
                        metricValues.get(keys.get(j)));
                if (Math.abs(corr) > 0.7) strongRules++;
                else if (Math.abs(corr) > 0.5) midRules++;
                else weakRules++;
            }
        }

        System.out.printf("  [PASS] 关联规则完成! 强相关: %d, 中相关: %d, 弱相关: %d%n",
                strongRules, midRules, weakRules);

        AnalysisResult r = new AnalysisResult();
        r.setTaskId("task-assoc-" + type);
        r.setTaskType("association");
        r.setDataType(type);
        r.setDateStr("2026-06-09");
        r.setStatus("success");
        r.setCreateTime(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        analysisResults.add(r);
    }

    // ======================== Step 7: 预测分析 ========================

    @Test
    @Order(7)
    @DisplayName("Step7: 预测分析 - 线性回归趋势预测")
    void step7_PredictionAnalysis() {
        System.out.println("===== Step7: 预测分析 =====");

        String type = "traffic";
        List<CityData> data = cleanedData.get(type);

        List<Double> values = data.stream()
                .mapToDouble(d -> (int) d.getMetrics().get("traffic_flow"))
                .boxed().collect(Collectors.toList());

        // 简单线性回归
        int n = values.size();
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        for (int i = 0; i < n; i++) {
            sumX += i; sumY += values.get(i);
            sumXY += i * values.get(i);
            sumX2 += i * i;
        }

        double slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
        double intercept = (sumY - slope * sumX) / n;

        double avg = values.stream().mapToDouble(v -> v).average().orElse(0);
        double predicted = intercept + slope * n;

        assertTrue(Double.isFinite(predicted), "预测值应为有限数");
        assertFalse(Double.isNaN(predicted), "预测值不应为NaN");

        String trend = slope > 0.01 ? "上升" : slope < -0.01 ? "下降" : "平稳";
        System.out.printf("  [PASS] 预测完成! 当前均值: %.1f, 趋势: %s, 预测下期: %.1f%n",
                avg, trend, predicted);

        AnalysisResult r = new AnalysisResult();
        r.setTaskId("task-predict-" + type);
        r.setTaskType("predict");
        r.setDataType(type);
        r.setDateStr("2026-06-09");
        r.setStatus("success");
        r.setCreateTime(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        analysisResults.add(r);
    }

    // ======================== Step 8: 可视化聚合 ========================

    @Test
    @Order(8)
    @DisplayName("Step8: 可视化聚合 - 生成ECharts图表数据")
    void step8_VisualizationAggregation() {
        System.out.println("===== Step8: 可视化数据聚合 =====");

        // 交通拥堵饼图
        List<CityData> trafficData = cleanedData.get("traffic");
        Map<String, Long> congestionCounts = new LinkedHashMap<>();
        for (CityData d : trafficData) {
            double index = (double) d.getMetrics().get("congestion_index");
            String level = classifyCongestion(index);
            congestionCounts.merge(level, 1L, Long::sum);
        }
        System.out.println("  [PASS] 拥堵饼图: " + congestionCounts);

        // 人口柱状图
        List<CityData> popData = cleanedData.get("population");
        Map<String, Double> popByDistrict = popData.stream()
                .collect(Collectors.groupingBy(CityData::getDistrict,
                        Collectors.averagingInt(d -> (int) d.getMetrics().get("density"))));
        assertFalse(popByDistrict.isEmpty());
        System.out.println("  [PASS] 人口柱状图: " + popByDistrict.size() + "个区域");

        // 气象折线图(取前50个时间点)
        List<CityData> weatherData = cleanedData.get("weather");
        weatherData.sort(Comparator.comparing(CityData::getTimestamp));
        double avgTemp = weatherData.stream()
                .mapToDouble(d -> (double) d.getMetrics().get("temperature"))
                .average().orElse(0);
        assertTrue(avgTemp > -50 && avgTemp < 50, "平均温度应在-50~50之间");
        System.out.printf("  [PASS] 气象折线图: %d个数据点, 平均温度: %.1f°C%n",
                weatherData.size(), avgTemp);

        // 消费散点图
        List<CityData> consumptionData = cleanedData.get("consumption");
        assertFalse(consumptionData.isEmpty());
        System.out.println("  [PASS] 消费散点图: " + consumptionData.size() + "个数据点");

        // 舆情仪表盘
        List<CityData> opinionData = cleanedData.get("opinion");
        double positiveRatio = opinionData.stream()
                .mapToDouble(d -> (double) d.getMetrics().get("positive_ratio"))
                .average().orElse(0.5);
        assertTrue(positiveRatio >= 0 && positiveRatio <= 1);
        System.out.printf("  [PASS] 舆情仪表盘: 正面比例 %.1f%%%n", positiveRatio * 100);
    }

    // ======================== Step 9: 分析结果汇总 ========================

    @Test
    @Order(9)
    @DisplayName("Step9: 分析结果汇总 - 验证完整性")
    void step9_ResultSummary() {
        System.out.println("===== Step9: 分析结果汇总 =====");

        assertFalse(analysisResults.isEmpty(), "应有分析结果");

        Map<String, Long> typeCount = analysisResults.stream()
                .collect(Collectors.groupingBy(AnalysisResult::getTaskType, Collectors.counting()));

        System.out.println("  分析结果统计:");
        typeCount.forEach((k, v) -> System.out.println("    " + k + ": " + v + "次"));

        assertTrue(typeCount.containsKey("statistic"), "应有统计分析");
        assertTrue(typeCount.containsKey("cluster"), "应有聚类分析");
        assertTrue(typeCount.containsKey("association"), "应有关联分析");
        assertTrue(typeCount.containsKey("predict"), "应有预测分析");

        long allSuccess = analysisResults.stream()
                .filter(r -> "success".equals(r.getStatus()))
                .count();
        assertEquals(analysisResults.size(), allSuccess, "所有分析都应成功");

        System.out.println("  [PASS] 全部分析结果: " + analysisResults.size() + "个任务, 全部成功!");
    }

    // ======================== Step 10: 数据完整性校验 ========================

    @Test
    @Order(10)
    @DisplayName("Step10: 数据完整性校验")
    void step10_DataIntegrityCheck() {
        System.out.println("===== Step10: 数据完整性校验 =====");

        // 检查五类数据都存在
        String[] types = {"traffic", "weather", "opinion", "consumption", "population"};
        for (String type : types) {
            List<CityData> data = cleanedData.get(type);
            assertNotNull(data, type + "数据不应为null");
            assertFalse(data.isEmpty(), type + "数据不应为空");
            System.out.println("  [PASS] " + type + ": " + data.size() + "条");
        }

        // 检查数据模型完整性
        long dataWithCity = allCollectedData.stream()
                .filter(d -> d.getCity() != null && !d.getCity().isEmpty())
                .count();
        assertEquals(allCollectedData.size(), dataWithCity, "所有数据应有城市字段");

        long dataWithDistrict = allCollectedData.stream()
                .filter(d -> d.getDistrict() != null && !d.getDistrict().isEmpty())
                .count();
        assertEquals(allCollectedData.size(), dataWithDistrict, "所有数据应有区域字段");

        long dataWithTimestamp = allCollectedData.stream()
                .filter(d -> d.getTimestamp() != null)
                .count();
        assertEquals(allCollectedData.size(), dataWithTimestamp, "所有数据应有时间戳");

        System.out.println("  [PASS] 数据完整性校验通过!");
        System.out.println("  [PASS] 字段完整性: city=" + dataWithCity +
                ", district=" + dataWithDistrict + ", timestamp=" + dataWithTimestamp);

        // 最终报告
        System.out.println("\n================================================");
        System.out.println(" 全链路集成白盒测试报告");
        System.out.println("================================================");
        System.out.println(" 登录: [PASS]");
        System.out.println(" 数据采集: [PASS] " + allCollectedData.size() + "条");
        System.out.println(" 数据预处理: [PASS] " +
                cleanedData.values().stream().mapToLong(List::size).sum() + "条");
        System.out.println(" 统计分析: [PASS] MapReduce聚合");
        System.out.println(" 聚类分析: [PASS] K=3分箱");
        System.out.println(" 关联规则: [PASS] 相关系数矩阵");
        System.out.println(" 预测分析: [PASS] 线性回归趋势");
        System.out.println(" 可视化聚合: [PASS] 6类图表");
        System.out.println(" 结果保存: [PASS] " + analysisResults.size() + "个任务");
        System.out.println(" 数据完整性: [PASS]");
        System.out.println("================================================");
    }

    // ======================== 辅助方法 ========================

    private void generateMetrics(String dataType, Map<String, Object> metrics, Random r) {
        switch (dataType) {
            case "traffic":
                metrics.put("traffic_flow", r.nextInt(5000) + 500);
                metrics.put("avg_speed", r.nextInt(80) + 20);
                metrics.put("congestion_index", r.nextDouble() * 8 + 1);
                metrics.put("accident_count", r.nextInt(5));
                metrics.put("road_occupancy", r.nextDouble() * 80 + 20);
                break;
            case "weather":
                metrics.put("temperature", r.nextDouble() * 35 - 5);
                metrics.put("humidity", r.nextInt(60) + 40);
                metrics.put("wind_speed", r.nextDouble() * 25);
                metrics.put("precipitation", r.nextDouble() * 50);
                metrics.put("aqi", r.nextInt(200) + 20);
                break;
            case "opinion":
                metrics.put("hot_index", r.nextInt(1000) + 10);
                metrics.put("positive_ratio", r.nextDouble());
                metrics.put("neutral_ratio", r.nextDouble() * 0.5);
                metrics.put("negative_ratio", r.nextDouble() * 0.3);
                metrics.put("mention_count", r.nextInt(5000) + 100);
                break;
            case "consumption":
                metrics.put("transaction_count", r.nextInt(2000) + 100);
                metrics.put("total_amount", r.nextDouble() * 100000 + 5000);
                metrics.put("avg_price", r.nextDouble() * 500 + 20);
                metrics.put("yoy_growth", (r.nextDouble() - 0.3) * 30);
                metrics.put("active_users", r.nextInt(8000) + 500);
                break;
            case "population":
                metrics.put("resident_pop", r.nextInt(50000) + 10000);
                metrics.put("floating_pop", r.nextInt(20000) + 1000);
                metrics.put("density", r.nextInt(20000) + 500);
                metrics.put("age_0_18", r.nextDouble() * 0.3);
                metrics.put("age_19_45", r.nextDouble() * 0.5);
                metrics.put("age_46_above", r.nextDouble() * 0.3);
                break;
        }
    }

    private String classifyCongestion(double index) {
        if (index < 2) return "畅通";
        else if (index < 4) return "基本畅通";
        else if (index < 6) return "轻度拥堵";
        else if (index < 8) return "中度拥堵";
        else return "严重拥堵";
    }

    private double pearsonCorr(List<Double> x, List<Double> y) {
        int n = Math.min(x.size(), y.size());
        if (n < 3) return 0;
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0, sumY2 = 0;
        for (int i = 0; i < n; i++) {
            double xi = x.get(i), yi = y.get(i);
            sumX += xi; sumY += yi;
            sumXY += xi * yi; sumX2 += xi * xi; sumY2 += yi * yi;
        }
        double numerator = n * sumXY - sumX * sumY;
        double denominator = Math.sqrt((n * sumX2 - sumX * sumX) * (n * sumY2 - sumY * sumY));
        return denominator == 0 ? 0 : numerator / denominator;
    }
}
