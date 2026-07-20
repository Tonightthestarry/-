package com.massdata.dao.mongo;

import com.massdata.entity.CityData;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

/**
 * 城市数据MongoDB仓库 - 统一管理五类数据
 */
@Repository
public interface CityDataRepository extends MongoRepository<CityData, String> {

    // 按数据类型和日期查询
    List<CityData> findByDataTypeAndDateStr(String dataType, String dateStr);

    // 按数据类型批量查询
    @Query("{'dataType': ?0, 'dateStr': ?1}")
    List<CityData> findByTypeAndDate(String dataType, String dateStr);

    // 按城市+数据类型查询
    List<CityData> findByCityAndDataType(String city, String dataType);

    // 按日期范围统计各数据类型数量
    @Query(value = "{'dateStr': {'$gte': ?0, '$lte': ?1}}", count = true)
    long countByDateRange(String startDate, String endDate);

    // 查询最近N条(用于实时展示)
    List<CityData> findByDataTypeOrderByTimestampDesc(String dataType, Pageable pageable);

    // 按区域分组统计(聚合在Service层实现)
    List<CityData> findByDistrictAndDataType(String district, String dataType);

    // 删除指定日期数据(重新采集时清理)
    void deleteByDateStr(String dateStr);

    // 按数据类型统计数量
    long countByDataType(String dataType);

    // 按数据类型查所有
    List<CityData> findByDataType(String dataType);

    // 取一条指定类型的样本
    CityData findFirstByDataType(String dataType);
}
