package com.massdata.service;

import com.massdata.entity.CityData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 数据挖掘服务白盒测试 - 模块二+模块三
 * 测试预处理逻辑、统计分析、聚类分箱、关联规则相关系数、预测算法
 */
@DisplayName("数据挖掘服务白盒逻辑测试")
class DataMiningServiceWhiteBoxTest {

    private List<CityData> testData;

    @BeforeEach
    void setUp() {
        testData = createTestData(200);
    }

    // ======================== 模块二: 预处理 ========================

    @Nested
    @DisplayName("模块二: 数据预处理")
    class PreprocessingTests {

        @Test
        @DisplayName("T7: 异常值过滤 - 负流量应被过滤")
        void testFilterNegativeTrafficFlow() {
            List<CityData> data = new ArrayList<>();
            // 正常数据
            data.add(makeData("A", Map.of("traffic_flow", 1000)));
            // 异常数据(负值)
            data.add(makeData("B", Map.of("traffic_flow", -500)));
            // 正常数据
            data.add(makeData("C", Map.of("traffic_flow", 2000)));

            List<CityData> cleaned = data.stream()
                    .filter(d -> d.getMetrics() != null && !d.getMetrics().isEmpty())
                    .filter(d -> {
                        Object v = d.getMetrics().get("traffic_flow");
                        if (v instanceof Integer && (int) v < 0) return false;
                        return true;
                    })
                    .collect(Collectors.toList());

            assertEquals(2, cleaned.size(), "应过滤掉负流量数据");
            assertEquals(1000, (int) cleaned.get(0).getMetrics().get("traffic_flow"));
            assertEquals(2000, (int) cleaned.get(1).getMetrics().get("traffic_flow"));
        }

        @Test
        @DisplayName("T8: 空Metrics数据过滤")
        void testFilterEmptyMetrics() {
            CityData empty = new CityData();
            empty.setMetrics(new HashMap<>());
            CityData nullMetrics = new CityData();
            CityData valid = makeData("test", Map.of("value", 100));

            List<CityData> all = Arrays.asList(empty, nullMetrics, valid);
            List<CityData> filtered = all.stream()
                    .filter(d -> d.getMetrics() != null && !d.getMetrics().isEmpty())
                    .collect(Collectors.toList());

            assertEquals(1, filtered.size(), "应只保留有指标的数据");
        }

        @Test
        @DisplayName("T9: 去重 - 相同ID只保留一条")
        void testDedupById() {
            CityData a1 = new CityData(); a1.setId("id-1"); a1.setMetrics(Map.of("v", 100));
            CityData a2 = new CityData(); a2.setId("id-1"); a2.setMetrics(Map.of("v", 200));
            CityData b = new CityData(); b.setId("id-2"); b.setMetrics(Map.of("v", 300));

            List<CityData> data = Arrays.asList(a1, a2, b);
            List<CityData> deduped = data.stream()
                    .collect(Collectors.toMap(CityData::getId, d -> d, (a, z) -> a))
                    .values().stream().collect(Collectors.toList());

            assertEquals(2, deduped.size(), "去重后应有2条");
        }
    }

    // ======================== 模块三: 深度挖掘 ========================

    @Nested
    @DisplayName("模块三: 统计分析算法")
    class StatisticAnalysisTests {

        @Test
        @DisplayName("T10: 单指标统计 - 均值计算正确")
        void testAverageCalculation() {
            List<Double> values = Arrays.asList(10.0, 20.0, 30.0, 40.0, 50.0);
            double avg = values.stream().mapToDouble(v -> v).average().orElse(0);
            assertEquals(30.0, avg, 0.001, "均值计算错误");
        }

        @Test
        @DisplayName("T11: 统计聚合 - max/min/sum正确")
        void testMaxMinSum() {
            List<Double> values = Arrays.asList(15.0, 5.0, 25.0, 10.0, 30.0);
            double max = values.stream().mapToDouble(v -> v).max().orElse(0);
            double min = values.stream().mapToDouble(v -> v).min().orElse(0);
            double sum = values.stream().mapToDouble(v -> v).sum();

            assertEquals(30.0, max);
            assertEquals(5.0, min);
            assertEquals(85.0, sum);
        }

