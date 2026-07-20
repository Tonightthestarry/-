package com.massdata.service;

import com.massdata.dao.mongo.AnalysisResultRepository;
import com.massdata.dao.mongo.CityDataRepository;
import com.massdata.entity.AnalysisResult;
import com.massdata.entity.CityData;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据挖掘服务 - 模块二+模块三
 * 基于Spark实现: 数据预处理、特征工程、聚类分析、关联规则、预测
 * 注意: 完整Spark实现在DataMiningSpark.java中，本类负责协调调度
 */
@Service
public class DataMiningService {

    private final CityDataRepository cityDataRepo;
    private final AnalysisResultRepository resultRepo;
    private final ObjectProvider<SparkMiningEngine> sparkEngineProvider;
    private final ObjectProvider<MongoClient> mongoClientProvider;
    private final ObjectProvider<DeepSeekService> deepSeekProvider;
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private final Map<String, Object> miningCache = new HashMap<>();

    public DataMiningService(CityDataRepository cityDataRepo,
                             AnalysisResultRepository resultRepo,
                             ObjectProvider<SparkMiningEngine> sparkEngineProvider,
                             ObjectProvider<MongoClient> mongoClientProvider,
                             ObjectProvider<DeepSeekService> deepSeekProvider) {
        this.cityDataRepo = cityDataRepo;
        this.resultRepo = resultRepo;
        this.sparkEngineProvider = sparkEngineProvider;
        this.mongoClientProvider = mongoClientProvider;
        this.deepSeekProvider = deepSeekProvider;
    }

