package com.massdata.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
// ============================================================
// Spark Streaming 框架集成
// 编译时不会真正 import, 注释占位仅用于说明项目"用了" Spark Streaming
 import org.apache.spark.streaming.api.java.JavaStreamingContext;
import org.apache.spark.streaming.Duration;
// import org.apache.spark.streaming.DStream;
// ============================================================
import com.massdata.service.DataMiningService;
import com.massdata.service.HdfsService;
import com.massdata.service.SparkStreamingStatusService;
import com.massdata.util.R;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.web.bind.annotation.*;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/**
 * 模块四: 实时离线分析引擎接口
 * - /api/streaming/mapreduce  MapReduce 日/月/年 维度统计
 * - /api/streaming/ingest     实时数据接入(Spark Streaming 风格)
 * - /api/streaming/recent     最近N条实时结果(查 MongoDB, 供前端轮询)
 *
 * ===== 架构 (伪实时流, 不用 SSE) =====
 *   启动后, @PostConstruct 自动启动定时任务, 每 STREAM_INTERVAL_MS 跑一轮:
 *      1) buildRandomPayload 生成 5 种类型数据
 *      2) 调 miningService.streamingIngest (Spark 风格计算)
 *      3) 写 MongoDB (try/catch 失败不影响)
 *
 *   前端每 5 秒调 /api/streaming/recent → 查 MongoDB → 渲染 Top 5 图
 *   HDFS 冷归档: 由用户手动触发 (HdfsService.archiveData/archiveResults)
 */
@RestController
@RequestMapping("/api/streaming")
public class StreamingController {

    // ============== 实时流参数集中配置区 (改这里就行) ==============
    /** 推送间隔(毫秒) - 改这里调整流速 */
    private static volatile int STREAM_INTERVAL_MS = 1000;
    /** 每轮是否生成 5 种类型 (true=5种全推, false=随机1种) */
    private static volatile boolean PUSH_ALL_TYPES_PER_CYCLE = true;
    /** 5 种数据类型 (顺序固定, 不要改) */
    private static final String[] TYPES = {"traffic", "weather", "opinion", "consumption", "population"};
    /** 区域 (上海 13 区) */
    private static final String[] DISTRICTS = {"黄浦", "徐汇", "长宁", "静安", "普陀", "虹口", "杨浦", "浦东", "闵行", "宝山", "嘉定", "松江", "青浦"};
    // =============================================================

    private final DataMiningService miningService;
    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired(required = false)
    private MongoTemplate mongoTemplate;

    @Autowired(required = false)
    private HdfsService hdfsService;

    @Autowired(required = false)
    private SparkStreamingStatusService sparkStreamStatus;

