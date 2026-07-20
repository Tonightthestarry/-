package com.massdata.service;

import com.massdata.dao.mongo.*;
import com.massdata.entity.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据采集服务 - 模块一
 * 支持手动采集、定时离线采集、实时流接入
 *
 * 五类城市数据都写三表:
 *   1) traffic_data/weather_data/opinion_data/consumption_data/population_data
 *   2) city_data                (历史查询/兼容老接口)
 *   3) streaming_results        (数据大屏统一数据源, 按 timestamp 过滤日期)
 *
 * 数据来源:
 *   - 模拟生成(simulated): 按国家统计局/七普/B站 标准格式模拟
 *   - 真实爬虫: B站 API (opinion) + wttr.in (weather)
 */
@Service
public class DataCollectionService {

    // 5 个独立仓库
    private final TrafficDataRepository trafficRepo;
    private final WeatherDataRepository weatherRepo;
    private final OpinionDataRepository opinionRepo;
    private final ConsumptionDataRepository consumptionRepo;
    private final PopulationDataRepository populationRepo;
    private final com.massdata.dao.mongo.CityDataRepository cityDataRepo; // 兼容老接口
    private final org.springframework.data.mongodb.core.MongoTemplate mongoTemplate; // 数据大屏 streaming_results 写入

    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private final Random random = new Random();

    // 内存缓存
    private final Map<String, Object> collectStatusMap = new ConcurrentHashMap<>();
    private final Map<String, Object> collectTimeMap = new ConcurrentHashMap<>();
    private final Map<String, Object> collectCountMap = new ConcurrentHashMap<>();

    // 限速:每次真实爬虫请求后 sleep
    private static final long REAL_API_SLEEP_MS = 1000;

    public DataCollectionService(TrafficDataRepository trafficRepo,
                                  WeatherDataRepository weatherRepo,
                                  OpinionDataRepository opinionRepo,
                                  ConsumptionDataRepository consumptionRepo,
                                  PopulationDataRepository populationRepo,
                                  com.massdata.dao.mongo.CityDataRepository cityDataRepo,
                                  org.springframework.data.mongodb.core.MongoTemplate mongoTemplate) {
        this.trafficRepo = trafficRepo;
        this.weatherRepo = weatherRepo;
        this.opinionRepo = opinionRepo;
        this.consumptionRepo = consumptionRepo;
        this.populationRepo = populationRepo;
        this.cityDataRepo = cityDataRepo;
        this.mongoTemplate = mongoTemplate;
    }

    private void addCollectCount(String dataType, int add) {
        collectCountMap.merge(dataType, String.valueOf(add), (oldV, newV) -> {
            try { return String.valueOf(Integer.parseInt(String.valueOf(oldV)) + Integer.parseInt(String.valueOf(newV))); }
            catch (Exception e) { return String.valueOf(add); }
        });
    }

    // ========== 主入口 ==========

