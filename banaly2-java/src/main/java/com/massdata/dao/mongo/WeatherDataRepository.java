package com.massdata.dao.mongo;

import com.massdata.entity.WeatherData;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 气象数据 MongoDB 仓库 - 对应集合 weather_data
 * 字段: temperature/humidity/windSpeed/windDirection/pressure/precipitation/weather/aqi
 */
@Repository
public interface WeatherDataRepository extends MongoRepository<WeatherData, String> {
    List<WeatherData> findByDateStr(String dateStr);
    List<WeatherData> findByDistrictAndDateStr(String district, String dateStr);
    List<WeatherData> findByCityAndDateStr(String city, String dateStr);
    long countByDateStr(String dateStr);
    void deleteByDateStr(String dateStr);
}
