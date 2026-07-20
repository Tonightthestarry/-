package com.massdata.service;

import com.massdata.dao.mongo.*;
import com.massdata.entity.*;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.Month;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AI便民建议服务 - 模块六增强
 * 扫描当日5个MongoDB集合的真实数据，发现异常/事件后自动生成普通人能看懂的便民建议
 * 不依赖大模型API，纯规则引擎 + 数据模板
 */
@Service
public class AiSuggestionService {

    private final TrafficDataRepository trafficRepo;
    private final WeatherDataRepository weatherRepo;
    private final OpinionDataRepository opinionRepo;
    private final ConsumptionDataRepository consumptionRepo;
    private final PopulationDataRepository populationRepo;
    private final AnalysisResultRepository resultRepo;

    public AiSuggestionService(TrafficDataRepository trafficRepo,
                                WeatherDataRepository weatherRepo,
                                OpinionDataRepository opinionRepo,
                                ConsumptionDataRepository consumptionRepo,
                                PopulationDataRepository populationRepo,
                                AnalysisResultRepository resultRepo) {
        this.trafficRepo = trafficRepo;
        this.weatherRepo = weatherRepo;
        this.opinionRepo = opinionRepo;
        this.consumptionRepo = consumptionRepo;
        this.populationRepo = populationRepo;
        this.resultRepo = resultRepo;
    }

    private String today() {
        return new SimpleDateFormat("yyyy-MM-dd").format(new Date());
    }

    /**
     * 主入口: 扫描全天数据, 生成便民建议
     * @param dateStr 分析日期(为空则自动找最近有数据的日期)
     * @return {suggestions, predictions, summary, basedOn}
     */
    public Map<String, Object> generate(String dateStr) {
        Map<String, Object> result = new LinkedHashMap<>();

        // 1. 确定查询日期
        String d = (dateStr == null || dateStr.isEmpty()) ? latestDateWithData() : dateStr;
        result.put("basedOn", d);

        // 2. 拉取当日所有数据
        List<TrafficData> traffic = trafficRepo.findByDateStr(d);
        List<WeatherData> weather = weatherRepo.findByDateStr(d);
        List<OpinionData> opinion = opinionRepo.findByDateStr(d);
        List<ConsumptionData> consumption = consumptionRepo.findByDateStr(d);
        List<PopulationData> population = populationRepo.findByDateStr(d);

        int totalRecords = traffic.size() + weather.size() + opinion.size()
                + consumption.size() + population.size();
        result.put("totalRecords", totalRecords);

        // 3. 数据不足时直接返回
        if (totalRecords == 0) {
            result.put("suggestions", Collections.singletonList(
                    "暂无" + d + "的数据，请先执行「数据采集」或切换分析日期。"));
            result.put("predictions", Collections.emptyMap());
            result.put("summary", "无数据");
            return result;
        }

        // 4. 扫描各类数据, 生成建议列表
        List<String> suggestions = new ArrayList<>();

        // 先扫节日
        String holiday = checkHoliday(LocalDate.now());
        if (holiday != null) {
            suggestions.add(genHolidaySuggestion(holiday, traffic, population, consumption));
        }

        // 扫气象
        scanWeather(weather, suggestions);

        // 扫交通(拥堵 + 事故)
        scanTraffic(traffic, suggestions);

        // 扫舆情
        scanOpinion(opinion, suggestions);

        // 扫消费
        scanConsumption(consumption, suggestions);

        // 扫人口
        scanPopulation(population, suggestions);

        // 生活化预测(给普通人的"明天带伞"建议)
        suggestions.add(genLifeAdviceBody(traffic, weather, opinion, consumption, population));

        // 一切正常
        if (suggestions.isEmpty()) {
            suggestions.add(genAllNormal(traffic, weather, opinion, consumption, population, d));
        }

        result.put("suggestions", suggestions);

        // 5. 简易预测(温度、拥堵趋势)
        Map<String, Object> predictions = new LinkedHashMap<>();
        if (!weather.isEmpty()) {
            double avgTemp = weather.stream().mapToDouble(WeatherData::getTemperature)
                    .average().orElse(0);
            // 根据最近20条趋势外推
            List<Double> temps = weather.stream()
                    .map(WeatherData::getTemperature)
                    .sorted(Comparator.reverseOrder())
                    .limit(20)
                    .collect(Collectors.toList());
            double trend = temps.size() > 1
                    ? (temps.get(0) - temps.get(temps.size() - 1)) / temps.size()
                    : 0;
            predictions.put("temperature_current", Math.round(avgTemp * 10.0) / 10.0);
            predictions.put("temperature_next", Math.round((avgTemp + trend) * 10.0) / 10.0);
        }
        if (!traffic.isEmpty()) {
            double avgCI = traffic.stream().mapToDouble(TrafficData::getCongestionIndex)
                    .average().orElse(0);
            double avgFlow = traffic.stream().mapToInt(TrafficData::getTrafficFlow)
                    .average().orElse(0);
            predictions.put("congestion_avg", Math.round(avgCI * 1000.0) / 1000.0);
            predictions.put("traffic_flow_avg", Math.round(avgFlow));
        }
        predictions.put("method", "移动平均 + 近期趋势外推");
        predictions.put("horizon", "下一采集周期");
        result.put("predictions", predictions);

        // 6. 总结
        result.put("summary", buildSummary(suggestions.size(), holiday));

        return result;
    }