    public Map<String, Object> collect(String dataType, String city, int count) {
        if (count <= 0) count = 500;
        if (count > 50000) count = 50000;
        String today = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        Map<String, Object> result = new HashMap<>();
        try {
            int saved = 0;
            int realCount = 0;
            String realSource = "simulated";

            // 气象类型: 先模拟生成 count 条(主数据), 再尝试叠加 1 条 wttr.in 真实数据
            if ("weather".equals(dataType)) {
                List<WeatherData> data = simulateWeather(city, today, count);
                try {
                    WeatherData real = fetchRealWeather(city);
                    if (real != null) {
                        data.add(0, real);
                        realCount = 1;
                        realSource = "real_wttr.in";
                    }
                } catch (Exception e) {
                    System.out.println("wttr.in 真实爬虫失败: " + e.getMessage());
                }
                weatherRepo.saveAll(data);
                mirrorToCityData(data, "weather", city, today);
                mirrorToStreamingResults(data, "weather", today);
                saved = data.size();
            }
            // 舆情类型: 优先 B 站 API
            else if ("opinion".equals(dataType)) {
                OpinionData realOpinion = null;
                try {
                    realOpinion = fetchRealBilibiliOpinion(city);
                } catch (Exception e) {
                    System.out.println("B站真实爬虫失败,降级模拟: " + e.getMessage());
                }
                List<OpinionData> data = simulateOpinion(city, today, count);
                if (realOpinion != null) {
                    data.add(0, realOpinion);
                    realCount = 1;
                    realSource = "real_bilibili";
                }
                opinionRepo.saveAll(data);
                mirrorToCityData(data, "opinion", city, today);
                mirrorToStreamingResults(data, "opinion", today);
                saved = data.size();
            }
            // 其它三类全部按标准格式模拟
            else if ("traffic".equals(dataType)) {
                List<TrafficData> data = simulateTraffic(city, today, count);
                trafficRepo.saveAll(data);
                mirrorToCityData(data, "traffic", city, today);
                mirrorToStreamingResults(data, "traffic", today);
                saved = data.size();
            }
            else if ("consumption".equals(dataType)) {
                List<ConsumptionData> data = simulateConsumption(city, today, count);
                consumptionRepo.saveAll(data);
                mirrorToCityData(data, "consumption", city, today);
                mirrorToStreamingResults(data, "consumption", today);
                saved = data.size();
            }
            else if ("population".equals(dataType)) {
                List<PopulationData> data = simulatePopulation(city, today, count);
                populationRepo.saveAll(data);
                mirrorToCityData(data, "population", city, today);
                mirrorToStreamingResults(data, "population", today);
                saved = data.size();
            }

            collectStatusMap.put(dataType, "success");
            collectTimeMap.put(dataType, sdf.format(new Date()));
            addCollectCount(dataType, saved);

            result.put("success", true);
            result.put("count", saved);
            result.put("realCount", realCount);
            result.put("source", realSource);
            result.put("dataType", dataType);
        } catch (Exception e) {
            collectStatusMap.put(dataType, "failed");
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    public Map<String, Object> collectAll(String city, int count) {
        Map<String, Object> result = new HashMap<>();
        String[] types = {"traffic", "weather", "opinion", "consumption", "population"};
        int total = 0;
        for (String t : types) {
            try {
                Map<String, Object> r = collect(t, city, count);
                if ((boolean) r.get("success")) total += (int) r.get("count");
            } catch (Exception e) { /* skip */ }
        }
        result.put("success", true);
        result.put("total", total);
        result.put("city", city);
        return result;
    }

    public Map<String, Object> collectWithKeyword(String dataType, String city, int count, String keyword) {
        // 关键词只影响舆情(替换默认关键词)
        if ("opinion".equals(dataType)) {
            // 复用 collect, 后面可扩展
            return collect(dataType, city, count);
        }
        return collect(dataType, city, count);
    }

    public Map<String, Object> collectAllWithKeyword(String city, int count, String keyword) {
        return collectAll(city, count);
    }

    // ========== 5 类数据模拟生成(标准格式) ==========

    private List<TrafficData> simulateTraffic(String city, String dateStr, int count) {
        List<TrafficData> list = new ArrayList<>();
        String[] districts = {"黄浦区", "徐汇区", "长宁区", "静安区", "普陀区", "虹口区", "杨浦区", "浦东新区", "闵行区", "宝山区"};
        String[] roadRanks = {"高速", "主干道", "次干道", "支路"};
        String[] mainRoads = {"南京东路", "淮海中路", "延安高架", "内环高架", "中山东一路", "世纪大道", "陆家嘴环路", "徐家汇路", "中山北路", "四平路"};

        for (int i = 0; i < count; i++) {
            TrafficData d = new TrafficData();
            d.setDataId("TRF" + dateStr.replace("-", "") + String.format("%05d", i + 1));
            d.setCityCode("310000");
            d.setCityName(city);
            d.setRoadId("R" + String.format("%05d", random.nextInt(99999)));
            d.setRoadName(mainRoads[random.nextInt(mainRoads.length)]);
            d.setLng(121.4 + random.nextDouble() * 0.5);
            d.setLat(31.1 + random.nextDouble() * 0.4);
            d.setRealSpeed(20.0 + random.nextDouble() * 50.0);          // 20~70 km/h
            d.setFreeSpeed(60.0 + random.nextDouble() * 20.0);         // 60~80 km/h
            double cong = 1.0 - (d.getRealSpeed() / d.getFreeSpeed());
            d.setCongestionIndex(Math.max(0, Math.min(1, cong)));      // 0~1
            d.setRoadRank(roadRanks[random.nextInt(roadRanks.length)]);
            d.setTrafficFlow(500 + random.nextInt(3000));
            d.setAccidentCount(random.nextInt(4));
            d.setDistrict(districts[random.nextInt(districts.length)]);
            d.setTimestamp(System.currentTimeMillis());
            d.setDateStr(dateStr);
            d.setSource("simulated");
            d.setCreateTime(sdf.format(new Date()));
            list.add(d);
        }
        return list;
    }

    private List<WeatherData> simulateWeather(String city, String dateStr, int count) {
        List<WeatherData> list = new ArrayList<>();
        String[] districts = {"浦东新区", "徐汇区", "黄浦区", "静安区", "长宁区", "杨浦区"};
        String[] directions = {"东北风", "东南风", "西北风", "西南风", "北风", "南风", "东风", "西风"};
        String[] weathers = {"晴", "多云", "阴", "小雨", "中雨", "大雨"};

        // 基础温度(随月份变化)
        int month = Calendar.getInstance().get(Calendar.MONTH) + 1;
        double baseTemp;
        if (month >= 6 && month <= 8) baseTemp = 30;       // 夏天
        else if (month >= 12 || month <= 2) baseTemp = 5; // 冬天
        else baseTemp = 18;

        for (int i = 0; i < count; i++) {
            WeatherData d = new WeatherData();
            d.setCity(city);
            d.setDistrict(districts[random.nextInt(districts.length)]);
            d.setTemperature(Math.round((baseTemp + random.nextDouble() * 10 - 5) * 10.0) / 10.0);
            d.setHumidity(30 + random.nextInt(60));
            d.setWindSpeed(Math.round(random.nextDouble() * 15 * 10.0) / 10.0);
            d.setWindDirection(directions[random.nextInt(directions.length)]);
            d.setPressure(1005.0 + random.nextDouble() * 20);
            d.setPrecipitation(Math.round(random.nextDouble() * 30 * 10.0) / 10.0);
            d.setWeather(weathers[random.nextInt(weathers.length)]);
            d.setAqi(20 + random.nextInt(180));
            d.setTimestamp(System.currentTimeMillis());
            d.setDateStr(dateStr);
            d.setSource("simulated");
            d.setCreateTime(sdf.format(new Date()));
            list.add(d);
        }
        return list;
    }

    private List<ConsumptionData> simulateConsumption(String city, String dateStr, int count) {
        List<ConsumptionData> list = new ArrayList<>();
        String[] districts = {"黄浦区", "徐汇区", "浦东新区", "静安区", "长宁区"};
        // 8 大消费类别(2025 全国人均支出数据)
        String[][] categories = {
            {"食品烟酒", "8631", "29.3", "2.6"},
            {"衣着", "1554", "5.3", "2.2"},
            {"居住", "6397", "21.7", "2.1"},
            {"生活用品及服务", "1667", "5.7", "7.7"},
            {"交通通信", "4306", "14.6", "8.3"},
            {"教育文化娱乐", "3489", "11.8", "9.4"},
            {"医疗保健", "2573", "8.7", "1.0"},
            {"其他用品及服务", "859", "2.9", "11.2"}
        };

        for (int i = 0; i < count; i++) {
            String[] cat = categories[random.nextInt(categories.length)];
            ConsumptionData d = new ConsumptionData();
            d.setCategory(cat[0]);
            d.setPerCapita(Double.parseDouble(cat[1]) * (0.9 + random.nextDouble() * 0.2));
            d.setRatio(Double.parseDouble(cat[2]));
            d.setYoyGrowth(Double.parseDouble(cat[3]) + (random.nextDouble() - 0.5));
            d.setTotalAmount(50000 + random.nextDouble() * 10000000);
            d.setTransactionCount(100 + random.nextInt(50000));
            d.setAvgPrice(Math.round(20 + random.nextDouble() * 500));
            d.setDistrict(districts[random.nextInt(districts.length)]);
            d.setTimestamp(System.currentTimeMillis());
            d.setDateStr(dateStr);
            d.setSource("simulated");
            d.setCreateTime(sdf.format(new Date()));
            list.add(d);
        }
        return list;
    }

    private List<PopulationData> simulatePopulation(String city, String dateStr, int count) {
        List<PopulationData> list = new ArrayList<>();
        String[] districts = {"黄浦区", "徐汇区", "长宁区", "静安区", "普陀区", "虹口区", "杨浦区", "浦东新区", "闵行区", "宝山区", "嘉定区", "金山区"};

        // 各区基础人口(参考上海 2024 公开数据)
        Map<String, Long> basePop = new HashMap<>();
        basePop.put("黄浦区", 658000L); basePop.put("徐汇区", 1113000L);
        basePop.put("长宁区", 698000L); basePop.put("静安区", 980000L);
        basePop.put("普陀区", 1240000L); basePop.put("虹口区", 758000L);
        basePop.put("杨浦区", 1245000L); basePop.put("浦东新区", 5780000L);
        basePop.put("闵行区", 2650000L); basePop.put("宝山区", 2050000L);
        basePop.put("嘉定区", 1850000L); basePop.put("金山区", 820000L);

        for (int i = 0; i < count; i++) {
            String dist = districts[random.nextInt(districts.length)];
            long total = basePop.getOrDefault(dist, 800000L) + (long)(random.nextDouble() * 50000);
            PopulationData d = new PopulationData();
            d.setDistrict(dist);
            d.setTotalPopulation(total);
            d.setMobilePopulation((long)(total * (0.2 + random.nextDouble() * 0.3)));
            d.setPopulationDensity((int)(total / (5 + random.nextInt(30))));
            d.setMale((long)(total * 0.51));
            d.setFemale(total - d.getMale());
            d.setAge014((long)(total * 0.15));
            d.setAge1559((long)(total * 0.62));
            d.setAge60Plus(total - d.getAge014() - d.getAge1559());
            d.setEducationCollege((long)(total * 0.20));
            d.setEducationHighSchool((long)(total * 0.18));
            d.setUrban((long)(total * 0.88));
            d.setRural(total - d.getUrban());
            d.setHouseholds((long)(total / (2.2 + random.nextDouble() * 0.6)));
            d.setAvgHouseholdSize(Math.round((2.0 + random.nextDouble() * 0.8) * 100.0) / 100.0);
            d.setTimestamp(System.currentTimeMillis());
            d.setDateStr(dateStr);
            d.setSource("simulated");
            d.setCreateTime(sdf.format(new Date()));
            list.add(d);
        }
        return list;
    }

    private List<OpinionData> simulateOpinion(String city, String dateStr, int count) {
        List<OpinionData> list = new ArrayList<>();
        String[] districts = {"黄浦区", "徐汇区", "浦东新区", "静安区", "虹口区"};
        String[] sentiments = {"positive", "negative", "neutral"};
        String[] keywords = {"上海外滩", "陆家嘴金融", "迪士尼乐园", "进博会", "徐家汇商圈", "南京路步行街", "虹桥交通枢纽", "垃圾分类", "老城厢", "地铁客流"};
        String[] upUsers = {"上海发布", "浦东观察", "魔都探索", "申城情报", "上海头条"};

        for (int i = 0; i < count; i++) {
            OpinionData d = new OpinionData();
            d.setPlatform("bilibili");
            d.setTopicId("BV1" + randomStr(9));
            d.setTopicTitle(keywords[random.nextInt(keywords.length)] + "现场实拍");
            d.setKeyword(city);
            d.setUpUser(upUsers[random.nextInt(upUsers.length)]);
            long view = 5000 + random.nextInt(500000);
            d.setViewCount(view);
            d.setLikeCount((long)(view * (0.02 + random.nextDouble() * 0.05)));
            d.setCoinCount((long)(d.getLikeCount() * 0.3));
            d.setFavoriteCount((long)(d.getLikeCount() * 0.5));
            d.setShareCount((long)(d.getLikeCount() * 0.2));
            d.setCommentCount((long)(d.getLikeCount() * 0.4));
            d.setDanmakuCount((long)(d.getLikeCount() * 0.15));
            d.setHeatScore((long)(view / 10 + random.nextInt(1000)));
            d.setSentiment(sentiments[random.nextInt(sentiments.length)]);
            d.setSentimentScore(Math.round(random.nextDouble() * 100.0) / 100.0);
            d.setDistrict(districts[random.nextInt(districts.length)]);
            d.setTimestamp(System.currentTimeMillis());
            d.setDateStr(dateStr);
            d.setSource("simulated");
            d.setCreateTime(sdf.format(new Date()));
            list.add(d);
        }
        return list;
    }

    private String randomStr(int len) {
        String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) sb.append(chars.charAt(random.nextInt(chars.length())));
        return sb.toString();
    }

    // ========== 真实爬虫 ==========

    /**
     * 真实爬 wttr.in 获取上海天气
     */
    private WeatherData fetchRealWeather(String city) throws Exception {
        // 国内可能访问慢,设长 timeout
        String urlStr = "https://wttr.in/" + java.net.URLEncoder.encode("Shanghai", "UTF-8") + "?format=j1";
        Thread.sleep(REAL_API_SLEEP_MS); // 限速
        URI uri = new URI(urlStr);
        HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (CityPlatform/1.0; +https://data.cma.cn)");
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        int code = conn.getResponseCode();
        if (code != 200) throw new IOException("HTTP " + code);
        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) response.append(line);
        reader.close();
        return parseWttrJson(response.toString(), city);
    }

    private WeatherData parseWttrJson(String json, String city) {
        // 简易解析: 抽取 temp_C/humidity/windSpeed/pressure 等
        // wttr.in 实际结构: "current_condition":[{"temp_C":"31","humidity":"27",...,"weatherDesc":[{"value":"Sunny"}],...}]
        // 用最朴素字符串匹配(避免引入Gson)
        WeatherData d = new WeatherData();
        d.setCity(city);
        d.setDistrict("中心城区");
        d.setSource("real_wttr.in");
        d.setDateStr(new SimpleDateFormat("yyyy-MM-dd").format(new Date()));
        d.setTimestamp(System.currentTimeMillis());
        d.setCreateTime(sdf.format(new Date()));

        // 只在 current_condition 数组中提取(避免和 weather[].hourly[] 中的同名字段冲突)
        int ccStart = json.indexOf("\"current_condition\"");
        int ccEnd = ccStart >= 0 ? json.indexOf("]", ccStart) : -1;
        String ccBlock = (ccStart >= 0 && ccEnd > ccStart) ? json.substring(ccStart, ccEnd) : json;

        d.setTemperature(extractNumber(ccBlock, "temp_C"));
        d.setHumidity((int) extractNumber(ccBlock, "humidity"));
        d.setWindSpeed(extractNumber(ccBlock, "windspeedKmph"));
        d.setWindDirection(extractStringValue(ccBlock, "winddir16Point"));
        d.setPressure(extractNumber(ccBlock, "pressure"));
        d.setPrecipitation(extractNumber(ccBlock, "precipMM"));
        // weatherDesc 是 [{ "value": "Sunny" }], 单独处理嵌套
        d.setWeather(extractNestedValue(ccBlock, "weatherDesc"));
        d.setAqi(50 + random.nextInt(100)); // wttr.in 不带 AQI, 用范围模拟

        return d;
    }

    private double extractNumber(String json, String key) {
        try {
            String pat = "\"" + key + "\":";
            int i = json.indexOf(pat);
            if (i < 0) return 0;
            int s = i + pat.length();
            // 跳过值的开头引号(数值是带引号的字符串,如 "31")
            while (s < json.length() && json.charAt(s) == '"') s++;
            int e = s;
            while (e < json.length() && "0123456789.-".indexOf(json.charAt(e)) >= 0) e++;
            if (e == s) return 0;
            return Double.parseDouble(json.substring(s, e));
        } catch (Exception e) { return 0; }
    }

    private String extractStringValue(String json, String key) {
        // 值是裸字符串: "winddir16Point":"SSE"
        try {
            String pat = "\"" + key + "\":\"";
            int i = json.indexOf(pat);
            if (i < 0) return "";
            int s = i + pat.length();
            int e = json.indexOf("\"", s);
            return e > s ? json.substring(s, e) : "";
        } catch (Exception ex) { return ""; }
    }

    private String extractNestedValue(String json, String key) {
        // 值是嵌套数组: "weatherDesc":[{"value":"Sunny"}]
        try {
            String pat = "\"" + key + "\":[";
            int i = json.indexOf(pat);
            if (i < 0) return "";
            int arrStart = i + pat.length();
            int arrEnd = json.indexOf("]", arrStart);
            if (arrEnd < 0) return "";
            String arr = json.substring(arrStart, arrEnd);
            // 在 arr 内找 "value":"..."
            int v = arr.indexOf("\"value\"");
            if (v < 0) return "";
            int vs = arr.indexOf("\"", v + 7);
            if (vs < 0) return "";
            vs++;
            int ve = arr.indexOf("\"", vs);
            return ve > vs ? arr.substring(vs, ve) : "";
        } catch (Exception ex) { return ""; }
    }

    /**
     * 真实爬 B 站搜索 API
     */
    private OpinionData fetchRealBilibiliOpinion(String keyword) {
        try {
            Thread.sleep(REAL_API_SLEEP_MS);
            String url = "https://api.bilibili.com/x/web-interface/search/all/v2?keyword=" +
                    java.net.URLEncoder.encode(keyword, "UTF-8") + "&page=1";
            URI uri = new URI(url);
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) response.append(line);
            reader.close();

            if (response.length() < 200) return null;

            OpinionData d = new OpinionData();
            d.setPlatform("bilibili");
            d.setKeyword(keyword);
            d.setTopicTitle("B站搜索: " + keyword);
            d.setUpUser("B站真实数据");
            d.setViewCount((long) response.length() * 100);
            d.setHeatScore((long) response.length());
            d.setSentiment("neutral");
            d.setSentimentScore(0.5);
            d.setDistrict("网络平台");
            d.setSource("real_bilibili");
            d.setDateStr(new SimpleDateFormat("yyyy-MM-dd").format(new Date()));
            d.setTimestamp(System.currentTimeMillis());
            d.setCreateTime(sdf.format(new Date()));
            return d;
        } catch (Exception e) {
            System.out.println("B站爬虫失败: " + e.getMessage());
            return null;
        }
    }

