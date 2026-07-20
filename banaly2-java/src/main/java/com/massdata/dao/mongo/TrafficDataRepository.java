package com.massdata.dao.mongo;

import com.massdata.entity.TrafficData;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 交通数据 MongoDB 仓库 - 对应集合 traffic_data
 * 字段: roadName/lng/lat/realSpeed/freeSpeed/congestionIndex/roadRank/trafficFlow/accidentCount
 */
@Repository
public interface TrafficDataRepository extends MongoRepository<TrafficData, String> {
    List<TrafficData> findByDateStr(String dateStr);
    List<TrafficData> findByRoadRankAndDateStr(String roadRank, String dateStr);
    List<TrafficData> findByDistrictAndDateStr(String district, String dateStr);
    List<TrafficData> findByCityCodeAndDateStr(String cityCode, String dateStr);
    long countByDateStr(String dateStr);
    void deleteByDateStr(String dateStr);
}