    // ==================== 节日判断 ====================
    private String checkHoliday(LocalDate d) {
        int m = d.getMonthValue();
        int day = d.getDayOfMonth();
        // 元旦
        if (m == 1 && day == 1) return "元旦";
        // 春节(大致: 1月底-2月初, 用固定日期模拟)
        if ((m == 1 && day >= 22) || (m == 2 && day <= 15)) return "春节期间";
        // 清明(4月4-6)
        if (m == 4 && day >= 4 && day <= 6) return "清明节";
        // 五一
        if (m == 5 && day <= 5) return "劳动节假期";
        // 端午(农历五月初五, 大致5月底6月初)
        if ((m == 5 && day >= 28) || (m == 6 && day <= 10)) return "端午节假期";
        // 中秋(农历八月十五, 大致9月中)
        if (m == 9 && day >= 10 && day <= 20) return "中秋节假期";
        // 国庆
        if (m == 10 && day <= 7) return "国庆假期";
        // 高考日
        if (m == 6 && day >= 7 && day <= 9) return "高考日";
        // 周末
        if (d.getDayOfWeek().getValue() >= 6) return "周末";
        return null;
    }

    private String genHolidaySuggestion(String holiday, List<TrafficData> traffic,
                                         List<PopulationData> population,
                                         List<ConsumptionData> consumption) {
        StringBuilder sb = new StringBuilder();
        sb.append("【").append(holiday).append("出行提醒】今日为").append(holiday).append("\n");

        // 交通拥堵段
        List<TrafficData> congested = traffic.stream()
                .filter(t -> t.getCongestionIndex() > 0.5)
                .sorted((a, b) -> Double.compare(b.getCongestionIndex(), a.getCongestionIndex()))
                .limit(3)
                .collect(Collectors.toList());
        if (!congested.isEmpty()) {
            sb.append("  · 以下路段拥堵较重：");
            for (TrafficData t : congested) {
                sb.append(t.getDistrict()).append(t.getRoadName())
                        .append("(拥堵").append(String.format("%.2f", t.getCongestionIndex()))
                        .append(")、");
            }
            sb.deleteCharAt(sb.length() - 1);
            sb.append("\n  → 建议：以上路段增派交警疏导，引导市民乘坐公共交通\n");
        }

        // 商圈消费
        if (!consumption.isEmpty()) {
            double total = consumption.stream().mapToDouble(ConsumptionData::getTotalAmount).sum() / 10000.0;
            sb.append("  · 今日消费总额约").append(String.format("%.0f", total)).append("万元");
            if (total > 100) {
                sb.append("，较平日偏高");
            }
            sb.append("\n  → 建议：重点商圈增派安保和清洁人员\n");
        }

        // 人口
        if (!population.isEmpty()) {
            long totalPop = population.stream().mapToLong(PopulationData::getMobilePopulation).sum();
            sb.append("  · 流动人口约").append(String.format("%.0f", totalPop / 10000.0))
                    .append("万人\n");
        }

        sb.append("  → 建议：地铁公交加密班次，增派").append(
                Math.max(10, congested.size() * 3)).append("名交通疏导员");
        return sb.toString();
    }

