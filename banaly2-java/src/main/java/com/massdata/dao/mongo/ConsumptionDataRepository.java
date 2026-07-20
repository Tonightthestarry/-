package com.massdata.dao.mongo;

import com.massdata.entity.ConsumptionData;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 消费数据 MongoDB 仓库 - 对应集合 consumption_data
 * 字段: category/perCapita/ratio/yoyGrowth/totalAmount/transactionCount/avgPrice
 */
@Repository
public interface ConsumptionDataRepository extends MongoRepository<ConsumptionData, String> {
    List<ConsumptionData> findByDateStr(String dateStr);
    List<ConsumptionData> findByCategoryAndDateStr(String category, String dateStr);
    List<ConsumptionData> findByDistrictAndDateStr(String district, String dateStr);
    long countByDateStr(String dateStr);
    void deleteByDateStr(String dateStr);
}
