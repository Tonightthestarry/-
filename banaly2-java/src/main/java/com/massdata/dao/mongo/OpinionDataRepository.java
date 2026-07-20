package com.massdata.dao.mongo;

import com.massdata.entity.OpinionData;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 舆情数据 MongoDB 仓库 - 对应集合 opinion_data
 * 字段: platform/topicId/topicTitle/keyword/upUser/view/like/coin/favorite/share/comment/heat/sentiment
 */
@Repository
public interface OpinionDataRepository extends MongoRepository<OpinionData, String> {
    List<OpinionData> findByDateStr(String dateStr);
    List<OpinionData> findByKeywordAndDateStr(String keyword, String dateStr);
    List<OpinionData> findBySentimentAndDateStr(String sentiment, String dateStr);
    List<OpinionData> findByDistrictAndDateStr(String district, String dateStr);
    long countByDateStr(String dateStr);
    void deleteByDateStr(String dateStr);
}