    // ==================== 气象扫描 ====================
    private void scanWeather(List<WeatherData> weather, List<String> suggestions) {
        if (weather.isEmpty()) return;

        double avgTemp = weather.stream().mapToDouble(WeatherData::getTemperature).average().orElse(0);
        double maxTemp = weather.stream().mapToDouble(WeatherData::getTemperature).max().orElse(0);
        double avgHumidity = weather.stream().mapToInt(WeatherData::getHumidity).average().orElse(0);
        double totalPrecip = weather.stream().mapToDouble(WeatherData::getPrecipitation).sum();

        // 降雨
        List<WeatherData> rainy = weather.stream()
                .filter(w -> w.getWeather() != null && w.getWeather().contains("雨"))
                .collect(Collectors.toList());
        if (!rainy.isEmpty()) {
            // 按区域分组
            Map<String, Double> byDistrict = rainy.stream()
                    .collect(Collectors.groupingBy(WeatherData::getDistrict,
                            Collectors.averagingDouble(WeatherData::getPrecipitation)));
            StringBuilder sb = new StringBuilder();
            sb.append("【降雨提醒】今日以下区域有降雨：\n");
            byDistrict.entrySet().stream()
                    .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                    .limit(3)
                    .forEach(e -> sb.append("  · ").append(e.getKey())
                            .append("  降水量 ").append(String.format("%.1f", e.getValue())).append("mm\n"));
            // 找对应区域的事故路段
            Set<String> rainDistricts = byDistrict.keySet();
            List<TrafficData> rainyAreaTraffic = trafficRepo.findByDateStr(weather.get(0).getDateStr())
                    .stream()
                    .filter(t -> rainDistricts.contains(t.getDistrict()) && t.getRoadRank() != null
                            && (t.getRoadRank().contains("次干") || t.getRoadRank().contains("支路")))
                    .sorted((a, b) -> Double.compare(b.getCongestionIndex(), a.getCongestionIndex()))
                    .limit(3)
                    .collect(Collectors.toList());
            if (!rainyAreaTraffic.isEmpty()) {
                sb.append("  · 以下路段为历史易积水路段，需重点关注：");
                for (TrafficData t : rainyAreaTraffic) {
                    sb.append(t.getDistrict()).append(t.getRoadName()).append("、");
                }
                sb.deleteCharAt(sb.length() - 1);
                sb.append("\n");
            }
            sb.append("  → 建议：提前疏通下水道，在低洼路段设置警示标志和排水设备");
            suggestions.add(sb.toString());
        }

        // 高温
        if (maxTemp > 35) {
            StringBuilder sb = new StringBuilder();
            sb.append("【高温提醒】当前最高气温 ").append(String.format("%.1f", maxTemp))
                    .append("°C，超过35°C警戒线\n");
            sb.append("  · 全市平均气温 ").append(String.format("%.1f", avgTemp)).append("°C\n");
            if (avgHumidity > 80) {
                sb.append("  · 平均湿度 ").append(String.format("%.0f", avgHumidity))
                        .append("%，闷热感明显\n");
            }
            sb.append("  → 建议：户外作业人员避开11:00-14:00高温时段");
            sb.append("\n  → 建议：主干道洒水车循环喷雾降温");
            suggestions.add(sb.toString());
        }

        // 大风
        double maxWind = weather.stream().mapToDouble(WeatherData::getWindSpeed).max().orElse(0);
        if (maxWind > 10) {
            suggestions.add("【大风提醒】当前最大风速 " + String.format("%.1f", maxWind)
                    + " m/s（约" + windLevel(maxWind) + "），"
                    + "建议户外广告牌加固，建筑工地暂停高空作业。");
        }
    }

    private String windLevel(double speed) {
        if (speed < 5.5) return "3级";
        if (speed < 8.0) return "4级";
        if (speed < 10.8) return "5级";
        if (speed < 13.9) return "6级";
        if (speed < 17.2) return "7级";
        return "8级以上";
    }

