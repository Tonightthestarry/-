package com.massdata.dao.mongo;

import com.massdata.entity.PopulationData;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 人口数据 MongoDB 仓库 - 对应集合 population_data
 * 字段: totalPopulation/mobilePopulation/populationDensity/male/female/age分组/education/城乡
 */
@Repository
public interface PopulationDataRepository extends MongoRepository<PopulationData, String> {
    List<PopulationData> findByDateStr(String dateStr);
    List<PopulationData> findByDistrictAndDateStr(String district, String dateStr);
    long countByDateStr(String dateStr);
    void deleteByDateStr(String dateStr);
}
