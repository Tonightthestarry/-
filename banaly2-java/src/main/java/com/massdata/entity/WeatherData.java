package com.massdata.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 气象数据实体 - 存储城市/区域级别的实时气象观测数据
 * 对应 MongoDB 集合: weather_data
 * 字段包括温度、湿度、风速风向、气压、降水量、天气状况、空气质量指数等
 */
@Document(collection = "weather_data")
public class WeatherData {
    @Id
    private String id;
    private String city;            // 城市，如"上海"
    private String district;        // 区域，如"浦东新区"
    private double temperature;     // 温度 ℃
    private int humidity;           // 湿度 %
    private double windSpeed;       // 风速 m/s
    private String windDirection;   // 风向，如"东北风"
    private double pressure;        // 气压 hPa
    private double precipitation;   // 降水量 mm
    private String weather;         // 天气状况，如"多云"
    private int aqi;                // 空气质量指数
    private long timestamp;         // 数据采集时间戳(毫秒)
    private String dateStr;         // 日期 yyyy-MM-dd
    private String source;          // 数据来源 simulated / real_wttr
    private String createTime;      // 记录创建时间

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }
    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }
    public int getHumidity() { return humidity; }
    public void setHumidity(int humidity) { this.humidity = humidity; }
    public double getWindSpeed() { return windSpeed; }
    public void setWindSpeed(double windSpeed) { this.windSpeed = windSpeed; }
    public String getWindDirection() { return windDirection; }
    public void setWindDirection(String windDirection) { this.windDirection = windDirection; }
    public double getPressure() { return pressure; }
    public void setPressure(double pressure) { this.pressure = pressure; }
    public double getPrecipitation() { return precipitation; }
    public void setPrecipitation(double precipitation) { this.precipitation = precipitation; }
    public String getWeather() { return weather; }
    public void setWeather(String weather) { this.weather = weather; }
    public int getAqi() { return aqi; }
    public void setAqi(int aqi) { this.aqi = aqi; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public String getDateStr() { return dateStr; }
    public void setDateStr(String dateStr) { this.dateStr = dateStr; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getCreateTime() { return createTime; }
    public void setCreateTime(String createTime) { this.createTime = createTime; }
}
