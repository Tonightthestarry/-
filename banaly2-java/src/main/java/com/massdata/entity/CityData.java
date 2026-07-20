package com.massdata.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.Map;

/**
 * 通用城市数据实体 - 交通/气象/舆情/消费/人口五类数据统一存储于MongoDB
 * 每类数据通过 dataType 字段区分，各自有不同的扩展字段
 *
 * 交通数据: 路段名、车流量、平均速度、拥堵指数、事故数
 * 气象数据: 温度、湿度、风速、降水量、空气质量指数
 * 舆情数据: 关键词、情感值、热度、来源平台、正负面
 * 消费数据: 品类、交易笔数、交易金额、客单价、同比
 * 人口数据: 区域、常驻人口、流动人口、人口密度、年龄分布
 */
@Document(collection = "city_data")
public class CityData {
    @Id
    private String id;
    private String dataType;        // traffic/weather/opinion/consumption/population
    private String city;            // 城市
    private String district;        // 区域/路段
    private String timestamp;       // 数据时间 yyyy-MM-dd HH:mm:ss
    private String dateStr;         // 日期 yyyy-MM-dd
    private Map<String, Object> metrics;  // 各类指标数据(灵活扩展)
    private String source;          // realtime/manual/simulated
    private String createTime;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }
    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    public String getDateStr() { return dateStr; }
    public void setDateStr(String dateStr) { this.dateStr = dateStr; }
    public Map<String, Object> getMetrics() { return metrics; }
    public void setMetrics(Map<String, Object> metrics) { this.metrics = metrics; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getCreateTime() { return createTime; }
    public void setCreateTime(String createTime) { this.createTime = createTime; }
}
