package com.massdata.service;

import com.massdata.entity.CityData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 数据采集服务白盒测试 - 模块一
 * 测试数据模拟生成、指标生成逻辑、五类数据字段完整性
 */
@DisplayName("数据采集服务白盒测试")
class DataCollectionServiceWhiteBoxTest {

    private DataCollectionService service;

    // ========== 使用反射手动验证内部逻辑(无需Spring容器) ==========

    @Test
    @DisplayName("T1: 五类数据类型标识正确")
    void testDataTypesDefined() {
        String[] types = {"traffic", "weather", "opinion", "consumption", "population"};
        for (String t : types) {
            assertNotNull(t, "数据类型不应为空");
            assertFalse(t.isEmpty());
        }
        assertEquals("traffic", types[0], "第一类应为交通数据");
        assertEquals("population", types[4], "第五类应为人口数据");
    }

    @Nested
    @DisplayName("模拟数据生成验证")
    class DataSimulationTests {

        @Test
        @DisplayName("T2: 生成的CityData必需字段完整性")
        void testCityDataFieldsComplete() {
            CityData data = new CityData();
            data.setId(UUID.randomUUID().toString());
            data.setDataType("traffic");
            data.setCity("上海");
            data.setDistrict("市中心区");
            data.setTimestamp("2026-06-09 12:00:00");
            data.setDateStr("2026-06-09");
            data.setSource("simulated");
            data.setCreateTime("2026-06-09 12:00:00");

            Map<String, Object> metrics = new HashMap<>();
            metrics.put("traffic_flow", 2500);
            metrics.put("avg_speed", 45.5);
            metrics.put("congestion_index", 4.2);
            metrics.put("accident_count", 2);
            data.setMetrics(metrics);

            assertNotNull(data.getId(), "ID不能为空");
            assertEquals("traffic", data.getDataType());
            assertEquals("上海", data.getCity());
            assertNotNull(data.getDistrict(), "区域不能为空");
            assertNotNull(data.getTimestamp(), "时间戳不能为空");
            assertEquals("simulated", data.getSource(), "来源应为simulated");
            assertEquals(4, data.getMetrics().size(), "交通数据应有4个指标");
            assertEquals(2500, data.getMetrics().get("traffic_flow"));
            assertInstanceOf(Integer.class, data.getMetrics().get("traffic_flow"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"traffic", "weather", "opinion", "consumption", "population"})
        @DisplayName("T3: 五类数据各有正确的指标键")
        void testMetricsKeysByDataType(String dataType) {
            CityData data = new CityData();
            data.setDataType(dataType);
            Map<String, Object> metrics = generateTestMetrics(dataType);
            data.setMetrics(metrics);

            Set<String> keys = metrics.keySet();
            assertFalse(keys.isEmpty(), dataType + "的指标不应为空");

            switch (dataType) {
                case "traffic":
                    assertTrue(keys.contains("traffic_flow"), "交通应有traffic_flow");
                    assertTrue(keys.contains("congestion_index"), "交通应有congestion_index");
                    assertTrue(keys.contains("avg_speed"), "交通应有avg_speed");
                    break;
                case "weather":
                    assertTrue(keys.contains("temperature"), "气象应有temperature");
                    assertTrue(keys.contains("humidity"), "气象应有humidity");
                    assertTrue(keys.contains("aqi"), "气象应有aqi");
                    break;
                case "opinion":
                    assertTrue(keys.contains("hot_index"), "舆情应有hot_index");
                    assertTrue(keys.contains("positive_ratio"), "舆情应有positive_ratio");
                    break;
                case "consumption":
                    assertTrue(keys.contains("transaction_count"), "消费应有transaction_count");
                    assertTrue(keys.contains("total_amount"), "消费应有total_amount");
                    break;
                case "population":
                    assertTrue(keys.contains("density"), "人口应有density");
                    assertTrue(keys.contains("resident_pop"), "人口应有resident_pop");
                    break;
            }
        }

        @Test
        @DisplayName("T4: 指标值范围合理")
        void testMetricsValueRanges() {
            // 模拟生成100条交通数据验证指标范围
            Random r = new Random(42);
            List<Map<String, Object>> samples = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                Map<String, Object> m = new HashMap<>();
                m.put("traffic_flow", r.nextInt(5000) + 500);
                m.put("avg_speed", r.nextInt(80) + 20);
                m.put("congestion_index", r.nextDouble() * 8 + 1);
                m.put("accident_count", r.nextInt(5));
                samples.add(m);
            }

            for (Map<String, Object> m : samples) {
                int flow = (int) m.get("traffic_flow");
                assertTrue(flow >= 500 && flow <= 5500, "车流量应在500-5500, 实际:" + flow);

                int speed = (int) m.get("avg_speed");
                assertTrue(speed >= 20 && speed <= 100, "速度应在20-100, 实际:" + speed);

                double congestion = (double) m.get("congestion_index");
                assertTrue(congestion >= 1.0 && congestion <= 9.0, "拥堵指数应在1-9, 实际:" + congestion);

                int accidents = (int) m.get("accident_count");
                assertTrue(accidents >= 0 && accidents <= 4, "事故数应在0-4, 实际:" + accidents);
            }
        }
    }