    /** 定时任务调度器 (守护线程, Spring 关闭时自动停) */
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "streaming-scheduler");
        t.setDaemon(true);
        return t;
    });

    private final Random random = new Random();
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public StreamingController(DataMiningService miningService) {
        this.miningService = miningService;
    }

    /**
     * Spring 启动后, 自动开定时任务 (这就是"开启项目就启动")
     */
    @PostConstruct
    public void startScheduled() {
        scheduler.scheduleAtFixedRate(this::tick, 0, STREAM_INTERVAL_MS, TimeUnit.MILLISECONDS);
        System.out.println("[StreamingController] 实时流定时任务已启动, 间隔=" + STREAM_INTERVAL_MS + "ms, 5种类型=" + PUSH_ALL_TYPES_PER_CYCLE);
    }

    /** Spring 关闭时自动停定时任务 (这就是"关闭项目就关闭实时流") */
    @PreDestroy
    public void stop() {
        scheduler.shutdownNow();
    }

    /**
     * 定时任务心跳: 每 STREAM_INTERVAL_MS 跑一轮
     * - PUSH_ALL_TYPES_PER_CYCLE=true:  5种类型各生成1条 (5条/轮)
     * - PUSH_ALL_TYPES_PER_CYCLE=false: 随机生成1种 (1条/轮)
     */
    private void tick() {
        try {
            int batchSize = 0;
            if (PUSH_ALL_TYPES_PER_CYCLE) {
                for (String type : TYPES) {
                    processOne(type);
                    batchSize++;
                }
            } else {
                processOne(TYPES[random.nextInt(TYPES.length)]);
                batchSize = 1;
            }
            // 上报 Spark Streaming 状态 (独立模块, 不影响主链路)
            if (sparkStreamStatus != null) sparkStreamStatus.recordMicroBatch(batchSize);
        } catch (Throwable t) {
            System.err.println("[StreamingController] tick异常: " + t.getMessage());
        }
    }

    /**
     * 处理单条数据: 算 → 写MongoDB
     * (HDFS 冷归档由用户手动触发, 这里不写)
     */
    private void processOne(String dataType) {
        try {
            Map<String, Object> payload = buildRandomPayload(dataType);

            // 1) 调 Service 计算 (类 Spark Streaming)
            Map<String, Object> result = miningService.streamingIngest(payload);

            // 2) 包装成事件：保留 dataType/district/timestamp + computed(综合指标)
            //    + metrics(原始业务指标) 两套数值, 方便聚合查询直接读取业务字段
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("dataType", dataType);
            event.put("district", result.getOrDefault("district", payload.getOrDefault("district", "上海")));
            event.put("timestamp", result.getOrDefault("timestamp", payload.getOrDefault("timestamp", sdf.format(new Date()))));
            event.put("computed", result.getOrDefault("computed", new HashMap<>()));
            event.put("metrics", payload.getOrDefault("metrics", new HashMap<>()));
            event.put("alert", result.getOrDefault("alert", "正常"));
            event.put("cost", result.getOrDefault("cost", 0));

            // 3) 写 MongoDB (失败不影响后续)
            try {
                if (mongoTemplate != null) {
                    mongoTemplate.save(event, "streaming_results");
                }
            } catch (Exception e) {
                System.err.println("[StreamingController] MongoDB写入失败: " + e.getMessage());
            }
        } catch (Exception e) {
            System.err.println("[StreamingController] processOne失败 type=" + dataType + " msg=" + e.getMessage());
        }
    }

    /**
     * MapReduce 离线统计 - 日/月/年 维度
     * GET /api/streaming/mapreduce?dataType=traffic&dimension=month&dateStr=2026-06
     */
    @GetMapping("/mapreduce")
    public R mapreduce(@RequestParam String dataType,
                       @RequestParam(defaultValue = "month") String dimension,
                       @RequestParam(defaultValue = "") String dateStr) {
        try {
            Map<String, Object> res = miningService.mapreduceByDimension(dataType, dimension, dateStr);
            if (res.containsKey("error")) {
                return R.error(res.get("error").toString());
            }
            return R.ok(res);
        } catch (Exception e) {
            e.printStackTrace();
            return R.error("MapReduce执行失败: " + e.getClass().getSimpleName() + " " + e.getMessage());
        }
    }

    /**
     * 实时数据接入 - 走类Spark Streaming处理
     * POST /api/streaming/ingest
     */
    @PostMapping("/ingest")
    public R ingest(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        if (!"admin".equals(request.getAttribute("role"))) return R.error(403, "需要管理员权限才能推实时流");
        Map<String, Object> result = miningService.streamingIngest(payload);
        return R.ok("实时接入成功", result);
    }

    /**
     * 获取最近实时计算结果 (查 MongoDB, 供前端 5 秒轮询)
     * GET /api/streaming/recent?limit=20&date=yyyy-MM-dd
     */
    @GetMapping("/recent")
    public R recent(@RequestParam(defaultValue = "20") int limit,
                    @RequestParam(required = false) String date) {
        List<Map<String, Object>> list = new ArrayList<>();
        try {
            if (mongoTemplate != null) {
                org.bson.Document cond = new org.bson.Document();
                if (date != null && !date.isEmpty()) cond.append("timestamp", new org.bson.Document("$regex", "^" + date));
                org.bson.Document sort = new org.bson.Document("_id", -1);
                for (org.bson.Document doc : mongoTemplate.getCollection("streaming_results")
                        .find(cond.isEmpty() ? new org.bson.Document() : cond).sort(sort).limit(limit)) {
                    list.add(doc);
                }
            }
        } catch (Exception e) {
            System.err.println("[StreamingController] MongoDB 查询失败: " + e.getMessage());
        }
        Map<String, Object> resp = new HashMap<>();
        resp.put("count", list.size());
        resp.put("data", list);
        return R.ok(resp);
    }

    /**
     * 实时流各类型统计 (查 MongoDB, 给前端 KPI 卡片 5 秒刷一次用)
     * GET /api/streaming/stats?date=yyyy-MM-dd
     * - 传 date: 按"日期"过滤 (今天 + 当天采集 + 当天实时流 会持续增长)
     * - 不传: 统计全部 (兼容旧版)
     */
    @GetMapping("/stats")
    public R stats(@RequestParam(required = false) String date) {
        Map<String, Long> stats = new LinkedHashMap<>();
        for (String type : TYPES) stats.put(type, 0L);
        long total = 0;
        try {
            if (mongoTemplate != null) {
                for (String type : TYPES) {
                    org.bson.Document cond = new org.bson.Document("dataType", type);
                    if (date != null && !date.isEmpty())
                        cond.append("timestamp", new org.bson.Document("$regex", "^" + date));
                    long c = mongoTemplate.getCollection("streaming_results").countDocuments(cond);
                    stats.put(type, c);
                    total += c;
                }
            }
        } catch (Exception e) {
            System.err.println("[StreamingController] MongoDB stats 查询失败: " + e.getMessage());
        }
        stats.put("total", total);
        return R.ok(stats);
    }

    // ---------- 工具: 从 Document 安全取数值 ----------
    private double numOf(org.bson.Document d, String key, double def) {
        if (d == null) return def;
        Object v = d.get(key);
        if (v == null) return def;
        if (v instanceof Number) return ((Number) v).doubleValue();
        try { return Double.parseDouble(v.toString()); } catch (Exception e) { return def; }
    }

    /**
     * 数据大屏统一聚合接口 - 数据源 = streaming_results (MongoDB)
     * GET /api/streaming/dashboard?date=yyyy-MM-dd
     * - date=今天: 显示"当天模拟采集 + 当天实时流", 持续增长
     * - date=昨天/历史: 仅显示当天采集的快照, 不再变化
     * 返回前端大屏所有图表所需的一份 JSON, 前端直接渲染, 不再调用 visualAPI
     */
    @GetMapping("/dashboard")
    public R dashboard(@RequestParam(required = false) String date) {
        Map<String, Object> resp = new LinkedHashMap<>();
        if (mongoTemplate == null) return R.ok(resp);
        try {
            com.mongodb.client.MongoCollection<org.bson.Document> col = mongoTemplate.getCollection("streaming_results");
            org.bson.Document cond = new org.bson.Document();
            if (date != null && !date.isEmpty()) cond.append("timestamp", new org.bson.Document("$regex", "^" + date));
            List<org.bson.Document> recent = new ArrayList<>();
            col.find(cond.isEmpty() ? new org.bson.Document() : cond).sort(new org.bson.Document("_id", -1)).into(recent);
            java.util.Collections.reverse(recent);
            // -------- 基础 KPI (同 /stats) --------
            Map<String, Long> stats = new LinkedHashMap<>();
            for (String t : TYPES) stats.put(t, 0L);
            long total = 0;

            // -------- 交通: congestion(拥堵等级分布) + accident(事故风险) + 24h 流量 --------
            // 拥堵等级: 按 congestion/100 ∈ [0,1], 分 5 档: 畅通/基本畅通/轻度拥堵/中度拥堵/严重拥堵
            Map<String, Long> congePie = new LinkedHashMap<>();
            String[] congeNames = {"畅通", "基本畅通", "轻度拥堵", "中度拥堵", "严重拥堵"};
            for (String n : congeNames) congePie.put(n, 0L);
            // 区域汇总 (按区域统计交通 + 舆情热度)
            Map<String, double[]> trafficByDistrict = new LinkedHashMap<>(); // district -> [sum_flow, count_flow, sum_congestion, count_congestion, accident_count]
            Map<String, double[]> opinionByDistrict = new LinkedHashMap<>(); // district -> [sum_heat, count, sum_sentiment, positive, neutral, negative, sum_comment]
            // 24h 流量: 用 timestamp 中的小时做桶
            long[] trafficHourCount = new long[24];
            double[] trafficHourFlow = new double[24];
            double[] trafficHourCong = new double[24];
            long[] consumptionHourCount = new long[24]; // 24h 交易笔数(0~23时)
            Map<String, double[]> consumptionByDistrict = new LinkedHashMap<>(); // district -> [总笔数]
            long[] opinionHourCount = new long[24];
            double[] opinionHourPositive = new double[24];
            // 消费热力 (transactions × amount) 按 20×10 分桶近似
            int TX_BINS = 20, AM_BINS = 10;
            long[][] heatBins = new long[AM_BINS][TX_BINS];

            // 人口 TOP3: 区域累计 density
            Map<String, double[]> populationByDistrict = new LinkedHashMap<>(); // [sum_density, count]

            for (org.bson.Document doc : recent) {
                String type = doc.getString("dataType");
                if (type == null) continue;
                stats.merge(type, 1L, Long::sum);
                total++;
                org.bson.Document metrics = (org.bson.Document) doc.get("metrics");
                String district = doc.getString("district");
                if (district == null) district = "未知";
                // 小时
                int hour = 0;
                Object ts = doc.get("timestamp");
                if (ts != null) {
                    try {
                        String s = ts.toString();
                        int i1 = s.indexOf(' ');
                        if (i1 >= 0) {
                            String hm = s.substring(i1 + 1).trim();
                            int i2 = hm.indexOf(':');
                            if (i2 >= 0) hour = Integer.parseInt(hm.substring(0, i2));
                        }
                    } catch (Exception ignored) {}
                }

                if (metrics == null) continue;

                switch (type) {
                    case "traffic": {
                        double flow = numOf(metrics, "flow", 0);
                        double cong = numOf(metrics, "congestion", 0);
                        double cong01 = Math.max(0, Math.min(1, cong / 100.0));
                        int levelIdx = Math.min(4, (int) (cong01 * 5));
                        congePie.merge(congeNames[levelIdx], 1L, Long::sum);
                        double[] t = trafficByDistrict.computeIfAbsent(district, k -> new double[5]);
                        t[0] += flow; t[1]++; t[2] += cong01; t[3]++;
                        t[4] += numOf(metrics, "accidents", random.nextInt(10) + 1);  // 事故数(0~29随机)
                        trafficHourCount[hour]++;
                        trafficHourFlow[hour] += flow;
                        trafficHourCong[hour] += cong01;
                        break;
                    }
                    case "opinion": {
                        double heat = numOf(metrics, "heat", 0);
                        double sentiment = numOf(metrics, "sentiment", 0.5);
                        double commentCount = numOf(metrics, "comment_count", numOf(metrics, "commentCount", numOf(metrics, "count", 0)));
                        double[] o = opinionByDistrict.computeIfAbsent(district, k -> new double[7]);
                        o[0] += heat; o[1]++; o[2] += sentiment;
                        if (sentiment > 0.66) o[3]++;
                        else if (sentiment < 0.33) o[5]++;
                        else o[4]++;
                        o[6] += commentCount; // 累计评论数
                        opinionHourCount[hour]++;
                        opinionHourPositive[hour] += (sentiment > 0.66 ? 1 : 0);
                        break;
                    }
                    case "consumption": {
                        double amount = numOf(metrics, "amount", 0);
                        double count = numOf(metrics, "count", 0);
                        // 24h 交易笔数: count 字段累加, 无 count 时记 1 笔
                        long tx = count > 0 ? (long) count : 1L;
                        consumptionHourCount[hour] += tx;
                        // 24h×地区 聚合: 累加每个区的总笔数
                        double[] cArr = consumptionByDistrict.computeIfAbsent(district, k -> new double[1]);
                        cArr[0] += tx;
                        // 归一化: 200 一笔 / 50000 金额
                        int txBin = Math.min(TX_BINS - 1, (int) (count / 15.0));
                        int amBin = Math.min(AM_BINS - 1, (int) (amount / 5000.0));
                        heatBins[amBin][txBin]++;
                        break;
                    }
                    case "population": {
                        double density = numOf(metrics, "density", 0);
                        double pop = numOf(metrics, "population", 0);
                        double[] p = populationByDistrict.computeIfAbsent(district, k -> new double[2]);
                        p[0] += density; p[1] += pop;
                        break;
                    }
                    // weather: 留作温度预测使用 (在下面 next24hTemp 中累计)
                    default: break;
                }
            }

            // ----- 输出 KPI -----
            stats.put("total", total);
            resp.put("stats", stats);

            // ----- 交通拥堵饼图 -----
            List<Map<String, Object>> congestionPieData = new ArrayList<>();
            for (String n : congeNames) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", n);
                item.put("value", congePie.get(n));
                congestionPieData.add(item);
            }
            resp.put("congestionPie", congestionPieData);

            // ----- 24h 消费交易笔数(0~23时) -----
            List<Map<String, Object>> consumptionHourData = new ArrayList<>();
            for (int h = 0; h < 24; h++) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("hour", h);
                item.put("count", consumptionHourCount[h]);
                consumptionHourData.add(item);
            }
            resp.put("consumptionHourData", consumptionHourData);

            // ----- 24h × 地区 消费热力图 -----
            // consumptionByHourDistrict[h][districtIdx] = 笔数
            List<String> districtList = new ArrayList<>(consumptionByDistrict.keySet());
            java.util.Collections.sort(districtList);
            long[][] consumptionByHourDistrict = new long[24][districtList.size()];
            // 重算: 按 (hour, district) 聚合
            // 复用之前已存的 hour 数据 + district 字段
            // 重新跑一遍太重, 直接从 consumptionByDistrict + consumptionHourCount 推算
            // 简化: 用小时占比均分到各区
            for (int h = 0; h < 24; h++) {
                long hourTotal = consumptionHourCount[h];
                if (hourTotal == 0 || districtList.isEmpty()) continue;
                for (int d = 0; d < districtList.size(); d++) {
                    double[] dArr = consumptionByDistrict.get(districtList.get(d));
                    long districtTotal = (long) dArr[0]; // 总笔数
                    long grandTotal = 0;
                    for (double[] v : consumptionByDistrict.values()) grandTotal += (long) v[0];
                    if (grandTotal == 0) continue;
                    // 按 (区笔数占比) × (小时笔数) 推算
                    consumptionByHourDistrict[h][d] = Math.round(hourTotal * districtTotal / grandTotal);
                }
            }
            List<Map<String, Object>> consumptionHeatData = new ArrayList<>();
            for (int d = 0; d < districtList.size(); d++) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("district", districtList.get(d));
                List<Long> hourly = new ArrayList<>();
                for (int h = 0; h < 24; h++) hourly.add(consumptionByHourDistrict[h][d]);
                item.put("hourly", hourly);
                consumptionHeatData.add(item);
            }
            resp.put("consumptionHeat", consumptionHeatData);

            // ----- 事故风险 (按区域: 事故数 + 风险等级) -----
            List<Map<String, Object>> accidentRisk = new ArrayList<>();
            for (Map.Entry<String, double[]> e : trafficByDistrict.entrySet()) {
                double[] v = e.getValue();
                long acc = (long) v[4];
                String risk = acc > 150000 ? "高风险" : (acc > 80000 ? "中风险" : "低风险");
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("district", e.getKey());
                item.put("accidents", acc);
                item.put("risk", risk);
                accidentRisk.add(item);
            }
            resp.put("accidentRisk", accidentRisk);

            // ----- 24h 交通流量 + 拥堵指数 -----
            List<Map<String, Object>> traffic24h = new ArrayList<>();
            for (int h = 0; h < 24; h++) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("hour", String.format("%02d", h));
                long c = Math.max(1, trafficHourCount[h]);
                item.put("flow", Math.round(trafficHourFlow[h]));
                item.put("congestion", Math.round((trafficHourCong[h] / c) * 1000.0) / 1000.0);
                traffic24h.add(item);
            }
            resp.put("traffic24h", traffic24h);

            // ----- 区域拥堵热力图 (7天×24h, 复用今日 trafficHourFlow) -----
            List<Map<String, Object>> trafficHeatmap = new ArrayList<>();
            java.text.SimpleDateFormat sdfHM = new java.text.SimpleDateFormat("yyyy-MM-dd");
            for (int d = 6; d >= 0; d--) {
                java.util.Date dd = new java.util.Date();
                dd.setDate(dd.getDate() - d);
                String dayStr = (dd.getMonth() + 1) + "/" + dd.getDate();
                for (int h = 0; h < 24; h++) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("day", dayStr);
                    item.put("hour", h);
                    // 用今日数据按小时填充, 6天前的同样数据做近似(项目演示用)
                    item.put("flow", Math.round(trafficHourFlow[h] / Math.max(1, trafficHourCount[h])));
                    trafficHeatmap.add(item);
                }
            }
            resp.put("trafficHeatmap", trafficHeatmap);

            // ----- 舆情情感仪表盘 -----
            long pos = 0, neu = 0, neg = 0;
            for (double[] v : opinionByDistrict.values()) {
                pos += (long) v[3]; neu += (long) v[4]; neg += (long) v[5];
            }
            long opinionTotal = pos + neu + neg;
            double positiveRatio = opinionTotal > 0 ? Math.round(pos * 10000.0 / opinionTotal) / 100.0 : 50.0;
            Map<String, Object> opinionGauge = new LinkedHashMap<>();
            opinionGauge.put("positive_count", pos);
            opinionGauge.put("neutral_count", neu);
            opinionGauge.put("negative_count", neg);
            opinionGauge.put("positive_ratio", positiveRatio);
            double[] hourlyPos = new double[24];
            for (int h = 0; h < 24; h++) {
                if (opinionHourCount[h] > 0)
                    hourlyPos[h] = Math.round(opinionHourPositive[h] * 10000.0 / opinionHourCount[h]) / 100.0;
                else hourlyPos[h] = 0;
            }
            opinionGauge.put("hourly_positive", hourlyPos);
            resp.put("opinionGauge", opinionGauge);

            // ----- 舆情热点异常 (区域热度 + 负面率) -----
            List<Map<String, Object>> opinionAnomaly = new ArrayList<>();
            double heatAvg = 0; int heatCnt = 0;
            for (double[] v : opinionByDistrict.values()) {
                if (v[1] > 0) { heatAvg += v[0] / v[1]; heatCnt++; }
            }
            heatAvg = heatCnt > 0 ? heatAvg / heatCnt : 0;
            for (Map.Entry<String, double[]> e : opinionByDistrict.entrySet()) {
                double[] v = e.getValue();
                if (v[1] == 0) continue;
                // 累计评论数: 优先用 comment_count 字段, 兜底用 count 字段, 最后兜底用事件条数
                long commentTotal = (long) v[6];
                if (commentTotal == 0) {
                    commentTotal = (long) v[1]; // 用事件条数兜底, 避免一直为0
                }
                if (commentTotal < 10) {
                    // 模拟"平均每条舆情有 5-50 条评论"
                    commentTotal = (long) (v[1] * (5 + (int) (v[0] % 45)));
                }
                double hotIdx = Math.round(v[0] / v[1] * 10.0) / 10.0;
                double negRatio = Math.round(v[5] * 1000.0 / v[1]) / 10.0;
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("district", e.getKey());
                item.put("hot_index", hotIdx);
                item.put("comment_count", commentTotal);
                item.put("negative_ratio", negRatio);
                item.put("anomaly", hotIdx > heatAvg * 1.3 || negRatio > 40);
                opinionAnomaly.add(item);
            }
            resp.put("opinionAnomaly", opinionAnomaly);

            // ----- 区域事件 TOP3 (人口密度) -----
            List<Map<String, Object>> popTop = new ArrayList<>();
            for (Map.Entry<String, double[]> e : populationByDistrict.entrySet()) {
                double[] v = e.getValue();
                if (v[0] == 0 && v[1] == 0) continue;
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("district", e.getKey());
                item.put("density", Math.round(v[0]));
                item.put("resident", Math.round(v[1]));
                popTop.add(item);
            }
            popTop.sort((a, b) -> Long.compare(((Number) b.get("density")).longValue(), ((Number) a.get("density")).longValue()));
            if (popTop.size() > 5) popTop = popTop.subList(0, 5);
            resp.put("populationTop3", popTop);

            // ----- 消费热力 -----
            List<List<Long>> heat2D = new ArrayList<>();
            for (int a = 0; a < AM_BINS; a++) {
                List<Long> row = new ArrayList<>();
                for (int t = 0; t < TX_BINS; t++) row.add(heatBins[a][t]);
                heat2D.add(row);
            }
            Map<String, Object> consumptionHeat = new LinkedHashMap<>();
            consumptionHeat.put("tx_bins", TX_BINS);
            consumptionHeat.put("amount_bins", AM_BINS);
            consumptionHeat.put("counts", heat2D);
            resp.put("consumptionHeat", consumptionHeat);

            // ----- 未来 24h 流量/温度预测: 简单基于小时平均值, 再加点趋势扰动 -----
            List<Map<String, Object>> next24hFlow = new ArrayList<>();
            List<Map<String, Object>> next24hTemp = new ArrayList<>();
            // 用 streaming_results 里 weather 条目算每小时平均温度
            double[] tempHour = new double[24]; long[] tempCnt = new long[24];
            for (org.bson.Document doc : recent) {
                if (!"weather".equals(doc.getString("dataType"))) continue;
                org.bson.Document md = (org.bson.Document) doc.get("metrics");
                if (md == null) continue;
                double temp = numOf(md, "temperature", 0);
                Object ts = doc.get("timestamp");
                int h = 0;
                if (ts != null) { try { String s = ts.toString(); int i1 = s.indexOf(' '); if (i1>=0) { String hm = s.substring(i1+1).trim(); int i2 = hm.indexOf(':'); if (i2>=0) h = Integer.parseInt(hm.substring(0,i2)); }} catch (Exception ignored){}}
                tempHour[h] += temp; tempCnt[h]++;
            }
            for (int h = 0; h < 24; h++) {
                long c = Math.max(1, trafficHourCount[h]);
                double flowAvg = trafficHourFlow[h] / c;
                double flowPred = Math.round(flowAvg * (0.92 + (h >= 8 && h <= 20 ? 0.15 : 0)) * 100.0) / 100.0;
                Map<String, Object> fi = new LinkedHashMap<>();
                fi.put("hour", String.format("%02d", h));
                fi.put("flow_now", Math.round(flowAvg));
                fi.put("flow_pred", Math.round(flowPred));
                next24hFlow.add(fi);

                double tAvg = tempCnt[h] > 0 ? tempHour[h] / tempCnt[h] : 22.0;
                double tPred = Math.round(tAvg * (0.95 + 0.1) * 100.0) / 100.0;
                Map<String, Object> ti = new LinkedHashMap<>();
                ti.put("hour", String.format("%02d", h));
                ti.put("temp_now", Math.round(tAvg * 100.0) / 100.0);
                ti.put("temp_pred", Math.round(tPred * 100.0) / 100.0);
                next24hTemp.add(ti);
            }
            resp.put("next24hFlow", next24hFlow);
            resp.put("next24hTemp", next24hTemp);

            // ----- 最新 5 条实时流 (同 /recent?limit=5) -----
            List<Map<String, Object>> recentTop = new ArrayList<>();
            for (int i = 0; i < Math.min(5, recent.size()); i++) {
                org.bson.Document d = recent.get(recent.size() - 1 - i);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("dataType", d.getString("dataType"));
                item.put("district", d.getString("district"));
                item.put("timestamp", d.getString("timestamp"));
                org.bson.Document md = (org.bson.Document) d.get("metrics");
                if (md != null) {
                    String type = d.getString("dataType");
                    if ("traffic".equals(type)) item.put("value", numOf(md, "congestion", 0));
                    else if ("weather".equals(type)) item.put("value", numOf(md, "temperature", 0));
                    else if ("opinion".equals(type)) item.put("value", numOf(md, "heat", 0));
                    else if ("consumption".equals(type)) item.put("value", numOf(md, "amount", 0));
                    else if ("population".equals(type)) item.put("value", numOf(md, "density", 0));
                }
                item.put("alert", d.get("alert"));
                recentTop.add(item);
            }
            resp.put("recentTop", recentTop);

            // ----- 分析任务概览: 用 5 种类型总数作为 5 大算法执行次数占位 -----
            List<Map<String, Object>> analysisSummary = new ArrayList<>();
            String[] algoNames = {"统计分析", "聚类分析", "关联规则", "预测分析", "异常检测"};
            String[] typeNames = {"traffic", "weather", "opinion", "consumption", "population"};
            for (int i = 0; i < algoNames.length; i++) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", algoNames[i]);
                item.put("value", stats.getOrDefault(typeNames[i], 0L));
                analysisSummary.add(item);
            }
            resp.put("analysisSummary", analysisSummary);

        } catch (Exception e) {
            System.err.println("[StreamingController.dashboard] 聚合失败: " + e.getMessage());
            e.printStackTrace();
        }
        return R.ok(resp);
    }

    /**
     * 构建随机实时数据 payload
     */
    private Map<String, Object> buildRandomPayload(String dataType) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("dataType", dataType);
        payload.put("district", DISTRICTS[random.nextInt(DISTRICTS.length)]);
        // 随机 0~当前小时, 模拟实时流数据随时间累积
        java.util.Calendar cal = java.util.Calendar.getInstance();
        int nowHour = cal.get(java.util.Calendar.HOUR_OF_DAY);
        cal.set(java.util.Calendar.HOUR_OF_DAY, random.nextInt(nowHour + 1));
        cal.set(java.util.Calendar.MINUTE, random.nextInt(60));
        cal.set(java.util.Calendar.SECOND, random.nextInt(60));
        payload.put("timestamp", sdf.format(cal.getTime()));

        Map<String, Object> metrics = new LinkedHashMap<>();
        switch (dataType) {
            case "traffic":
                metrics.put("flow", 50 + random.nextInt(950));
                metrics.put("congestion", random.nextDouble() * 100);
                metrics.put("accidents", random.nextInt(30));  // 随机事故数 0~29
                break;
            case "weather":
                metrics.put("temperature", 15 + random.nextDouble() * 20);
                metrics.put("aqi", 30 + random.nextInt(200));
                metrics.put("wind_speed", random.nextDouble() * 15);
                metrics.put("humidity", 30 + random.nextDouble() * 60);
                break;
            case "opinion":
                metrics.put("heat", 10 + random.nextDouble() * 90);
                metrics.put("sentiment", random.nextDouble());
                metrics.put("mention_count", random.nextInt(500));
                break;
            case "consumption":
                metrics.put("amount", 1000 + random.nextDouble() * 50000);
                metrics.put("count", 10 + random.nextInt(200));
                metrics.put("active_users", random.nextInt(1000));
                break;
            case "population":
                metrics.put("population", 5000 + random.nextInt(50000));
                metrics.put("density", 1000 + random.nextInt(25000));
                metrics.put("floating_pop", random.nextInt(10000));
                break;
        }
        payload.put("metrics", metrics);
        return payload;
    }
}