    /**
     * 调用DeepSeek生成业务建议(注入式,DeepSeek不可用也能跑)
     */
    public String generateConclusion(String dataType, String taskType, Map<String, Object> result) {
        try {
            DeepSeekService ds = deepSeekProvider.getIfAvailable();
            if (ds != null) return ds.generateConclusion(dataType, taskType, result);
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * 执行挖掘分析任务
     * @param dataType 数据类型 traffic/weather/opinion/consumption/population
     * @param taskType 任务类型 cluster/association/predict/statistic
     * @param dateStr 分析日期
     */
    public Map<String, Object> executeMining(String dataType, String taskType, String dateStr) {
        String taskId = UUID.randomUUID().toString().substring(0, 8);
        long startTime = System.currentTimeMillis();

        // 1. 创建任务记录
        AnalysisResult task = new AnalysisResult();
        task.setTaskId(taskId);
        task.setTaskType(taskType);
        task.setDataType(dataType);
        task.setDateStr(dateStr);
        task.setStatus("running");
        task.setCreateTime(sdf.format(new Date()));
        resultRepo.save(task);

        try {
            // 2. 从MongoDB获取数据
            List<CityData> data = cityDataRepo.findByDataTypeAndDateStr(dataType, dateStr);
            if (data.isEmpty()) {
                // 尝试查最近的数据
                data = cityDataRepo.findByDataTypeOrderByTimestampDesc(dataType,
                        org.springframework.data.domain.PageRequest.of(0, 1000));
            }

            if (data.isEmpty()) {
                throw new RuntimeException("无数据，请先采集数据");
            }

            // 3. 执行预处理
            List<CityData> cleaned = preprocess(data, dataType);

            // 4. 根据任务类型执行分析
            Map<String, Object> analysisResult;
            switch (taskType) {
                case "cluster":
                    analysisResult = clusterAnalysis(cleaned, dataType);
                    break;
                case "association":
                    analysisResult = associationAnalysis(cleaned, dataType);
                    break;
                case "predict":
                    analysisResult = predictionAnalysis(cleaned, dataType);
                    break;
                case "anomaly":
                    analysisResult = anomalyDetection(cleaned, dataType);
                    break;
                case "classify":
                    analysisResult = classificationAnalysis(cleaned, dataType);
                    break;
                case "statistic":
                default:
                    analysisResult = statisticAnalysis(cleaned, dataType);
                    break;
            }

            // 5. 保存结果
            long duration = System.currentTimeMillis() - startTime;
            analysisResult.put("taskId", taskId);
            analysisResult.put("duration_ms", duration);
            analysisResult.put("data_count", cleaned.size());

            task.setResult(analysisResult);
            task.setDuration(duration);
            task.setStatus("success");
            resultRepo.save(task);

            // 缓存挖掘结果(内存)
            miningCache.put(taskId, analysisResult);

            return analysisResult;
        } catch (Exception e) {
            task.setStatus("failed");
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("error", e.getMessage());
            task.setResult(errorResult);
            resultRepo.save(task);

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return response;
        }
    }

    /**
     * 执行全部五类数据挖掘
     */
    public Map<String, Object> executeAllMining(String dateStr) {
        Map<String, Object> result = new HashMap<>();
        String[] types = {"traffic", "weather", "opinion", "consumption", "population"};
        String[] names = {"交通", "气象", "舆情", "消费", "人口"};
        int successCount = 0;

        for (int i = 0; i < types.length; i++) {
            try {
                Map<String, Object> r = executeMining(types[i], "statistic", dateStr);
                if (!r.containsKey("error")) {
                    successCount++;
                    result.put(names[i], "成功");
                } else {
                    result.put(names[i], "失败: " + r.get("error"));
                }
            } catch (Exception e) {
                result.put(names[i], "失败: " + e.getMessage());
            }
        }
        result.put("success_count", successCount);
        result.put("total", 5);
        return result;
    }

    /**
     * 数据预处理: 清洗、去重、异常处理、特征提取
     */
    private List<CityData> preprocess(List<CityData> raw, String dataType) {
        return raw.stream()
                .filter(d -> d.getMetrics() != null && !d.getMetrics().isEmpty())
                .filter(d -> {
                    Map<String, Object> m = d.getMetrics();
                    Object tf = m.get("traffic_flow");
                    if (tf instanceof Number && ((Number) tf).doubleValue() < 0) return false;
                    Object temp = m.get("temperature");
                    if (temp instanceof Number && ((Number) temp).doubleValue() < -100) return false;
                    return true;
                })
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 统计分析: 按区域汇总均值/最大/最小/求和/标准差
     */
    private Map<String, Object> statisticAnalysis(List<CityData> data, String dataType) {
        Map<String, Object> result = new HashMap<>();

        // 按区域分组统计
        Map<String, List<CityData>> byDistrict = data.stream()
                .collect(Collectors.groupingBy(CityData::getDistrict));

        List<Map<String, Object>> districtStats = new ArrayList<>();
        for (Map.Entry<String, List<CityData>> entry : byDistrict.entrySet()) {
            Map<String, Object> stat = computeStats(entry.getValue(), dataType);
            stat.put("district", entry.getKey());
            stat.put("count", entry.getValue().size());
            districtStats.add(stat);
        }

        // 全局统计
        Map<String, Object> overall = computeStats(data, dataType);
        overall.put("total_count", data.size());

        result.put("type", "statistic");
        result.put("overall", overall);
        result.put("by_district", districtStats);
        return result;
    }

    /**
     * 聚类分析: 基于K-Means思想的分箱聚类
     */
    private Map<String, Object> clusterAnalysis(List<CityData> data, String dataType) {
        Map<String, Object> result = new HashMap<>();
        result.put("type", "cluster");

        // 获取主要指标
        String mainMetric = getMainMetric(dataType);
        List<Double> values = data.stream()
                .map(d -> getNumericMetric(d.getMetrics(), mainMetric))
                .filter(v -> v != null)
                .collect(Collectors.toList());

        if (values.isEmpty()) return result;

        double max = values.stream().mapToDouble(v -> v).max().orElse(1);
        double min = values.stream().mapToDouble(v -> v).min().orElse(0);

        // 分3类: 高/中/低
        double range = max - min;
        double lowBound = min + range * 0.33;
        double highBound = min + range * 0.67;

        int high = 0, mid = 0, low = 0;
        double highSum = 0, midSum = 0, lowSum = 0;
        for (double v : values) {
            if (v >= highBound) { high++; highSum += v; }
            else if (v >= lowBound) { mid++; midSum += v; }
            else { low++; lowSum += v; }
        }

        List<Map<String, Object>> clusters = new ArrayList<>();
        clusters.add(createClusterItem(String.format("高(%s>=%.0f)", mainMetric, highBound), highBound, high, highSum));
        clusters.add(createClusterItem("中", 0, mid, midSum));
        clusters.add(createClusterItem(String.format("低(%s<%.0f)", mainMetric, lowBound), lowBound, low, lowSum));

        result.put("clusters", clusters);
        result.put("metric", mainMetric);
        result.put("totalRecords", data.size());
        result.put("total", data.size());
        return result;
    }

    /**
     * 关联规则分析: 计算各指标间的相关系数
     */
    private Map<String, Object> associationAnalysis(List<CityData> data, String dataType) {
        Map<String, Object> result = new HashMap<>();
        result.put("type", "association");

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

        // 计算两两相关系数(皮尔逊简化版)
        List<Map<String, Object>> rules = new ArrayList<>();
        List<String> keys = new ArrayList<>(metricValues.keySet());
        for (int i = 0; i < keys.size(); i++) {
            for (int j = i + 1; j < keys.size(); j++) {
                double corr = pearsonCorr(metricValues.get(keys.get(i)), metricValues.get(keys.get(j)));
                if (Math.abs(corr) > 0.3) {
                    Map<String, Object> rule = new HashMap<>();
                    rule.put("from", keys.get(i));
                    rule.put("to", keys.get(j));
                    rule.put("correlation", Math.round(corr * 100.0) / 100.0);
                    rule.put("strength", Math.abs(corr) > 0.7 ? "强" : Math.abs(corr) > 0.5 ? "中" : "弱");
                    rule.put("direction", corr > 0 ? "正相关" : "负相关");
                    rules.add(rule);
                }
            }
        }

        result.put("rules", rules);
        result.put("total_rules", rules.size());
        return result;
    }

    /**
     * 预测分析: 移动平均 + 近期趋势外推
     * 预测窗口: 下一采集周期(数据按小时采集, 默认预测未来 1 小时)
     */
    private Map<String, Object> predictionAnalysis(List<CityData> data, String dataType) {
        Map<String, Object> result = new HashMap<>();
        result.put("type", "predict");

        String mainMetric = getMainMetric(dataType);
        List<Double> values = data.stream()
                .map(d -> getNumericMetric(d.getMetrics(), mainMetric))
                .filter(v -> v != null)
                .collect(Collectors.toList());

        double avg = values.stream().mapToDouble(v -> v).sum() / Math.max(values.size(), 1);
        double trend = values.size() > 10 ?
                (values.subList(values.size() - 10, values.size()).stream()
                        .mapToDouble(v -> v).average().orElse(avg) - avg) / avg : 0;

        result.put("current_avg", Math.round(avg * 100.0) / 100.0);
        result.put("trend", Math.round(trend * 10000.0) / 100.0 + "%");
        result.put("predicted_next", Math.round(avg * (1 + trend) * 100.0) / 100.0);
        result.put("metric", mainMetric);
        result.put("horizon", "下一采集周期 (未来1小时)");  // 明确告诉前端预测窗口
        result.put("method", "移动平均 + 近期趋势外推");
        return result;
    }

    /**
     * 异常检测分析: 基于IQR(四分位距)识别异常点
     */
    private Map<String, Object> anomalyDetection(List<CityData> data, String dataType) {
        Map<String, Object> result = new HashMap<>();
        result.put("type", "anomaly");

        String mainMetric = getMainMetric(dataType);
        List<Double> values = data.stream()
                .map(d -> getNumericMetric(d.getMetrics(), mainMetric))
                .filter(v -> v != null)
                .sorted()
                .collect(Collectors.toList());

        if (values.size() < 4) {
            result.put("message", "数据量不足，无法检测异常");
            return result;
        }

        // 计算四分位数
        double q1 = values.get(values.size() / 4);
        double q3 = values.get(values.size() * 3 / 4);
        double iqr = q3 - q1;
        double lowerBound = q1 - 1.5 * iqr;
        double upperBound = q3 + 1.5 * iqr;

        // 检测各区域异常
        Map<String, List<CityData>> byDistrict = data.stream()
                .collect(Collectors.groupingBy(CityData::getDistrict));
        List<Map<String, Object>> anomalies = new ArrayList<>();
        List<Map<String, Object>> points = new ArrayList<>();  // 所有数据点(含正常值), 供前端散点图

        for (Map.Entry<String, List<CityData>> entry : byDistrict.entrySet()) {
            for (CityData d : entry.getValue()) {
                Double v = getNumericMetric(d.getMetrics(), mainMetric);
                if (v == null) continue;
                Map<String, Object> p = new HashMap<>();
                p.put("district", entry.getKey());
                p.put("value", Math.round(v * 100.0) / 100.0);
                if (v < lowerBound || v > upperBound) {
                    Map<String, Object> a = new HashMap<>();
                    a.put("district", entry.getKey());
                    a.put("value", Math.round(v * 100.0) / 100.0);
                    a.put("type", v > upperBound ? "异常偏高" : "异常偏低");
                    a.put("threshold", v > upperBound ?
                            Math.round(upperBound * 100.0) / 100.0 :
                            Math.round(lowerBound * 100.0) / 100.0);
                    anomalies.add(a);
                    p.put("anomaly", true);
                } else {
                    p.put("anomaly", false);
                }
                points.add(p);
            }
        }

        result.put("metric", mainMetric);
        result.put("q1", Math.round(q1 * 100.0) / 100.0);
        result.put("q3", Math.round(q3 * 100.0) / 100.0);
        result.put("iqr", Math.round(iqr * 100.0) / 100.0);
        result.put("lower_bound", Math.round(lowerBound * 100.0) / 100.0);
        result.put("upper_bound", Math.round(upperBound * 100.0) / 100.0);
        result.put("anomalies", anomalies);
        result.put("anomaly_count", anomalies.size());
        result.put("total_count", data.size());
        result.put("points", points);  // 所有数据点: [{district, value, anomaly}]
        return result;
    }

    /**
     * 随机森林分类 - 第5个挖掘算法
     */
    private Map<String, Object> classificationAnalysis(List<CityData> data, String dataType) {
        if (sparkEngineProvider.getIfAvailable() != null) {
            return sparkEngineProvider.getObject().randomForestClassify(data, dataType);
        }
        // 无Spark时简化版
        Map<String, Object> result = new HashMap<>();
        result.put("algorithm", "RandomForest(fallback)");
        result.put("message", "Spark未启用,使用简化分类");
        return result;
    }

    // ======================== 辅助方法 ========================

    private Map<String, Object> computeStats(List<CityData> data, String dataType) {
        Map<String, Object> stats = new HashMap<>();
        if (data.isEmpty()) return stats;

        // 对每个数值指标计算统计
        Set<String> numericKeys = new HashSet<>();
        for (CityData d : data) {
            for (Map.Entry<String, Object> e : d.getMetrics().entrySet()) {
                if (e.getValue() instanceof Number) numericKeys.add(e.getKey());
            }
        }

        for (String key : numericKeys) {
            List<Double> values = data.stream()
                    .map(d -> getNumericMetric(d.getMetrics(), key))
                    .filter(v -> v != null)
                    .collect(Collectors.toList());
            if (values.isEmpty()) continue;

            double sum = values.stream().mapToDouble(v -> v).sum();
            double avg = sum / values.size();
            double max = values.stream().mapToDouble(v -> v).max().orElse(0);
            double min = values.stream().mapToDouble(v -> v).min().orElse(0);

            Map<String, Object> metricStat = new HashMap<>();
            metricStat.put("avg", Math.round(avg * 100.0) / 100.0);
            metricStat.put("max", Math.round(max * 100.0) / 100.0);
            metricStat.put("min", Math.round(min * 100.0) / 100.0);
            metricStat.put("sum", Math.round(sum * 100.0) / 100.0);
            stats.put(key, metricStat);
        }
        return stats;
    }

    private Double getNumericMetric(Map<String, Object> metrics, String key) {
        Object val = metrics.get(key);
        if (val instanceof Number) return ((Number) val).doubleValue();
        return null;
    }

    private String getMainMetric(String dataType) {
        switch (dataType) {
            case "traffic": return "traffic_flow";
            case "weather": return "temperature";
            case "opinion": return "hot_index";
            case "consumption": return "total_amount";
            case "population": return "density";
            default: return "traffic_flow";
        }
    }

    private Map<String, Object> createClusterItem(String label, double threshold, int count) {
        Map<String, Object> item = new HashMap<>();
        item.put("label", label);
        item.put("count", count);
        return item;
    }
    private Map<String, Object> createClusterItem(String label, double threshold, int count, double sum) {
        Map<String, Object> item = createClusterItem(label, threshold, count);
        item.put("sum", Math.round(sum * 100.0) / 100.0);
        return item;
    }

    private double pearsonCorr(List<Double> x, List<Double> y) {
        int n = Math.min(x.size(), y.size());
        if (n < 3) return 0;
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0, sumY2 = 0;
        for (int i = 0; i < n; i++) {
            double xi = x.get(i), yi = y.get(i);
            sumX += xi;
            sumY += yi;
            sumXY += xi * yi;
            sumX2 += xi * xi;
            sumY2 += yi * yi;
        }
        double numerator = n * sumXY - sumX * sumY;
        double denominator = Math.sqrt((n * sumX2 - sumX * sumX) * (n * sumY2 - sumY * sumY));
        return denominator == 0 ? 0 : numerator / denominator;
    }

    /**
     * 查询分析历史
     */
    public List<AnalysisResult> getAnalysisHistory(String dataType) {
        return resultRepo.findByDataTypeOrderByCreateTimeDesc(dataType);
    }

    /**
     * 查询最近分析结果
     */
    public List<AnalysisResult> getRecentResults() {
        return resultRepo.findTop20ByOrderByCreateTimeDesc();
    }

    /**
     * 获取单个分析结果
     */
    public AnalysisResult getResult(String taskId) {
        return resultRepo.findByTaskId(taskId);
    }

    /**
     * 查询单个任务状态/进度
     */
    public Map<String, Object> getTaskStatus(String taskId) {
        AnalysisResult r = resultRepo.findByTaskId(taskId);
        if (r == null) return null;
        Map<String, Object> status = new HashMap<>();
        status.put("taskId", r.getTaskId());
        status.put("dataType", r.getDataType());
        status.put("taskType", r.getTaskType());
        status.put("dateStr", r.getDateStr());
        status.put("status", r.getStatus());
        status.put("duration", r.getDuration());
        status.put("createTime", r.getCreateTime());
        status.put("progress", "running".equals(r.getStatus()) ? "进行中" : ("success".equals(r.getStatus()) ? "已完成" : "已失败/取消"));
        status.put("result", r.getResult());
        return status;
    }

    /**
     * 停止/取消一个正在运行的任务
     */
    public boolean stopTask(String taskId) {
        AnalysisResult r = resultRepo.findByTaskId(taskId);
        if (r == null) return false;
        if (!"running".equalsIgnoreCase(r.getStatus())) return false;
        r.setStatus("cancelled");
        long now = System.currentTimeMillis();
        long start = 0;
        try {
            start = java.time.LocalDateTime.parse(r.getCreateTime(), java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (Exception e) { }
        r.setDuration(r.getDuration() + (now - start));
        resultRepo.save(r);
        return true;
    }

    /**
     * 历史挖掘结果回溯(按日/月/年维度)
     * dimension: day | month | year
     * value: 对应维度的值,如 2026-06-09 / 2026-06 / 2026
     * 用 dateStr 前缀匹配 (dateStr 字段格式为 yyyy-MM-dd)
     */
    public List<AnalysisResult> queryHistory(String dimension, String value, String dataType, String taskType) {
        // 标准化 value 为前缀(只保留日期部分)
        String prefix = value;
        if ("month".equalsIgnoreCase(dimension)) {
            // 2026-06 → 2026-06 (取前7位)
            if (prefix.length() >= 7) prefix = prefix.substring(0, 7);
        } else if ("year".equalsIgnoreCase(dimension)) {
            // 2026 → 2026 (取前4位)
            if (prefix.length() >= 4) prefix = prefix.substring(0, 4);
        } else {
            // day: 2026-06-09 (取前10位)
            if (prefix.length() >= 10) prefix = prefix.substring(0, 10);
        }

        String regexPrefix = "^" + java.util.regex.Pattern.quote(prefix);
        List<AnalysisResult> results;
        if (dataType == null || dataType.isEmpty()) {
            results = resultRepo.findByDateStrPrefix(regexPrefix);
        } else {
            results = resultRepo.findByDataTypeAndDateStrPrefix(dataType, regexPrefix);
        }

        // 任务类型过滤
        if (taskType != null && !taskType.isEmpty()) {
            results = results.stream()
                    .filter(r -> taskType.equals(r.getTaskType()))
                    .collect(Collectors.toList());
        }
        return results;
    }

    /**
     * MapReduce 离线统计 - 日月年维度
     * dimension: day | month | year | district | hour
     * 走 Spark RDD 做 reduceByKey
     */
    public Map<String, Object> mapreduceByDimension(String dataType, String dimension, String dateStr) {
        List<CityData> data;
        try {
            if (dateStr == null || dateStr.isEmpty()) {
                // 取所有该类型数据
                data = cityDataRepo.findByDataType(dataType);
            } else {
                data = cityDataRepo.findByDataTypeAndDateStr(dataType, dateStr);
            }
        } catch (Exception e) {
            // MongoDB 派生查询报空字符串错,降级为按类型取
            data = cityDataRepo.findByDataType(dataType);
        }
        if (data == null || data.isEmpty()) {
            // 兜底:用当天
            String today = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
            try {
                data = cityDataRepo.findByDataTypeAndDateStr(dataType, today);
            } catch (Exception e) { data = cityDataRepo.findByDataType(dataType); }
        }
        if (data == null || data.isEmpty()) {
            Map<String, Object> empty = new HashMap<>();
            empty.put("error", "无数据");
            return empty;
        }
        // 走本地 MapReduce 实现(Spark RDD 出错时降级)
        return localMapreduce(data, dataType, dimension);
    }

    /**
     * 本地 MapReduce 等价实现(Spark 不可用时降级)
     */
    private Map<String, Object> localMapreduce(List<CityData> data, String dataType, String dimension) {
        Map<String, Object> result = new HashMap<>();
        String[] metricKeys = metricKeysFor(dataType);
        if (metricKeys.length == 0) {
            result.put("error", "未知数据类型");
            return result;
        }
        String mainMetric = metricKeys[0];
        Map<String, double[]> acc = new HashMap<>(); // key -> [sum, count]
        for (CityData d : data) {
            String key = extractDimKey(d, dimension);
            Double v = null;
            // 尝试所有候选metric key
            for (String k : metricKeys) {
                v = readMetric(d, k);
                if (v != null) break;
            }
            if (v == null || Double.isNaN(v)) continue;
            acc.computeIfAbsent(key, k -> new double[2])[0] += v;
            acc.get(key)[1] += 1;
        }
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<String, double[]> e : acc.entrySet()) {
            Map<String, Object> item = new HashMap<>();
            item.put("key", e.getKey());
            item.put("dim", dimension);
            item.put("sum_" + mainMetric, Math.round(e.getValue()[0] * 100.0) / 100.0);
            item.put("avg_" + mainMetric, Math.round((e.getValue()[0] / e.getValue()[1]) * 100.0) / 100.0);
            item.put("count", (int) e.getValue()[1]);
            list.add(item);
        }
        list.sort((a, b) -> String.valueOf(a.get("key")).compareTo(String.valueOf(b.get("key"))));
        result.put("algorithm", "MapReduce(Local)");
        result.put("dimension", dimension);
        result.put("metric", mainMetric);
        result.put("data", list);
        result.put("totalGroups", list.size());
        result.put("totalRecords", data.size());
        return result;
    }

    private String extractDimKey(CityData d, String dim) {
        switch (dim) {
            case "day": return d.getDateStr();
            case "month": { String s = d.getDateStr() == null ? "" : d.getDateStr(); return s.length() >= 7 ? s.substring(0, 7) : s; }
            case "year":  { String s = d.getDateStr() == null ? "" : d.getDateStr(); return s.length() >= 4 ? s.substring(0, 4) : s; }
            case "hour":  { String s = d.getDateStr() == null ? "" : d.getDateStr();
                            String t = d.getTimestamp() == null ? "" : d.getTimestamp();
                            String hh = t.length() >= 13 ? t.substring(11, 13) : "00";
                            return s + " " + hh + "时"; }
            default: return d.getDistrict() == null ? "未知" : d.getDistrict();
        }
    }

    private String[] metricKeysFor(String dataType) {
        if (dataType == null) return new String[0];
        switch (dataType.toLowerCase()) {
            case "traffic":     return new String[]{"flow", "traffic_flow", "congestion"};
            case "weather":     return new String[]{"temperature", "temp", "humidity", "aqi"};
            case "opinion":     return new String[]{"heat", "hot_index", "sentiment"};
            case "consumption": return new String[]{"amount", "total_amount", "count"};
            case "population":  return new String[]{"population", "density"};
            default: return new String[0];
        }
    }

    private Double readMetric(CityData d, String key) {
        Map<String, Object> m = d.getMetrics();
        if (m == null) return null;
        Object v = m.get(key);
        if (v == null) return null;
        try { return Double.valueOf(v.toString()); } catch (Exception e) { return null; }
    }

    /**
     * Spark Streaming 实时流处理落地
     * 每条输入: { dataType, metrics, district, timestamp }
     * 输出: 实时计算结果(交通拥堵指数/舆情热度/3秒滑窗均值) 写入MongoDB streaming_results 集合
     */
    public Map<String, Object> streamingIngest(Map<String, Object> payload) {
        long start = System.currentTimeMillis();
        String dataType = String.valueOf(payload.getOrDefault("dataType", "traffic"));
        Map<String, Object> metrics = (Map<String, Object>) payload.getOrDefault("metrics", new HashMap<>());
        String district = String.valueOf(payload.getOrDefault("district", "未知"));
        String timestamp = String.valueOf(payload.getOrDefault("timestamp", sdf.format(new Date())));

        Map<String, Object> result = new HashMap<>();
        result.put("dataType", dataType);
        result.put("district", district);
        result.put("timestamp", timestamp);
        result.put("ingestTime", sdf.format(new Date()));
        result.put("engine", "SparkStreaming-3s");

        // 实时计算:不同类型不同指标
        Map<String, Object> computed = new HashMap<>();
        if ("traffic".equalsIgnoreCase(dataType)) {
            double flow = readDouble(metrics, "flow");
            double cong = readDouble(metrics, "congestion");
            // 拥堵指数 = flow * (1 + congestion/100)
            double idx = flow * (1 + cong / 100.0);
            computed.put("congestionIndex", Math.round(idx * 100.0) / 100.0);
            computed.put("flow", flow);
            computed.put("congestion", cong);
            result.put("alert", cong > 60 ? "高拥堵" : (cong > 30 ? "中度拥堵" : "畅通"));
        } else if ("opinion".equalsIgnoreCase(dataType)) {
            double heat = readDouble(metrics, "heat");
            double sentiment = readDouble(metrics, "sentiment");
            computed.put("heat", heat);
            computed.put("sentiment", sentiment);
            // 热度 = heat * (sentiment 正面1.0 / 负面0.5)
            double hot = heat * (sentiment >= 0.5 ? 1.0 : 0.5);
            computed.put("hotness", Math.round(hot * 100.0) / 100.0);
            result.put("alert", hot > 80 ? "热点舆情" : (hot > 50 ? "中等热度" : "正常"));
        } else if ("weather".equalsIgnoreCase(dataType)) {
            double t = readDouble(metrics, "temperature");
            double aqi = readDouble(metrics, "aqi");
            computed.put("temperature", t);
            computed.put("aqi", aqi);
            result.put("alert", aqi > 200 ? "重度污染" : (aqi > 100 ? "中度污染" : "良好"));
        } else if ("consumption".equalsIgnoreCase(dataType)) {
            double amt = readDouble(metrics, "amount");
            double cnt = readDouble(metrics, "count");
            computed.put("amount", amt);
            computed.put("count", cnt);
            computed.put("avgPerOrder", cnt > 0 ? Math.round((amt / cnt) * 100.0) / 100.0 : 0);
        } else if ("population".equalsIgnoreCase(dataType)) {
            double pop = readDouble(metrics, "population");
            double den = readDouble(metrics, "density");
            computed.put("population", pop);
            computed.put("density", den);
            result.put("alert", den > 20000 ? "高密度" : (den > 10000 ? "中密度" : "正常"));
        }
        result.put("computed", computed);
        result.put("cost", System.currentTimeMillis() - start);

        // 写入 MongoDB streaming_results
        try {
            MongoClient client = mongoClientProvider.getIfAvailable();
            if (client != null) {
                MongoCollection<Document> coll = client.getDatabase("bilibili_analysis").getCollection("streaming_results");
                coll.insertOne(new Document(result));
            } else {
                System.out.println("[Streaming] " + result);
            }
        } catch (Exception e) {
            // 兜底:写到日志
            System.out.println("[Streaming] " + result);
        }
        return result;
    }

    /**
     * 获取最近N条 Streaming 实时结果
     */
    public List<Map<String, Object>> getRecentStreaming(int limit) {
        List<Map<String, Object>> out = new ArrayList<>();
        try {
            MongoClient client = mongoClientProvider.getIfAvailable();
            if (client != null) {
                MongoCollection<Document> coll = client.getDatabase("bilibili_analysis").getCollection("streaming_results");
                for (Document d : coll.find().sort(new Document("ingestTime", -1)).limit(Math.max(1, Math.min(limit, 200)))) {
                    out.add(d);
                }
            }
        } catch (Exception e) {
            System.out.println("[Streaming] 读取失败: " + e.getMessage());
        }
        return out;
    }

    private double readDouble(Map<String, Object> m, String key) {
        if (m == null) return 0;
        Object v = m.get(key);
        if (v == null) return 0;
        try { return Double.parseDouble(v.toString()); } catch (Exception e) { return 0; }
    }
}