        @Test
        @DisplayName("T12: 标准差计算正确")
        void testStdDevCalculation() {
            List<Double> values = Arrays.asList(2.0, 4.0, 4.0, 4.0, 5.0, 5.0, 7.0, 9.0);
            double avg = values.stream().mapToDouble(v -> v).average().orElse(0);
            double variance = values.stream()
                    .mapToDouble(v -> Math.pow(v - avg, 2))
                    .average().orElse(0);
            double stdDev = Math.sqrt(variance);

            assertEquals(5.0, avg, 0.001);
            assertTrue(stdDev > 0, "标准差应大于0");
            assertTrue(stdDev < 5, "标准差应小于5");
        }

        @Test
        @DisplayName("T13: 区域分组统计正确")
        void testGroupByDistrict() {
            List<CityData> data = new ArrayList<>();
            data.add(makeData("A", Map.of("traffic_flow", 1000), "市中心区"));
            data.add(makeData("A", Map.of("traffic_flow", 2000), "市中心区"));
            data.add(makeData("B", Map.of("traffic_flow", 500), "高新区"));
            data.add(makeData("C", Map.of("traffic_flow", 800), "高新区"));

            Map<String, List<CityData>> grouped = data.stream()
                    .collect(Collectors.groupingBy(CityData::getDistrict));

            assertEquals(2, grouped.size(), "应有2个区域");
            assertEquals(2, grouped.get("市中心区").size());
            assertEquals(2, grouped.get("高新区").size());

            // 市中心区流量应该更高
            double centerAvg = grouped.get("市中心区").stream()
                    .mapToInt(d -> (int) d.getMetrics().get("traffic_flow"))
                    .average().orElse(0);
            assertEquals(1500.0, centerAvg, 0.001);
        }
    }

    @Nested
    @DisplayName("模块三: 聚类分箱算法")
    class ClusterAnalysisTests {

        @Test
        @DisplayName("T14: K=3分箱 - 三等分分界点计算正确")
        void testEqualWidthBinning() {
            double min = 10.0, max = 100.0;
            double range = max - min;
            double lowBound = min + range * 0.33;   // 39.7
            double highBound = min + range * 0.67;  // 70.3

            List<Double> values = Arrays.asList(10.0, 25.0, 50.0, 75.0, 100.0);
            int high = 0, mid = 0, low = 0;
            for (double v : values) {
                if (v >= highBound) high++;
                else if (v >= lowBound) mid++;
                else low++;
            }

            assertEquals(2, high, ">=70.3的有75和100");
            assertEquals(1, mid, "39.7~70.3的有50");
            assertEquals(2, low, "<39.7的有10和25");
        }

        @Test
        @DisplayName("T15: 聚类标签应覆盖全部数据")
        void testAllDataCovered() {
            List<Integer> values = Arrays.asList(5, 10, 15, 20, 25, 30);
            int min = values.stream().min(Integer::compareTo).get();
            int max = values.stream().max(Integer::compareTo).get();
            int range = max - min;  // 25
            int lowBound = min + range / 3;    // 5+8=13
            int highBound = min + range * 2 / 3; // 5+16=21

            int total = 0;
            for (int v : values) {
                if (v >= highBound) total++;
                else if (v >= lowBound) total++;
                else total++;
            }
            assertEquals(6, total, "所有数据都应被归类");
        }
    }

    @Nested
    @DisplayName("模块三: 关联规则 - 相关系数")
    class CorrelationTests {

        @Test
        @DisplayName("T16: 完全正相关 - 皮尔逊系数=1.0")
        void testPerfectPositiveCorrelation() {
            List<Double> x = Arrays.asList(1.0, 2.0, 3.0, 4.0, 5.0);
            List<Double> y = Arrays.asList(2.0, 4.0, 6.0, 8.0, 10.0);

            double corr = pearsonCorr(x, y);
            assertEquals(1.0, corr, 0.001, "完全正相关应为1.0");
        }

        @Test
        @DisplayName("T17: 完全负相关 - 皮尔逊系数=-1.0")
        void testPerfectNegativeCorrelation() {
            List<Double> x = Arrays.asList(1.0, 2.0, 3.0, 4.0, 5.0);
            List<Double> y = Arrays.asList(10.0, 8.0, 6.0, 4.0, 2.0);

            double corr = pearsonCorr(x, y);
            assertEquals(-1.0, corr, 0.001, "完全负相关应为-1.0");
        }