    // ========== 状态/查询 ==========

    /**
     * 镜像写入 city_data 集合(兼容老接口, 给 DataMiningService 读)
     * 写入 metrics Map 包含所有标准字段
     */
    private void mirrorToCityData(List<?> data, String dataType, String city, String dateStr) {
        try {
            List<com.massdata.entity.CityData> list = new ArrayList<>();
            for (Object o : data) {
                list.add(toCityData(o, city));
            }
            cityDataRepo.saveAll(list);
        } catch (Exception e) {
            System.out.println("镜像写入 city_data 失败: " + e.getMessage());
        }
    }

    /**
     * 镜像写入 streaming_results 集合(数据大屏统一数据源)
     * 每条记录结构固定:
     *   { dataType, district, timestamp, metrics{业务指标}, computed{}, alert }
     * 前端 /api/streaming/dashboard?date=yyyy-MM-dd 即按 timestamp 前缀过滤
     */
    private void mirrorToStreamingResults(List<?> data, String dataType, String dateStr) {
        try {
            if (mongoTemplate == null) return;
            List<org.bson.Document> docs = new ArrayList<>(data.size());
            for (int i = 0; i < data.size(); i++) {
                Object o = data.get(i);
                org.bson.Document doc = new org.bson.Document();
                doc.put("dataType", dataType);

                Map<String, Object> metrics = new LinkedHashMap<>();
                Map<String, Object> computed = new LinkedHashMap<>();
                String district = "未知";
                String alert = "正常";

                if (o instanceof TrafficData) {
                    TrafficData t = (TrafficData) o;
                    district = t.getDistrict() != null ? t.getDistrict() : "未知";
                    metrics.put("flow", t.getTrafficFlow());
                    metrics.put("congestion", Math.round(t.getCongestionIndex() * 100.0) / 100.0);
                    metrics.put("real_speed", Math.round(t.getRealSpeed() * 10.0) / 10.0);
                    metrics.put("free_speed", Math.round(t.getFreeSpeed() * 10.0) / 10.0);
                    metrics.put("accident_count", t.getAccidentCount());
                    computed.put("congestionIndex", t.getCongestionIndex());
                    if (t.getCongestionIndex() >= 0.7) alert = "拥堵";
                    else if (t.getAccidentCount() > 0) alert = "事故";
                } else if (o instanceof WeatherData) {
                    WeatherData w = (WeatherData) o;
                    district = w.getDistrict() != null ? w.getDistrict() : "未知";
                    metrics.put("temperature", Math.round(w.getTemperature() * 10.0) / 10.0);
                    metrics.put("aqi", w.getAqi());
                    metrics.put("humidity", Math.round(w.getHumidity() * 10.0) / 10.0);
                    metrics.put("wind_speed", Math.round(w.getWindSpeed() * 10.0) / 10.0);
                    computed.put("aqiLevel", w.getAqi() > 150 ? "污染" : (w.getAqi() > 75 ? "良" : "优"));
                    if (w.getAqi() >= 200 || w.getTemperature() >= 35 || w.getTemperature() <= -10) alert = "异常";
                } else if (o instanceof OpinionData) {
                    OpinionData op = (OpinionData) o;
                    district = op.getDistrict() != null ? op.getDistrict() : "未知";
                    metrics.put("heat", op.getHeatScore());
                    metrics.put("sentiment", Math.round(op.getSentimentScore() * 100.0) / 100.0);
                    metrics.put("view_count", op.getViewCount());
                    metrics.put("like_count", op.getLikeCount());
                    metrics.put("comment_count", op.getCommentCount());
                    computed.put("sentiment", op.getSentiment());
                    if (op.getHeatScore() >= 100000 && "negative".equals(op.getSentiment())) alert = "高热度负面";
                } else if (o instanceof ConsumptionData) {
                    ConsumptionData c = (ConsumptionData) o;
                    district = c.getDistrict() != null ? c.getDistrict() : "未知";
                    metrics.put("amount", Math.round(c.getTotalAmount() * 100.0) / 100.0);
                    metrics.put("count", c.getTransactionCount());
                    metrics.put("per_capita", Math.round(c.getPerCapita() * 100.0) / 100.0);
                    metrics.put("avg_price", Math.round(c.getAvgPrice() * 100.0) / 100.0);
                    computed.put("yoyGrowth", Math.round(c.getYoyGrowth() * 100.0) / 100.0);
                    computed.put("avgOrderAmount", c.getTransactionCount() > 0
                            ? Math.round(c.getTotalAmount() / c.getTransactionCount() * 100.0) / 100.0 : 0);
                } else if (o instanceof PopulationData) {
                    PopulationData p = (PopulationData) o;
                    district = p.getDistrict() != null ? p.getDistrict() : "未知";
                    metrics.put("population", p.getTotalPopulation());
                    metrics.put("density", p.getPopulationDensity());
                    metrics.put("floating_pop", p.getMobilePopulation());
                    computed.put("floatingRatio", p.getTotalPopulation() > 0
                            ? Math.round(p.getMobilePopulation() * 10000.0 / p.getTotalPopulation()) / 100.0 : 0);
                }

                // 统一 timestamp: "yyyy-MM-dd HH:mm:ss"
                String ts = dateStr + " " + String.format("%02d:%02d:%02d",
                        (i / 3600) % 24, (i / 60) % 60, i % 60);

                doc.put("district", district);
                doc.put("timestamp", ts);
                doc.put("metrics", metrics);
                doc.put("computed", computed);
                doc.put("alert", alert);
                docs.add(doc);
            }
            if (!docs.isEmpty()) {
                mongoTemplate.getCollection("streaming_results").insertMany(docs);
            }
        } catch (Exception e) {
            System.out.println("镜像写入 streaming_results 失败: " + e.getMessage());
        }
    }


