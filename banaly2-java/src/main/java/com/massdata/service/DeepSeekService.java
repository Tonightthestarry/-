package com.massdata.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * DeepSeek AI 大模型服务 - 根据挖掘分析结果生成城市管理业务建议
 */
@Service
public class DeepSeekService {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekService.class);
    private static final String API_KEY = "sk-9ff15fa8c0c1484baf4c605b393888c3";
    private static final String API_URL = "https://api.deepseek.com/v1/chat/completions";
    private static final String MODEL = "deepseek-chat";
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 根据挖掘分析结果调用DeepSeek生成业务建议
     */
    public String generateConclusion(String dataType, String taskType, Map<String, Object> analysisResult) {
        String prompt = buildPrompt(dataType, taskType, analysisResult);
        try {
            Map<String, Object> reqBody = new LinkedHashMap<>();
            reqBody.put("model", MODEL);
            reqBody.put("temperature", 0.7);
            reqBody.put("max_tokens", 800);
            List<Map<String, String>> messages = new ArrayList<>();

            Map<String, String> sysMsg = new LinkedHashMap<>();
            sysMsg.put("role", "system");
            sysMsg.put("content", "你是一个城市管理数据分析专家。根据提供的城市数据挖掘结果,给出3-5条具体可执行的业务建议。" +
                    "要求: 每条建议用序号(1.2.3.)列出,每条约40字,包含具体行动方案。语气专业简洁。不要有多余的客套话。");
            messages.add(sysMsg);

            Map<String, String> userMsg = new LinkedHashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", prompt);
            messages.add(userMsg);

            reqBody.put("messages", messages);

            String json = mapper.writeValueAsString(reqBody);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + API_KEY)
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();
            Map<String, Object> respMap = mapper.readValue(body, Map.class);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) respMap.get("choices");
            if (choices != null && !choices.isEmpty()) {
                Map<String, Object> choice = choices.get(0);
                Map<String, Object> message = (Map<String, Object>) choice.get("message");
                return (String) message.get("content");
            }
            return null;
        } catch (Exception e) {
            log.warn("DeepSeek API 调用失败: {}", e.getMessage());
            return buildFallbackConclusion(dataType, taskType, analysisResult);
        }
    }

    /**
     * 构建给DeepSeek的提示词
     */
    private String buildPrompt(String dataType, String taskType, Map<String, Object> result) {
        String typeCn = getTypeCn(dataType);
        String taskCn = getTaskCn(taskType);
        StringBuilder sb = new StringBuilder();
        sb.append("城市数据类型:").append(typeCn).append("\n");
        sb.append("分析方法:").append(taskCn).append("\n");
        sb.append("分析结果摘要:\n");
        if (result != null) {
            // 只取前800字核心结果
            String summary = result.toString();
            if (summary.length() > 800) summary = summary.substring(0, 800);
            sb.append(summary);
        }
        sb.append("\n请根据以上数据,为城市管理部门给出3-5条具体可执行的业务建议(每条以序号开头)。");
        return sb.toString();
    }

    /**
     * AI不可用时用预设规则生成建议
     */
    private String buildFallbackConclusion(String dataType, String taskType, Map<String, Object> result) {
        List<String> tips = new ArrayList<>();
        switch (dataType) {
            case "traffic":
                tips.add("1. 对拥堵指数高于0.7的重点路段(如黄浦区、静安区)增派交警疏导,启动信号灯联动优化。");
                tips.add("2. 高峰时段(7:00-9:00,17:00-19:00)启用潮汐车道,并在关键路口增加临时引导标识。");
                tips.add("3. 事故多发区域增设电子警示牌,通过交通App向市民推送绕行建议,降低二次拥堵风险。");
                tips.add("4. 结合历史趋势预测下周拥堵高峰,提前发布出行预警,协调公交加密班次。");
                break;
            case "weather":
                tips.add("1. 当AQI指数超过150时,启动重污染天气应急预案,建议中小学减少户外活动。");
                tips.add("2. 温度骤变(24小时内温差超过5℃)时向公众推送穿衣指数提醒,重点关注老人儿童防护。");
                tips.add("3. 暴雨预警期间提前排查低洼路段排水设施,协调环卫部门加强巡查,防止内涝。");
                tips.add("4. 连续高温日启动户外作业高温补贴机制,设置临时避暑站,保障户外劳动者安全。");
                break;
            case "opinion":
                tips.add("1. 做好权威发声,召开新闻发布会回应市民关切,压缩谣言传播空间,引导正向舆论场。");
                tips.add("2. 加强舆情监测值班,对突发负面信息30分钟内启动应急响应,主动发布事实核查结果。");
                tips.add("3. 通过新媒体账号矩阵(微博、微信、抖音)发布正向内容,稀释负面声量,提升正面曝光。");
                tips.add("4. 建立舆情分级预警机制:热度>5000启动黄色预警,>10000启动橙色预警,>20000启动红色预警。");
                break;
            case "consumption":
                tips.add("1. 对客单价持续下滑的商圈(降幅超10%)建议发放定点消费券,联合商家推出促销活动刺激需求。");
                tips.add("2. 交易笔数激增时提前调配物流运力,协调快递公司增派人员,防止爆仓,保障配送时效。");
                tips.add("3. 夜间消费活跃区域延长公共交通运营时间,增设夜间经济示范区,提升消费便利度。");
                tips.add("4. 关注线上消费占比变化,引导传统商场数字化转型,搭建线上线下融合的新零售体系。");
                break;
            case "population":
                tips.add("1. 人口密度超过阈值(如每平方公里超过1万人)的区域启动分流方案,增派安保人员维护秩序。");
                tips.add("2. 流动人口占比超过30%时加强社区网格化管理,做好出租屋登记排查,保障社区安全。");
                tips.add("3. 节假日期间重点区域加密公交地铁班次,增设临时停车场,引导错峰出行,缓解交通压力。");
                tips.add("4. 结合人口年龄分布优化公共设施配置:老龄社区增设医疗服务点,年轻社区增加托幼资源。");
                break;
        }
        // 根据任务类型追加特定建议
        if ("anomaly".equals(taskType)) {
            tips.add("5. 对检测到的异常数据点进行人工复核,确认是否数据采集偏差或真实异常事件,避免误报。");
        } else if ("predict".equals(taskType)) {
            tips.add("5. 将预测模型结果同步至各职能部门,提前准备应急预案,做好数据驱动决策的闭环管理。");
        }
        return String.join("\n", tips);
    }

    private String getTypeCn(String t) {
        switch (t) {
            case "traffic": return "交通";
            case "weather": return "气象";
            case "opinion": return "舆情";
            case "consumption": return "消费";
            case "population": return "人口";
            default: return t;
        }
    }

    private String getTaskCn(String t) {
        switch (t) {
            case "statistic": return "统计分析";
            case "cluster": return "聚类分析(K-Means)";
            case "association": return "关联规则(Apriori)";
            case "predict": return "预测分析(线性回归)";
            case "anomaly": return "异常检测(IQR)";
            case "classify": return "随机森林分类";
            default: return t;
        }
    }
}