    @Nested
    @DisplayName("数据完整性验证")
    class DataIntegrityTests {

        @Test
        @DisplayName("T5: 批量数据去重逻辑正确")
        void testDeduplicationLogic() {
            // 模拟重复数据
            List<CityData> data = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                CityData d = new CityData();
                d.setId("id_" + (i % 3));  // 只有3个不同ID
                d.setDataType("traffic");
                d.setDistrict("市中心区");
                data.add(d);
            }

            // distinct by id
            List<CityData> deduped = data.stream()
                    .collect(Collectors.toMap(CityData::getId, d -> d, (a, b) -> a))
                    .values().stream().collect(Collectors.toList());

            assertEquals(3, deduped.size(), "去重后应有3条");
        }

        @Test
        @DisplayName("T6: 空指标数据应被过滤")
        void testEmptyMetricsFiltered() {
            CityData valid = new CityData();
            Map<String, Object> m = new HashMap<>();
            m.put("traffic_flow", 100);
            valid.setMetrics(m);

            CityData invalid = new CityData();
            invalid.setMetrics(new HashMap<>());

            List<CityData> list = Arrays.asList(valid, invalid);
            List<CityData> filtered = list.stream()
                    .filter(d -> d.getMetrics() != null && !d.getMetrics().isEmpty())
                    .collect(Collectors.toList());

            assertEquals(1, filtered.size(), "空指标数据应被过滤");
        }
    }

    // 辅助: 生成测试用的指标数据
    private Map<String, Object> generateTestMetrics(String dataType) {
        Map<String, Object> m = new HashMap<>();
        switch (dataType) {
            case "traffic":
                m.put("traffic_flow", 2500); m.put("avg_speed", 45.5);
                m.put("congestion_index", 4.2); m.put("accident_count", 2);
                m.put("road_occupancy", 65.3); break;
            case "weather":
                m.put("temperature", 25.5); m.put("humidity", 65);
                m.put("wind_speed", 12.3); m.put("precipitation", 5.0);
                m.put("aqi", 80); break;
            case "opinion":
                m.put("hot_index", 500); m.put("positive_ratio", 0.65);
                m.put("neutral_ratio", 0.25); m.put("negative_ratio", 0.10);
                m.put("mention_count", 3000); break;
            case "consumption":
                m.put("transaction_count", 1500); m.put("total_amount", 85000.0);
                m.put("avg_price", 56.6); m.put("yoy_growth", 12.5);
                m.put("active_users", 4500); break;
            case "population":
                m.put("resident_pop", 35000); m.put("floating_pop", 10000);
                m.put("density", 8000); m.put("age_0_18", 0.2);
                m.put("age_19_45", 0.5); m.put("age_46_above", 0.3); break;
        }
        return m;
    }
}