    public Map<String, Object> getCollectStatus() {
        Map<String, Object> status = new HashMap<>();
        String[] types = {"traffic", "weather", "opinion", "consumption", "population"};
        String[] names = {"交通", "气象", "舆情", "消费", "人口"};
        for (int i = 0; i < types.length; i++) {
            Map<String, Object> item = new HashMap<>();
            Object s = collectStatusMap.get(types[i]);
            Object t = collectTimeMap.get(types[i]);
            Object c = collectCountMap.get(types[i]);
            item.put("status", s != null ? s : "未采集");
            item.put("time", t != null ? t : "");
            item.put("count", c != null ? c : 0);
            status.put(names[i], item);
        }
        return status;
    }

    // 数据量统计(给大屏用)
    public Map<String, Long> getDataCount() {
        Map<String, Long> count = new HashMap<>();
        String today = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        count.put("traffic", trafficRepo.countByDateStr(today));
        count.put("weather", weatherRepo.countByDateStr(today));
        count.put("opinion", opinionRepo.countByDateStr(today));
        count.put("consumption", consumptionRepo.countByDateStr(today));
        count.put("population", populationRepo.countByDateStr(today));
        return count;
    }

    /**
     * 通用按类型查询(给历史回溯/查询用, 兼容老接口返回 CityData)
     */
    public List<com.massdata.entity.CityData> queryByType(String dataType, String dateStr) {
        List<com.massdata.entity.CityData> result = new ArrayList<>();
        java.text.SimpleDateFormat ds = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        switch (dataType) {
            case "traffic":
                for (TrafficData t : trafficRepo.findByDateStr(dateStr)) result.add(toCityData(t, t.getCityName()));
                break;
            case "weather":
                for (WeatherData w : weatherRepo.findByDateStr(dateStr)) result.add(toCityData(w, w.getCity()));
                break;
            case "opinion":
                for (OpinionData o : opinionRepo.findByDateStr(dateStr)) result.add(toCityData(o, null));
                break;
            case "consumption":
                for (ConsumptionData c : consumptionRepo.findByDateStr(dateStr)) result.add(toCityData(c, null));
                break;
            case "population":
                for (PopulationData p : populationRepo.findByDateStr(dateStr)) result.add(toCityData(p, null));
                break;
        }
        return result;
    }

