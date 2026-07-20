package com.massdata.service;

import com.massdata.entity.CityData;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.ml.clustering.KMeans;
import org.apache.spark.ml.clustering.KMeansModel;
import org.apache.spark.ml.classification.RandomForestClassificationModel;
import org.apache.spark.ml.classification.RandomForestClassifier;
import org.apache.spark.ml.feature.VectorAssembler;
import org.apache.spark.sql.*;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import scala.Tuple2;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Spark数据挖掘引擎 - 模块二+三+四
 * 使用Spark RDD/DataFrame/MLlib进行分布式计算
 *
 * 五大算法: K-Means聚类、关联规则(FP-Growth)、线性回归预测、
 *           MapReduce离线统计、Spark Streaming实时分析
 */
@Service
@ConditionalOnProperty(name = "spark.enabled", havingValue = "true")
public class SparkMiningEngine {

    private final JavaSparkContext sc;
    private final SparkSession spark;

    public SparkMiningEngine(JavaSparkContext sc, SparkSession spark) {
        this.sc = sc;
        this.spark = spark;
    }

    /**
     * K-Means聚类分析 - 对城市数据进行聚类
     * 将数据分为爆款/热门/普通/冷门四级
     */
    public Map<String, Object> kmeansCluster(List<CityData> data, String dataType) {
        Map<String, Object> result = new HashMap<>();

        try {
            // 提取数值特征
            List<double[]> features = extractFeatures(data, dataType);
            if (features.isEmpty()) {
                result.put("error", "无有效特征数据");
                return result;
            }

            // 构建Spark DataFrame
            StructType schema = new StructType()
                    .add("id", DataTypes.IntegerType)
                    .add("features", "vector");

            List<Row> rows = new ArrayList<>();
            List<String> featureNames = getFeatureNames(dataType);
            int n = featureNames.size();

            for (int i = 0; i < features.size(); i++) {
                rows.add(RowFactory.create(i,
                        org.apache.spark.ml.linalg.Vectors.dense(features.get(i))));
            }

            Dataset<Row> df = spark.createDataFrame(rows, schema);

            // K-Means训练(k=4: 爆款/热门/普通/冷门)
            KMeans kmeans = new KMeans()
                    .setK(4)
                    .setSeed(1L)
                    .setFeaturesCol("features")
                    .setPredictionCol("cluster");

            KMeansModel model = kmeans.fit(df);
            Dataset<Row> predictions = model.transform(df);

            // 统计聚类分布
            Map<Integer, Long> clusterCounts = predictions
                    .groupBy("cluster").count()
                    .collectAsList().stream()
                    .collect(Collectors.toMap(
                            r -> r.getInt(0),
                            r -> r.getLong(1)
                    ));

            // 获取聚类中心并排序
            double[][] centers = new double[4][n];
            for (int i = 0; i < 4; i++) {
                centers[i] = model.clusterCenters()[i].toArray();
            }

            // 按均值排序标注等级
            double[] centerAverages = new double[4];
            for (int i = 0; i < 4; i++) {
                centerAverages[i] = Arrays.stream(centers[i]).average().orElse(0);
            }
            Integer[] sortedIdx = {0, 1, 2, 3};
            Arrays.sort(sortedIdx, Comparator.comparingDouble(i -> -centerAverages[i]));
            String[] labels = {"爆款", "热门", "普通", "冷门"};

            List<Map<String, Object>> clusterResults = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                int idx = sortedIdx[i];
                Map<String, Object> item = new HashMap<>();
                item.put("label", labels[i]);
                item.put("count", clusterCounts.getOrDefault(idx, 0L));
                item.put("center", centers[idx]);
                clusterResults.add(item);
            }

            result.put("algorithm", "K-Means");
            result.put("k", 4);
            result.put("clusters", clusterResults);
            result.put("total", data.size());
        } catch (Exception e) {
            result.put("error", e.getMessage());
        }
        return result;
    }

    /**
     * 关联规则挖掘 - 使用RDD计算频繁项集
     * 简化版Apriori思路：统计指标间的共现关系
     */
    public Map<String, Object> associationRules(List<CityData> data, String dataType) {
        Map<String, Object> result = new HashMap<>();

        try {
            List<String> featureNames = getFeatureNames(dataType);

            // 转换为离散特征(高/中/低)
            List<List<String>> transactions = new ArrayList<>();
            for (CityData d : data) {
                List<String> items = new ArrayList<>();
                for (String fn : featureNames) {
                    Object val = d.getMetrics().get(fn);
                    if (val instanceof Number) {
                        double v = ((Number) val).doubleValue();
                        if (v > 0.7) items.add(fn + "_高");
                        else if (v > 0.3) items.add(fn + "_中");
                        else items.add(fn + "_低");
                    }
                }
                transactions.add(items);
            }

            if (transactions.isEmpty()) {
                result.put("error", "无有效数据");
                return result;
            }

            // RDD计算单项支持度
            JavaRDD<List<String>> transRDD = sc.parallelize(transactions);
            JavaRDD<String> items = transRDD.flatMap(List::iterator);
            Map<String, Long> itemCounts = items.countByValue();

            int totalTrans = transactions.size();
            double minSupport = 0.3;

            // 过滤频繁单项
            Map<String, Double> freqItems = new HashMap<>();
            itemCounts.forEach((k, v) -> {
                double support = (double) v / totalTrans;
                if (support >= minSupport) freqItems.put(k, support);
            });

            // 生成2项集关联规则
            List<Map<String, Object>> rules = new ArrayList<>();
            List<String> freqKeys = new ArrayList<>(freqItems.keySet());
            for (int i = 0; i < freqKeys.size(); i++) {
                for (int j = i + 1; j < freqKeys.size(); j++) {
                    String itemA = freqKeys.get(i);
                    String itemB = freqKeys.get(j);
                    // 计算共现支持度
                    long coCount = transRDD.filter(t -> t.contains(itemA) && t.contains(itemB)).count();
                    double support = (double) coCount / totalTrans;
                    if (support >= minSupport * 0.5) {
                        double confidence = (double) coCount / itemCounts.get(itemA);
                        Map<String, Object> rule = new HashMap<>();
                        rule.put("antecedent", itemA);
                        rule.put("consequent", itemB);
                        rule.put("support", Math.round(support * 1000.0) / 1000.0);
                        rule.put("confidence", Math.round(confidence * 1000.0) / 1000.0);
                        rules.add(rule);
                    }
                }
            }

            result.put("algorithm", "AssociationRules(RDD)");
            result.put("freq_items", freqItems.size());
            result.put("rules", rules);
            result.put("total_rules", rules.size());
        } catch (Exception e) {
            result.put("error", e.getMessage());
        }
        return result;
    }

    /**
     * MapReduce离线统计 - 按维度(日/月/年)聚合
     * level: day | month | year | district | hour
     */
    public Map<String, Object> offlineAggregation(List<CityData> data, String dataType, String level) {
        Map<String, Object> result = new HashMap<>();

        try {
            String mainMetric = getMainMetric(dataType);

            // 按维度分组键
            final String dimLevel = level == null ? "district" : level.toLowerCase();

            JavaRDD<CityData> dataRDD = sc.parallelize(data);

            // 1. 计算各分组的 累加 和 计数(用于求平均)
            JavaPairRDD<String, Tuple2<Double, Integer>> pairRDD = dataRDD
                    .mapToPair(d -> {
                        Double val = getMetricValue(d, mainMetric);
                        String key = extractKey(d, dimLevel);
                        return new Tuple2<>(key, new Tuple2<>(val, 1));
                    })
                    .reduceByKey((a, b) -> new Tuple2<>(a._1 + b._1, a._2 + b._2));

            List<Map<String, Object>> summary = new ArrayList<>();
            for (Tuple2<String, Tuple2<Double, Integer>> t : pairRDD.collect()) {
                Map<String, Object> item = new HashMap<>();
                double sum = t._2._1;
                int count = t._2._2;
                item.put("key", t._1);
                item.put("dim", dimLevel);
                item.put("sum_" + mainMetric, Math.round(sum * 100.0) / 100.0);
                item.put("avg_" + mainMetric, Math.round((sum / count) * 100.0) / 100.0);
                item.put("count", count);
                summary.add(item);
            }

            // 按 key 排序
            summary.sort((a, b) -> String.valueOf(a.get("key")).compareTo(String.valueOf(b.get("key"))));

            result.put("algorithm", "MapReduce");
            result.put("dimension", dimLevel);
            result.put("metric", mainMetric);
            result.put("data", summary);
            result.put("totalGroups", summary.size());
            result.put("totalRecords", data.size());
        } catch (Exception e) {
            result.put("error", e.getMessage());
        }
        return result;
    }

    /**
     * 根据维度级别提取分组键
     * day     -> yyyy-MM-dd (从dateStr)
     * month   -> yyyy-MM
     * year    -> yyyy
     * district-> 区域名
     * hour    -> yyyy-MM-dd HH时
     */
    private String extractKey(CityData d, String dimLevel) {
        switch (dimLevel) {
            case "day":
                return d.getDateStr();
            case "month": {
                String ds = d.getDateStr() == null ? "" : d.getDateStr();
                return ds.length() >= 7 ? ds.substring(0, 7) : ds;
            }
            case "year": {
                String ds2 = d.getDateStr() == null ? "" : d.getDateStr();
                return ds2.length() >= 4 ? ds2.substring(0, 4) : ds2;
            }
            case "hour": {
                String ds3 = d.getDateStr() == null ? "" : d.getDateStr();
                String ts = d.getTimestamp() == null ? "" : d.getTimestamp();
                String hh = ts.length() >= 13 ? ts.substring(11, 13) : "00";
                return ds3 + " " + hh + "时";
            }
            default:
                return d.getDistrict() == null ? "未知" : d.getDistrict();
        }
    }

    /**
     * 线性回归预测
     */
    public Map<String, Object> linearRegressionPredict(List<CityData> data, String dataType) {
        Map<String, Object> result = new HashMap<>();
        try {
            String mainMetric = getMainMetric(dataType);
            // 简化版: 基于历史数据做线性趋势预测
            List<Double> values = data.stream()
                    .map(d -> getMetricValue(d, mainMetric))
                    .filter(v -> !Double.isNaN(v))
                    .collect(Collectors.toList());

            if (values.size() < 3) {
                result.put("error", "数据不足");
                return result;
            }

            int n = values.size();
            double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
            for (int i = 0; i < n; i++) {
                sumX += i;
                sumY += values.get(i);
                sumXY += i * values.get(i);
                sumX2 += i * i;
            }

            double slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
            double intercept = (sumY - slope * sumX) / n;

            List<Double> predicted = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                predicted.add(intercept + slope * (n + i));
            }

            result.put("algorithm", "LinearRegression");
            result.put("slope", Math.round(slope * 1000.0) / 1000.0);
            result.put("intercept", Math.round(intercept * 100.0) / 100.0);
            result.put("predictions", predicted.stream()
                    .map(v -> Math.round(v * 100.0) / 100.0)
                    .collect(Collectors.toList()));
        } catch (Exception e) {
            result.put("error", e.getMessage());
        }
        return result;
    }

    /**
     * RandomForest分类 - 第5个挖掘算法
     * 将数据分为高/中/低三级，训练随机森林模型进行分类预测
     */
    public Map<String, Object> randomForestClassify(List<CityData> data, String dataType) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<String> fn = getFeatureNames(dataType);
            List<double[]> features = extractFeatures(data, dataType);
            if (features.isEmpty()) {
                result.put("error", "无有效特征数据");
                return result;
            }
            int n = fn.size();
            // 补齐到4列
            int cols = Math.max(n, 4);

            // 用BeanEncoder最简单：创建一个动态类
            // 改用 map 创建 DataFrame
            String[] colNames = new String[cols];
            for (int i = 0; i < cols; i++) colNames[i] = "f" + i;

            List<Row> rows = new ArrayList<>();
            for (double[] f : features) {
                Object[] vals = new Object[cols];
                vals[0] = 0.0; // label 占位
                for (int i = 0; i < Math.min(n, cols); i++) vals[i] = f[i];
                rows.add(RowFactory.create(vals));
            }
            // 计算标签
            String mainMetric = getMainMetric(dataType);
            int mainIdx = fn.indexOf(mainMetric);
            if (mainIdx < 0) mainIdx = 0;
            List<Row> labeled = new ArrayList<>();
            for (int i = 0; i < rows.size(); i++) {
                Row r = rows.get(i);
                Object[] arr = new Object[cols + 1];
                arr[0] = computeLabel(features.get(i)[mainIdx]);
                for (int j = 0; j < cols; j++) arr[j + 1] = r.get(j);
                labeled.add(RowFactory.create(arr));
            }

            StructType schema = new StructType().add("label", DataTypes.DoubleType);
            for (int i = 0; i < cols; i++) schema = schema.add(colNames[i], DataTypes.DoubleType);

            Dataset<Row> df0 = spark.createDataFrame(labeled, schema);
            // 用 VectorAssembler 合成 features
            String[] inputs = new String[cols];
            for (int i = 0; i < cols; i++) inputs[i] = colNames[i];
            VectorAssembler assembler = new VectorAssembler()
                    .setInputCols(inputs)
                    .setOutputCol("features");
            Dataset<Row> df = assembler.transform(df0);

            // 训练
            RandomForestClassifier rf = new RandomForestClassifier()
                    .setLabelCol("label")
                    .setFeaturesCol("features")
                    .setNumTrees(10);
            RandomForestClassificationModel model = rf.fit(df);
            Dataset<Row> predictions = model.transform(df);

            Map<Integer, Long> dist = new LinkedHashMap<>();
            for (Row r : predictions.collectAsList()) {
                int pred = (int) r.getDouble(r.fieldIndex("prediction"));
                dist.merge(pred, 1L, Long::sum);
            }

            double[] importance = model.featureImportances().toArray();
            List<Map<String, Object>> fi = new ArrayList<>();
            for (int i = 0; i < fn.size(); i++) {
                Map<String, Object> item = new HashMap<>();
                item.put("feature", fn.get(i));
                double imp = i < importance.length ? importance[i] : 0.0;
                item.put("importance", Math.round(imp * 10000.0) / 10000.0);
                fi.add(item);
            }

            result.put("algorithm", "RandomForest");
            result.put("numTrees", 10);
            result.put("prediction_distribution", dist);
            result.put("feature_importance", fi);
            result.put("total", data.size());
        } catch (Exception e) {
            result.put("error", e.getMessage());
        }
        return result;
    }

    private double computeLabel(double v) {
        if (v > 0.7) return 2.0;
        if (v > 0.3) return 1.0;
        return 0.0;
    }

    // ======================== 辅助方法 ========================

    private List<double[]> extractFeatures(List<CityData> data, String dataType) {
        List<double[]> features = new ArrayList<>();
        List<String> featureNames = getFeatureNames(dataType);
        for (CityData d : data) {
            double[] f = new double[featureNames.size()];
            boolean valid = true;
            for (int i = 0; i < featureNames.size(); i++) {
                Double val = getMetricValue(d, featureNames.get(i));
                if (val.isNaN()) {
                    valid = false;
                    break;
                }
                f[i] = val;
            }
            if (valid) features.add(f);
        }
        return features;
    }

    private List<String> getFeatureNames(String dataType) {
        switch (dataType) {
            case "traffic": return Arrays.asList("traffic_flow", "avg_speed", "congestion_index", "accident_count");
            case "weather": return Arrays.asList("temperature", "humidity", "wind_speed", "precipitation", "aqi");
            case "opinion": return Arrays.asList("hot_index", "positive_ratio", "mention_count");
            case "consumption": return Arrays.asList("transaction_count", "total_amount", "avg_price", "active_users");
            case "population": return Arrays.asList("resident_pop", "floating_pop", "density");
            default: return Arrays.asList("traffic_flow", "congestion_index");
        }
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

    private Double getMetricValue(CityData d, String key) {
        try {
            if (d == null || key == null) return 0.0;
            Map<String, Object> m = d.getMetrics();
            if (m == null) return 0.0;
            Object val = m.get(key);
            if (val == null) return 0.0;
            if (val instanceof Number) return ((Number) val).doubleValue();
            return Double.parseDouble(val.toString());
        } catch (Exception e) {
            return 0.0;
        }
    }
}