    // ==================== 交通扫描 ====================
    private void scanTraffic(List<TrafficData> traffic, List<String> suggestions) {
        if (traffic.isEmpty()) return;

        // 拥堵路段 (congestionIndex > 0.6)
        List<TrafficData> congested = traffic.stream()
                .filter(t -> t.getCongestionIndex() > 0.6)
                .sorted((a, b) -> Double.compare(b.getCongestionIndex(), a.getCongestionIndex()))
                .limit(5)
                .collect(Collectors.toList());
        if (!congested.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("【拥堵疏导】以下路段拥堵指数超过警戒线(0.6)：\n");
            int rank = 1;
            for (TrafficData t : congested) {
                sb.append("  第").append(rank++).append("：")
                        .append(t.getDistrict()).append(t.getRoadName())
                        .append(" | 拥堵 ").append(String.format("%.2f", t.getCongestionIndex()))
                        .append(" | 车流量 ").append(t.getTrafficFlow()).append(" 辆/h");
                if (t.getAccidentCount() > 0) {
                    sb.append(" | ⚠️ 附近 ").append(t.getAccidentCount()).append(" 起事故");
                }
                sb.append("\n");
            }
            sb.append("  → 建议：以上 ").append(congested.size())
                    .append(" 条路段各增派2名交警疏导，启用智能信号灯优先放行");
            suggestions.add(sb.toString());
        }

        // 事故统计
        List<TrafficData> accident = traffic.stream()
                .filter(t -> t.getAccidentCount() > 0)
                .collect(Collectors.toList());
        if (!accident.isEmpty()) {
            Map<String, Long> byDistrict = accident.stream()
                    .collect(Collectors.groupingBy(TrafficData::getDistrict, Collectors.counting()));
            long totalAcc = accident.stream().mapToInt(TrafficData::getAccidentCount).sum();
            StringBuilder sb = new StringBuilder();
            sb.append("【事故通报】今日累计事故 ").append(totalAcc).append(" 起：\n");
            byDistrict.entrySet().stream()
                    .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                    .limit(5)
                    .forEach(e -> sb.append("  · ").append(e.getKey())
                            .append(" ").append(e.getValue()).append(" 起\n"));
            sb.append("  → 建议：事故处理人员尽快到达现场，周边路段引导车辆绕行");
            suggestions.add(sb.toString());
        }
    }

    // ==================== 舆情扫描 ====================
    private void scanOpinion(List<OpinionData> opinion, List<String> suggestions) {
        if (opinion.isEmpty()) return;

        // 负面舆情热点
        List<OpinionData> negative = opinion.stream()
                .filter(o -> "negative".equals(o.getSentiment()))
                .sorted((a, b) -> Long.compare(b.getHeatScore(), a.getHeatScore()))
                .limit(5)
                .collect(Collectors.toList());
        if (!negative.isEmpty()) {
            long totalNeg = opinion.stream().filter(o -> "negative".equals(o.getSentiment())).count();
            double negRatio = totalNeg * 100.0 / opinion.size();
            StringBuilder sb = new StringBuilder();
            sb.append("【舆情热点】今日负面舆情 ").append(totalNeg).append(" 条，占比 ")
                    .append(String.format("%.1f", negRatio)).append("%");

            // 最热的负面话题
            OpinionData hottest = negative.get(0);
            sb.append("\n  · 最热讨论：").append(
                    hottest.getTopicTitle() != null && !hottest.getTopicTitle().isEmpty()
                            ? truncate(hottest.getTopicTitle(), 20)
                            : (hottest.getKeyword() != null ? hottest.getKeyword() : "无标题"));
            sb.append("（热度 ").append(hottest.getHeatScore()).append("）");
            if (hottest.getDistrict() != null) {
                sb.append(" | 区域：").append(hottest.getDistrict());
            }

            if (negRatio > 15) {
                sb.append("\n  ⚠️ 负面情绪占比偏高");
                sb.append("\n  → 建议：相关部门主动回应，发布官方说明，避免舆情发酵");
            } else if (negRatio > 8) {
                sb.append("\n  → 建议：关注舆情走势，做好应对预案");
            } else {
                sb.append("\n  → 舆情总体可控，保持监测即可");
            }
            suggestions.add(sb.toString());
        }
    }