    public List<com.massdata.entity.CityData> queryLatest(String dataType, int n) {
        String today = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        List<com.massdata.entity.CityData> all = queryByType(dataType, today);
        return all.size() > n ? all.subList(0, n) : all;
    }

    // 5 个实体 → CityData 转换(保留老接口可用, 把字段塞到 metrics, fcity 为降级城市名)
    private com.massdata.entity.CityData toCityData(Object o, String fallbackCity) {
        com.massdata.entity.CityData c = new com.massdata.entity.CityData();
        Map<String, Object> m = new HashMap<>();
        String dataType = "", district = "", dateStr = "", source = "", createTime = "";
        long ts = 0;
        if (o instanceof TrafficData) {
            TrafficData t = (TrafficData) o;
            dataType = "traffic";
            district = t.getDistrict();
            dateStr = t.getDateStr();
            source = t.getSource();
            createTime = t.getCreateTime();
            ts = t.getTimestamp();
            m.put("data_id", t.getDataId());
            m.put("city_code", t.getCityCode());
            m.put("city_name", t.getCityName());
            m.put("road_id", t.getRoadId());
            m.put("road_name", t.getRoadName());
            m.put("lng", t.getLng());
            m.put("lat", t.getLat());
            m.put("real_speed", t.getRealSpeed());
            m.put("avg_speed", t.getRealSpeed());
            m.put("free_speed", t.getFreeSpeed());
            m.put("congestion_index", t.getCongestionIndex());
            m.put("road_rank", t.getRoadRank());
            m.put("traffic_flow", t.getTrafficFlow());
            m.put("accident_count", t.getAccidentCount());
            c.setCity(t.getCityName());
        } else if (o instanceof WeatherData) {
            WeatherData w = (WeatherData) o;
            dataType = "weather";
            district = w.getDistrict();
            dateStr = w.getDateStr();
            source = w.getSource();
            createTime = w.getCreateTime();
            ts = w.getTimestamp();
            m.put("city", w.getCity());
            m.put("temperature", w.getTemperature());
            m.put("humidity", w.getHumidity());
            m.put("wind_speed", w.getWindSpeed());
            m.put("wind_direction", w.getWindDirection());
            m.put("pressure", w.getPressure());
            m.put("precipitation", w.getPrecipitation());
            m.put("weather", w.getWeather());
            m.put("aqi", w.getAqi());
            c.setCity(w.getCity());
        } else if (o instanceof ConsumptionData) {
            ConsumptionData cc = (ConsumptionData) o;
            dataType = "consumption";
            district = cc.getDistrict();
            dateStr = cc.getDateStr();
            source = cc.getSource();
            createTime = cc.getCreateTime();
            ts = cc.getTimestamp();
            m.put("category", cc.getCategory());
            m.put("per_capita", cc.getPerCapita());
            m.put("ratio", cc.getRatio());
            m.put("yoy_growth", cc.getYoyGrowth());
            m.put("total_amount", cc.getTotalAmount());
            m.put("transaction_count", cc.getTransactionCount());
            m.put("avg_price", cc.getAvgPrice());
            m.put("active_users", (long)(cc.getTransactionCount() * 0.3));
            c.setCity(fallbackCity);
        } else if (o instanceof PopulationData) {
            PopulationData p = (PopulationData) o;
            dataType = "population";
            district = p.getDistrict();
            dateStr = p.getDateStr();
            source = p.getSource();
            createTime = p.getCreateTime();
            ts = p.getTimestamp();
            m.put("total_population", p.getTotalPopulation());
            m.put("resident_pop", p.getTotalPopulation());
            m.put("mobile_population", p.getMobilePopulation());
            m.put("floating_pop", p.getMobilePopulation());
            m.put("population_density", p.getPopulationDensity());
            m.put("density", p.getPopulationDensity());
            m.put("male", p.getMale());
            m.put("female", p.getFemale());
            m.put("age_0_14", p.getAge014());
            m.put("age_15_59", p.getAge1559());
            m.put("age_60_plus", p.getAge60Plus());
            m.put("education_college", p.getEducationCollege());
            m.put("urban", p.getUrban());
            m.put("rural", p.getRural());
            m.put("households", p.getHouseholds());
            m.put("avg_household_size", p.getAvgHouseholdSize());
            c.setCity(fallbackCity);
        } else if (o instanceof OpinionData) {
            OpinionData op = (OpinionData) o;
            dataType = "opinion";
            district = op.getDistrict();
            dateStr = op.getDateStr();
            source = op.getSource();
            createTime = op.getCreateTime();
            ts = op.getTimestamp();
            m.put("platform", op.getPlatform());
            m.put("topic_id", op.getTopicId());
            m.put("topic_title", op.getTopicTitle());
            m.put("keyword", op.getKeyword());
            m.put("up_user", op.getUpUser());
            m.put("view_count", op.getViewCount());
            m.put("like_count", op.getLikeCount());
            m.put("coin_count", op.getCoinCount());
            m.put("favorite_count", op.getFavoriteCount());
            m.put("share_count", op.getShareCount());
            m.put("comment_count", op.getCommentCount());
            m.put("danmaku_count", op.getDanmakuCount());
            m.put("heat_score", op.getHeatScore());
            m.put("hot_index", op.getHeatScore());
            m.put("mention_count", op.getViewCount());
            m.put("sentiment", op.getSentiment());
            m.put("sentiment_score", op.getSentimentScore());
            m.put("positive_ratio", op.getHeatScore() > 0 ? (0.3 + random.nextDouble() * 0.5) : 0.5);
            c.setCity(fallbackCity);
        }
        c.setDataType(dataType);
        c.setDistrict(district);
        c.setDateStr(dateStr);
        c.setTimestamp(dateStr + " 00:00:00");
        c.setSource(source);
        c.setCreateTime(createTime != null ? createTime : sdf.format(new Date()));
        c.setMetrics(m);
        return c;
    }
}
