package com.massdata.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 交通数据实体 - 存储城市道路实时路况信息
 * 对应 MongoDB 集合: traffic_data
 * 字段包括路段名称、坐标、实时速度、畅通速度、拥堵指数、道路等级、车流量、事故数等
 */
@Document(collection = "traffic_data")
public class TrafficData {
    @Id
    private String id;
    private String dataId;          // 业务数据编号，如"TRF20251110001"
    private String cityCode;        // 城市编码，如"310000"
    private String cityName;        // 城市名称，如"上海"
    private String roadId;          // 路段编号，如"R00001"
    private String roadName;        // 路段名称，如"南京东路"
    private double lng;             // 经度
    private double lat;             // 纬度
    private double realSpeed;       // 实时速度 km/h
    private double freeSpeed;       // 畅通速度 km/h
    private double congestionIndex; // 拥堵指数 0~1
    private String roadRank;        // 道路等级: 高速/主干道/次干道/支路
    private int trafficFlow;        // 车流量 辆/小时
    private int accidentCount;      // 事故数
    private String district;        // 所属区域
    private long timestamp;         // 数据采集时间戳(毫秒)
    private String dateStr;         // 日期 yyyy-MM-dd
    private String source;          // 数据来源
    private String createTime;      // 记录创建时间

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDataId() { return dataId; }
    public void setDataId(String dataId) { this.dataId = dataId; }
    public String getCityCode() { return cityCode; }
    public void setCityCode(String cityCode) { this.cityCode = cityCode; }
    public String getCityName() { return cityName; }
    public void setCityName(String cityName) { this.cityName = cityName; }
    public String getRoadId() { return roadId; }
    public void setRoadId(String roadId) { this.roadId = roadId; }
    public String getRoadName() { return roadName; }
    public void setRoadName(String roadName) { this.roadName = roadName; }
    public double getLng() { return lng; }
    public void setLng(double lng) { this.lng = lng; }
    public double getLat() { return lat; }
    public void setLat(double lat) { this.lat = lat; }
    public double getRealSpeed() { return realSpeed; }
    public void setRealSpeed(double realSpeed) { this.realSpeed = realSpeed; }
    public double getFreeSpeed() { return freeSpeed; }
    public void setFreeSpeed(double freeSpeed) { this.freeSpeed = freeSpeed; }
    public double getCongestionIndex() { return congestionIndex; }
    public void setCongestionIndex(double congestionIndex) { this.congestionIndex = congestionIndex; }
    public String getRoadRank() { return roadRank; }
    public void setRoadRank(String roadRank) { this.roadRank = roadRank; }
    public int getTrafficFlow() { return trafficFlow; }
    public void setTrafficFlow(int trafficFlow) { this.trafficFlow = trafficFlow; }
    public int getAccidentCount() { return accidentCount; }
    public void setAccidentCount(int accidentCount) { this.accidentCount = accidentCount; }
    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public String getDateStr() { return dateStr; }
    public void setDateStr(String dateStr) { this.dateStr = dateStr; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getCreateTime() { return createTime; }
    public void setCreateTime(String createTime) { this.createTime = createTime; }
}