    // ==================== 消费扫描 ====================
    private void scanConsumption(List<ConsumptionData> consumption, List<String> suggestions) {
        if (consumption.isEmpty()) return;

        // 按区域汇总消费
        Map<String, DoubleSummaryStatistics> byDistrict = consumption.stream()
                .collect(Collectors.groupingBy(ConsumptionData::getDistrict,
                        Collectors.summarizingDouble(ConsumptionData::getTotalAmount)));
        // Top 3 消费区
        List<Map.Entry<String, DoubleSummaryStatistics>> top3 = byDistrict.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue().getSum(), a.getValue().getSum()))
                .limit(3)
                .collect(Collectors.toList());
        double overallTotal = consumption.stream().mapToDouble(ConsumptionData::getTotalAmount).sum();
        if (overallTotal > 500000) {  // 日消费总额超 50 万
            StringBuilder sb = new StringBuilder();
            sb.append("【消费高峰】今日消费总额 ")
                    .append(String.format("%.0f", overallTotal / 10000.0)).append(" 万元，属于消费高峰\n");
            sb.append("  · 消费前三区域：");
            for (Map.Entry<String, DoubleSummaryStatistics> e : top3) {
                sb.append(e.getKey()).append("(")
                        .append(String.format("%.0f", e.getValue().getSum() / 10000.0)).append("万)、");
            }
            sb.deleteCharAt(sb.length() - 1);
            sb.append("\n  → 建议：重点商圈增派安保人员和清洁人员，加强巡逻");
            suggestions.add(sb.toString());
        }
    }

    // ==================== 人口扫描 ====================
    private void scanPopulation(List<PopulationData> population, List<String> suggestions) {
        if (population.isEmpty()) return;

        // 找人口密度最高的区域
        PopulationData maxDensity = population.stream()
                .max(Comparator.comparingInt(PopulationData::getPopulationDensity))
                .orElse(null);
        if (maxDensity != null && maxDensity.getPopulationDensity() > 30000) {
            long totalMobile = population.stream()
                    .mapToLong(PopulationData::getMobilePopulation).sum();
            StringBuilder sb = new StringBuilder();
            sb.append("【人口密集提醒】今日流动人口约 ")
                    .append(String.format("%.0f", totalMobile / 10000.0)).append(" 万人\n");
            sb.append("  · 人口最密集区域：").append(maxDensity.getDistrict())
                    .append("（密度 ").append(maxDensity.getPopulationDensity()).append(" 人/km²）\n");
            if (maxDensity.getPopulationDensity() > 40000) {
                sb.append("  → 建议：该区域控制大型活动审批，加强人流疏导");
            } else {
                sb.append("  → 建议：关注该区域人流变化，做好应急预案");
            }
            suggestions.add(sb.toString());
        }
    }

    // ==================== 一切正常 ====================
    private String genAllNormal(List<TrafficData> traffic, List<WeatherData> weather,
                                 List<OpinionData> opinion, List<ConsumptionData> consumption,
                                 List<PopulationData> population, String d) {
        double avgTemp = weather.stream().mapToDouble(WeatherData::getTemperature).average().orElse(0);
        long totalOpinion = opinion.size();
        long posCount = opinion.stream().filter(o -> "positive".equals(o.getSentiment())).count();
        double posRatio = totalOpinion > 0 ? posCount * 100.0 / totalOpinion : 0;

        return "【今日城市态势正常】\n"
                + "\n✅ 交通：各区域拥堵指数均在正常范围（< 0.6）\n"
                + "✅ 气象：当前气温 " + String.format("%.1f", avgTemp) + "°C，天气晴好，适宜出行\n"
                + "✅ 舆情：共 " + totalOpinion + " 条，正面情绪占 "
                + String.format("%.0f", posRatio) + "%，市民情绪总体正面\n"
                + "✅ 消费：各商圈运行平稳\n"
                + "✅ 人口：各区域人口密度正常\n"
                + "\n→ 今日城市运行平稳，各项指标均在正常范围内，无需特别关注。";
    }

    // ==================== 工具方法 ====================
    private String latestDateWithData() {
        List<TrafficData> sample = trafficRepo.findAll();
        if (sample.isEmpty()) return today();
        return sample.stream()
                .map(TrafficData::getDateStr)
                .filter(s -> s != null && !s.isEmpty())
                .max(String::compareTo)
                .orElse(today());
    }

    private String buildSummary(int count, String holiday) {
        if (count == 0) return "今日城市运行平稳";
        if (holiday != null) return "今日为" + holiday + "，" + count + " 条建议请查收";
        return "共发现 " + count + " 个需关注的事项";
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    // ==================== 紧急事件(按区域汇总, 按紧急度排序) ====================
    // 用于大屏滚动公告栏: 每条带紧急级别
    public Map<String, Object> getUrgentAlerts(String dateStr) {
        String d = (dateStr == null || dateStr.isEmpty()) ? latestDateWithData() : dateStr;
        List<TrafficData> traffic = trafficRepo.findByDateStr(d);
        List<WeatherData> weather = weatherRepo.findByDateStr(d);
        List<OpinionData> opinion = opinionRepo.findByDateStr(d);

        List<Map<String, Object>> alerts = new ArrayList<>();

        // 1. 严重拥堵路段(拥堵指数>0.7, 最高级别)
        traffic.stream()
                .filter(t -> t.getCongestionIndex() > 0.7)
                .sorted((a, b) -> Double.compare(b.getCongestionIndex(), a.getCongestionIndex()))
                .limit(3)
                .forEach(t -> alerts.add(createAlert("URGENT", "🚨",
                        t.getDistrict() + t.getRoadName() + "严重拥堵",
                        String.format("拥堵指数 %.2f / 车流量 %d 辆/h / 附近 %d 起事故",
                                t.getCongestionIndex(), t.getTrafficFlow(), t.getAccidentCount()),
                        t.getDistrict(), t.getDateStr())));

        // 2. 高温预警(>35)
        weather.stream()
                .filter(w -> w.getTemperature() > 35)
                .collect(Collectors.groupingBy(WeatherData::getDistrict,
                        Collectors.averagingDouble(WeatherData::getTemperature)))
                .entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(3)
                .forEach(e -> alerts.add(createAlert("WARNING", "🔥",
                        e.getKey() + "高温预警",
                        String.format("平均气温 %.1f°C，建议避免 11:00-14:00 户外活动",
                                e.getValue()),
                        e.getKey(), d)));

        // 3. 暴雨区域(降水量>10mm)
        weather.stream()
                .filter(w -> w.getPrecipitation() > 10)
                .collect(Collectors.groupingBy(WeatherData::getDistrict,
                        Collectors.averagingDouble(WeatherData::getPrecipitation)))
                .entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(3)
                .forEach(e -> alerts.add(createAlert("WARNING", "🌧️",
                        e.getKey() + "强降雨",
                        String.format("降水量 %.1fmm，请携带雨具，注意路面积水",
                                e.getValue()),
                        e.getKey(), d)));

        // 4. 事故区域(总事故数>=2)
        Map<String, Integer> accidentsByDistrict = traffic.stream()
                .filter(t -> t.getAccidentCount() > 0)
                .collect(Collectors.groupingBy(TrafficData::getDistrict,
                        Collectors.summingInt(TrafficData::getAccidentCount)));
        accidentsByDistrict.entrySet().stream()
                .filter(e -> e.getValue() >= 2)
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(3)
                .forEach(e -> alerts.add(createAlert("URGENT", "⚠️",
                        e.getKey() + "交通事故多发",
                        "今日累计 " + e.getValue() + " 起事故，建议绕行",
                        e.getKey(), d)));

        // 5. 负面舆情
        Map<String, Long> negOpinionByDistrict = opinion.stream()
                .filter(o -> "negative".equals(o.getSentiment()))
                .collect(Collectors.groupingBy(OpinionData::getDistrict, Collectors.counting()));
        negOpinionByDistrict.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(3)
                .forEach(e -> alerts.add(createAlert("INFO", "💬",
                        e.getKey() + "舆情关注",
                        "负面讨论 " + e.getValue() + " 条，请关注民意",
                        e.getKey(), d)));

        // 按紧急级别排序: URGENT > WARNING > INFO
        Map<String, Integer> priority = new HashMap<>();
        priority.put("URGENT", 3);
        priority.put("WARNING", 2);
        priority.put("INFO", 1);
        alerts.sort((a, b) -> Integer.compare(
                priority.get(b.get("level")), priority.get(a.get("level"))));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("basedOn", d);
        result.put("total", alerts.size());
        result.put("alerts", alerts);
        return result;
    }

    private Map<String, Object> createAlert(String level, String icon, String title,
                                             String content, String district, String date) {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("level", level);
        a.put("icon", icon);
        a.put("title", title);
        a.put("content", content);
        a.put("district", district);
        a.put("date", date);
        return a;
    }

    // ==================== 生活化预测建议 ====================
    // 给5类数据每类都加一条"对人有用"的预测
    public String genLifeAdvice(String dateStr) {
        String d = (dateStr == null || dateStr.isEmpty()) ? latestDateWithData() : dateStr;
        List<TrafficData> traffic = trafficRepo.findByDateStr(d);
        List<WeatherData> weather = weatherRepo.findByDateStr(d);
        List<OpinionData> opinion = opinionRepo.findByDateStr(d);
        List<ConsumptionData> consumption = consumptionRepo.findByDateStr(d);
        List<PopulationData> population = populationRepo.findByDateStr(d);
        return genLifeAdviceBody(traffic, weather, opinion, consumption, population);
    }

    // 主 generate() 直接调这个（已有 List）
    private String genLifeAdviceBody(List<TrafficData> traffic, List<WeatherData> weather,
                                       List<OpinionData> opinion, List<ConsumptionData> consumption,
                                       List<PopulationData> population) {
        StringBuilder sb = new StringBuilder();
        sb.append("【生活贴士】基于今日数据给您的建议：\n\n");

        // 1. 出行贴士(基于交通)
        if (!traffic.isEmpty()) {
            double avgCI = traffic.stream().mapToDouble(TrafficData::getCongestionIndex)
                    .average().orElse(0);
            int maxFlow = traffic.stream().mapToInt(TrafficData::getTrafficFlow).max().orElse(0);
            if (avgCI > 0.6) {
                sb.append("🚗 【出行】今日全市平均拥堵指数 ").append(String.format("%.2f", avgCI))
                        .append("，建议：\n");
                sb.append("   · 通勤请预留 1.5 倍平时时间\n");
                sb.append("   · 高峰时段(17:00-19:00)尽量地铁出行\n");
                if (maxFlow > 3000) {
                    List<TrafficData> top = traffic.stream()
                            .sorted((a, b) -> Double.compare(b.getCongestionIndex(), a.getCongestionIndex()))
                            .limit(2)
                            .collect(Collectors.toList());
                    sb.append("   · 避开 ").append(top.get(0).getDistrict())
                            .append(top.get(0).getRoadName());
                    if (top.size() > 1) sb.append("、").append(top.get(1).getDistrict())
                            .append(top.get(1).getRoadName());
                    sb.append("\n");
                }
                sb.append("\n");
            } else {
                sb.append("🚗 【出行】今日交通顺畅，适合外出\n\n");
            }
        }

        // 2. 气象贴士(基于天气)
        if (!weather.isEmpty()) {
            double avgTemp = weather.stream().mapToDouble(WeatherData::getTemperature).average().orElse(0);
            double maxTemp = weather.stream().mapToDouble(WeatherData::getTemperature).max().orElse(0);
            double avgHumidity = weather.stream().mapToInt(WeatherData::getHumidity).average().orElse(0);
            double totalPrecip = weather.stream().mapToDouble(WeatherData::getPrecipitation).sum();
            List<WeatherData> rainy = weather.stream()
                    .filter(w -> w.getWeather() != null && w.getWeather().contains("雨"))
                    .collect(Collectors.toList());

            sb.append("☔ 【天气】");
            if (totalPrecip > 5 || !rainy.isEmpty()) {
                sb.append("今日有降雨，");
                // 简单预测: 后续降水趋势
                String futureRain = totalPrecip > 20 ? "明日雨势可能加强" : "明日雨势可能持续或减弱";
                sb.append(futureRain).append("，请提前备好雨具\n");
                sb.append("   · 雨伞指数：★★★★★ (强烈建议带伞)\n");
                sb.append("   · 雨衣雨鞋：低洼地区居民可准备\n");
                if (avgTemp < 15) {
                    sb.append("   · 雨天+低温，注意添衣防寒\n");
                }
            } else if (maxTemp > 35) {
                sb.append("今日高温 ").append(String.format("%.1f", maxTemp))
                        .append("°C，请：\n");
                sb.append("   · 防晒指数：★★★★★ (务必防晒)\n");
                sb.append("   · 饮水：建议每天 2L 温水\n");
                sb.append("   · 老人小孩尽量待在空调房\n");
                sb.append("   · 明日预测：高温将持续，注意防暑\n");
            } else if (maxTemp < 5) {
                sb.append("今日气温偏低 ").append(String.format("%.1f", maxTemp))
                        .append("°C，请：\n");
                sb.append("   · 保暖指数：★★★★★ (羽绒服+围巾)\n");
                sb.append("   · 心血管疾病患者注意防护\n");
            } else {
                sb.append("天气 ").append(String.format("%.1f", avgTemp))
                        .append("°C 适宜出行\n");
                sb.append("   · 穿衣：").append(dressingAdvice(avgTemp)).append("\n");
                sb.append("   · 紫外线：").append(uvAdvice(weather)).append("\n");
                sb.append("   · 空气：").append(aqiAdvice(weather)).append("\n");
            }
            sb.append("\n");
        }

        // 3. 消费贴士(基于消费数据)
        if (!consumption.isEmpty()) {
            double total = consumption.stream().mapToDouble(ConsumptionData::getTotalAmount).sum();
            Map<String, Double> byDistrict = consumption.stream()
                    .collect(Collectors.groupingBy(ConsumptionData::getDistrict,
                            Collectors.summingDouble(ConsumptionData::getTotalAmount)));
            String hotDistrict = byDistrict.entrySet().stream()
                    .max(Comparator.comparingDouble(Map.Entry::getValue))
                    .map(Map.Entry::getKey).orElse("市中心");
            sb.append("💰 【消费】今日 ").append(hotDistrict)
                    .append(" 消费最旺");
            if (total > 1000000) {
                sb.append("(总额 ").append(String.format("%.0f", total / 10000.0))
                        .append("万元)");
            }
            sb.append("：\n");
            sb.append("   · 餐饮高峰：建议提前订座或避开 11:30-13:00\n");
            sb.append("   · 热门商圈：").append(hotDistrict).append(" 可能拥堵\n");
            sb.append("   · 优惠：关注商圈会员折扣，比平日多省 10-20%\n\n");
        }

        // 4. 人口贴士
        if (!population.isEmpty()) {
            PopulationData maxDen = population.stream()
                    .max(Comparator.comparingInt(PopulationData::getPopulationDensity))
                    .orElse(null);
            if (maxDen != null) {
                sb.append("👥 【人流】").append(maxDen.getDistrict())
                        .append(" 当前密度 ").append(maxDen.getPopulationDensity())
                        .append(" 人/km²\n");
                if (maxDen.getPopulationDensity() > 30000) {
                    sb.append("   · 拥挤指数：★★★★☆ (人流密集)\n");
                    sb.append("   · 建议：错峰出行，避免 18:00-20:00 晚高峰\n");
                    sb.append("   · 老人小孩：避开拥挤区域\n");
                } else {
                    sb.append("   · 拥挤指数：★★☆☆☆ (人流适中)\n");
                }
                sb.append("\n");
            }
        }

        // 5. 舆情贴士
        if (!opinion.isEmpty()) {
            long total = opinion.size();
            long pos = opinion.stream().filter(o -> "positive".equals(o.getSentiment())).count();
            long neg = opinion.stream().filter(o -> "negative".equals(o.getSentiment())).count();
            double negRatio = total > 0 ? neg * 100.0 / total : 0;
            sb.append("💬 【舆情】今日全网讨论 ").append(total).append(" 条\n");
            sb.append("   · 正面情绪 ").append(String.format("%.0f", pos * 100.0 / total)).append("%\n");
            if (negRatio > 15) {
                sb.append("   · ⚠️ 负面情绪偏高(>15%)，请勿轻信网络传言\n");
                sb.append("   · 以官方通报为准\n");
            } else {
                sb.append("   · 网络氛围正常，可正常浏览\n");
            }
        }

        return sb.toString();
    }

    private String dressingAdvice(double temp) {
        if (temp < 10) return "厚外套+毛衣";
        if (temp < 18) return "夹克+长袖";
        if (temp < 25) return "薄外套+衬衫";
        if (temp < 30) return "短袖+长裤";
        return "短袖+短裤(注意防晒)";
    }

    private String uvAdvice(List<WeatherData> weather) {
        // WeatherData 无 uvIndex 字段, 用晴雨/气温做粗略代理(正午/晴天=较强, 阴雨=较弱)
        double maxUv = weather.stream()
                .mapToDouble(w -> {
                    String s = w.getWeather();
                    if (s != null && (s.contains("晴") || s.contains("sun"))) {
                        return w.getTemperature() > 25 ? 7.0 : 5.0;
                    }
                    return 3.0;
                })
                .max().orElse(0);
        if (maxUv > 8) return "★★★★★ 极强(避免暴晒)";
        if (maxUv > 6) return "★★★★☆ 强(需防晒)";
        if (maxUv > 3) return "★★★☆☆ 中等";
        return "★★☆☆☆ 弱";
    }

    private String aqiAdvice(List<WeatherData> weather) {
        double avgAqi = weather.stream().mapToInt(WeatherData::getAqi).average().orElse(0);
        if (avgAqi > 200) return "★★★★★ 重度污染(戴口罩)";
        if (avgAqi > 150) return "★★★★☆ 中度污染";
        if (avgAqi > 100) return "★★★☆☆ 轻度污染(敏感人群注意)";
        if (avgAqi > 50) return "★★☆☆☆ 良";
        return "★☆☆☆☆ 优";
    }
}