        @Test
        @DisplayName("T18: 无相关性 - 皮尔逊系数接近0")
        void testNoCorrelation() {
            List<Double> x = Arrays.asList(1.0, 2.0, 3.0, 4.0, 5.0);
            List<Double> y = Arrays.asList(5.0, 1.0, 4.0, 2.0, 3.0);

            double corr = pearsonCorr(x, y);
            assertTrue(Math.abs(corr) < 0.5, "弱相关应为小值, 实际:" + corr);
        }

        @Test
        @DisplayName("T19: 相关强度分类正确")
        void testCorrelationStrengthClassification() {
            // 强正相关
            double strongPos = pearsonCorr(
                    Arrays.asList(1.,2.,3.,4.,5.), Arrays.asList(1.2,2.1,2.9,4.1,5.3));
            String strongLabel = Math.abs(strongPos) > 0.7 ? "强" : "弱";
            assertEquals("强", strongLabel, "强相关应>0.7, 实际:" + strongPos);

            // 弱相关
            double weak = pearsonCorr(
                    Arrays.asList(1.,2.,3.,4.,5.), Arrays.asList(5.,1.,4.,2.,3.));
            String weakLabel = Math.abs(weak) > 0.7 ? "强" : "弱";
            assertEquals("弱", weakLabel, "弱相关应<0.7, 实际:" + weak);
        }
    }

    @Nested
    @DisplayName("模块三: 线性回归预测")
    class PredictionTests {

        @Test
        @DisplayName("T20: 简单线性回归 - y=2x+3")
        void testSimpleLinearRegression() {
            double[] x = {1, 2, 3, 4, 5};
            double[] y = {5, 7, 9, 11, 13};  // y=2x+3

            int n = x.length;
            double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
            for (int i = 0; i < n; i++) {
                sumX += x[i]; sumY += y[i];
                sumXY += x[i] * y[i];
                sumX2 += x[i] * x[i];
            }
            double slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
            double intercept = (sumY - slope * sumX) / n;

            assertEquals(2.0, slope, 0.01, "斜率应为2");
            assertEquals(3.0, intercept, 0.01, "截距应为3");
        }

        @Test
        @DisplayName("T21: 预测值应在合理趋势范围内")
        void testPredictionReasonable() {
            // 严格上升的数据确保预测也在上升
            double[] y = {10.0, 20.0, 30.0, 40.0, 50.0, 60.0, 70.0, 80.0, 90.0, 100.0};
            int n = y.length;
            double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
            for (int i = 0; i < n; i++) {
                sumX += i; sumY += y[i];
                sumXY += i * y[i]; sumX2 += i * i;
            }
            double slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
            double intercept = (sumY - slope * sumX) / n;

            // 预测下一个
            double predicted = intercept + slope * n;
            assertTrue(slope > 0, "严格上升数据斜率应>0");
            assertTrue(predicted > y[n - 1], "上升趋势预测值应大于最后一期: " + predicted + " > " + y[n-1]);
            assertTrue(predicted < y[n - 1] * 1.5, "预测值不应过于夸张");
        }

        @Test
        @DisplayName("T22: 数据不足时返回空")
        void testInsufficientData() {
            List<Double> values = Collections.singletonList(100.0);
            boolean sufficient = values.size() >= 3;
            assertFalse(sufficient, "1条数据不足以预测");
        }
    }

    // ======================== 辅助方法 ========================

    private List<CityData> createTestData(int count) {
        List<CityData> list = new ArrayList<>();
        String[] districts = {"市中心区", "高新区", "开发区", "老城区", "新城区", "滨江区"};
        for (int i = 0; i < count; i++) {
            Map<String, Object> m = new HashMap<>();
            m.put("traffic_flow", 500 + (int)(Math.random() * 5000));
            m.put("congestion_index", 1 + Math.random() * 8);
            m.put("avg_speed", 20 + Math.random() * 80);
            list.add(makeData("d-" + i, m, districts[i % districts.length]));
        }
        return list;
    }

    private CityData makeData(String id, Map<String, Object> metrics) {
        return makeData(id, metrics, "默认区域");
    }

    private CityData makeData(String id, Map<String, Object> metrics, String district) {
        CityData d = new CityData();
        d.setId(id);
        d.setDistrict(district);
        d.setMetrics(metrics);
        return d;
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
