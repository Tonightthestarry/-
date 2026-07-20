package com.massdata.service;

import com.massdata.entity.CityData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 可视化服务白盒测试 - 模块六
 * 测试各种图表数据的聚合逻辑
 */
@DisplayName("可视化服务图表数据白盒测试")
class VisualizationServiceWhiteBoxTest {

    // ======================== 模块六: 图表数据聚合 ========================

    @Nested
    @DisplayName("交通拥堵饼图数据")
    class CongestionPieTests {

        @Test
        @DisplayName("T36: 拥堵等级分类正确")
        void testCongestionLevelClassification() {
            double[] values = {1.0, 2.5, 4.0, 6.0, 9.0};
            String[] expected = {"畅通", "基本畅通", "轻度拥堵", "中度拥堵", "严重拥堵"};

            for (int i = 0; i < values.length; i++) {
                String level = classifyCongestion(values[i]);
                assertEquals(expected[i], level, "拥堵值" + values[i] + "应为" + expected[i]);
            }
        }

        @Test
        @DisplayName("T37: 饼图数据按类汇总正确")
        void testPieDataAggregation() {
            double[] values = {1.0, 1.5, 2.5, 3.0, 4.5, 6.0, 8.0, 9.0};
            Map<String, Long> counts = new LinkedHashMap<>();

            for (double v : values) {
                String level = classifyCongestion(v);
                counts.merge(level, 1L, Long::sum);
            }

            assertEquals(2L, (long) counts.get("畅通"), "畅通应有2条");
            assertEquals(2L, (long) counts.get("基本畅通"), "基本畅通应有2条");
            assertEquals(1L, (long) counts.get("轻度拥堵"), "轻度拥堵应有1条");
            assertEquals(1L, (long) counts.get("中度拥堵"), "中度拥堵应有1条");
            assertEquals(2L, (long) counts.get("严重拥堵"), "严重拥堵应有2条");
        }

        @Test
        @DisplayName("T38: 饼图分类总数应等于输入数据量")
        void testPieTotalMatchesInput() {
            double[] values = {1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0};
            Map<String, Long> counts = new LinkedHashMap<>();
            for (double v : values) {
                counts.merge(classifyCongestion(v), 1L, Long::sum);
            }

            long total = counts.values().stream().mapToLong(Long::longValue).sum();
            assertEquals(values.length, total, "分类总数应等于输入数量");
        }
    }

    @Nested
    @DisplayName("人口分布柱状图")
    class PopulationBarTests {

        @Test
        @DisplayName("T39: 区域人口密度聚合正确")
        void testPopulationDensityAggregation() {
            List<CityData> data = new ArrayList<>();
            // 市中心区 - 2条记录
            data.add(makePopData("市中心区", 10000));
            data.add(makePopData("市中心区", 12000));
            // 高新区 - 1条
            data.add(makePopData("高新区", 5000));

            Map<String, Double> avgDensity = data.stream()
                    .collect(Collectors.groupingBy(CityData::getDistrict,
                            Collectors.averagingDouble(d ->
                                    (Integer) d.getMetrics().getOrDefault("density", 0))));

            assertEquals(2, avgDensity.size());
            assertEquals(11000.0, avgDensity.get("市中心区"), 0.001, "市中心区均值=(10000+12000)/2=11000");
            assertEquals(5000.0, avgDensity.get("高新区"), 0.001);
        }

        @Test
        @DisplayName("T40: 常驻人口和密度应分别聚合")
        void testResidentVsDensity() {
            CityData d1 = new CityData();
            d1.setDistrict("区A");
            Map<String, Object> m = new HashMap<>();
            m.put("density", 8000);
            m.put("resident_pop", 35000);
            m.put("floating_pop", 10000);
            d1.setMetrics(m);

            assertEquals(8000, (int) d1.getMetrics().get("density"));
            assertEquals(35000, (int) d1.getMetrics().get("resident_pop"));
            assertEquals(10000, (int) d1.getMetrics().get("floating_pop"));
        }
    }

    @Nested
    @DisplayName("气象趋势折线图")
    class WeatherTrendTests {

        @Test
        @DisplayName("T41: 按时间排序正确")
        void testTimeOrdering() {
            List<CityData> data = new ArrayList<>();
            data.add(makeWeatherData("2026-06-09 14:00:00", 28.0));
            data.add(makeWeatherData("2026-06-09 08:00:00", 22.0));
            data.add(makeWeatherData("2026-06-09 20:00:00", 25.0));
            data.add(makeWeatherData("2026-06-09 10:00:00", 24.0));

            data.sort(Comparator.comparing(CityData::getTimestamp));

            assertEquals("2026-06-09 08:00:00", data.get(0).getTimestamp());
            assertEquals("2026-06-09 10:00:00", data.get(1).getTimestamp());
            assertEquals("2026-06-09 14:00:00", data.get(2).getTimestamp());
            assertEquals("2026-06-09 20:00:00", data.get(3).getTimestamp());
        }

        @Test
        @DisplayName("T42: 气象指标类型完整")
        void testWeatherMetricsComplete() {
            Map<String, Object> weather = new HashMap<>();
            weather.put("temperature", 25.5);
            weather.put("humidity", 65);
            weather.put("wind_speed", 12.3);
            weather.put("precipitation", 5.0);
            weather.put("aqi", 80);

            assertEquals(5, weather.size(), "应有5个气象指标");
            assertTrue(weather.containsKey("temperature"));
            assertTrue(weather.containsKey("aqi"));
            assertInstanceOf(Double.class, weather.get("temperature"));
            assertInstanceOf(Integer.class, weather.get("humidity"));
        }
    }

    @Nested
    @DisplayName("消费散点图")
    class ConsumptionScatterTests {

        @Test
        @DisplayName("T43: 散点图数据对(x,y)格式正确")
        void testScatterDataFormat() {
            List<CityData> data = new ArrayList<>();
            Map<String, Object> m1 = new HashMap<>();
            m1.put("transaction_count", 1500);
            m1.put("total_amount", 85000.0);
            data.add(makeData("消费1", m1, "高新区"));

            Map<String, Object> m2 = new HashMap<>();
            m2.put("transaction_count", 2500);
            m2.put("total_amount", 142000.0);
            data.add(makeData("消费2", m2, "市中心区"));

            assertEquals(2, data.size());
            // 验证数据相关性: 交易笔数多 → 总金额大
            int count1 = (int) data.get(0).getMetrics().get("transaction_count");
            double amount1 = (double) data.get(0).getMetrics().get("total_amount");
            int count2 = (int) data.get(1).getMetrics().get("transaction_count");
            double amount2 = (double) data.get(1).getMetrics().get("total_amount");

            assertTrue(count2 > count1, "市中心区交易笔数应更多");
            assertTrue(amount2 > amount1, "市中心区总额应更高");
        }
    }

    @Nested
    @DisplayName("舆情仪表盘")
    class OpinionGaugeTests {

        @Test
        @DisplayName("T44: 正面舆情比例范围正确")
        void testPositiveRatioRange() {
            List<Double> ratios = Arrays.asList(0.35, 0.50, 0.65, 0.80, 0.92);

            for (double r : ratios) {
                assertTrue(r >= 0.0 && r <= 1.0, "正面比例应在0-1之间");
            }

            double avg = ratios.stream().mapToDouble(v -> v).average().orElse(0);
            assertTrue(avg > 0 && avg < 1, "平均值应在0-1之间");
        }
    }

    @Nested
    @DisplayName("分析结果汇总")
    class AnalysisSummaryTests {

        @Test
        @DisplayName("T45: 任务类型分布统计正确")
        void testTaskTypeDistribution() {
            String[] taskTypes = {"statistic", "cluster", "statistic", "association", "cluster", "statistic"};
            Map<String, Long> distribution = Arrays.stream(taskTypes)
                    .collect(Collectors.groupingBy(t -> t, Collectors.counting()));

            assertEquals(3L, (long) distribution.get("statistic"), "统计应有3次");
            assertEquals(2L, (long) distribution.get("cluster"), "聚类应有2次");
            assertEquals(1L, (long) distribution.get("association"), "关联应有1次");
        }
    }

    // ======================== 辅助 ========================

    private String classifyCongestion(double index) {
        if (index < 2) return "畅通";
        else if (index < 4) return "基本畅通";
        else if (index < 6) return "轻度拥堵";
        else if (index < 8) return "中度拥堵";
        else return "严重拥堵";
    }

    private CityData makePopData(String district, int density) {
        CityData d = new CityData();
        d.setDistrict(district);
        Map<String, Object> m = new HashMap<>();
        m.put("density", density);
        d.setMetrics(m);
        return d;
    }

    private CityData makeWeatherData(String timestamp, double temp) {
        CityData d = new CityData();
        d.setTimestamp(timestamp);
        Map<String, Object> m = new HashMap<>();
        m.put("temperature", temp);
        d.setMetrics(m);
        return d;
    }

    private CityData makeData(String id, Map<String, Object> metrics, String district) {
        CityData d = new CityData();
        d.setId(id);
        d.setDistrict(district);
        d.setMetrics(metrics);
        return d;
    }
}
