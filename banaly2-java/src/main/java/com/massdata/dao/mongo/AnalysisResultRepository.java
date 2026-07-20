package com.massdata.dao.mongo;

import com.massdata.entity.AnalysisResult;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

/**
 * 分析结果MongoDB仓库
 */
@Repository
public interface AnalysisResultRepository extends MongoRepository<AnalysisResult, String> {

    // 按任务ID查询
    AnalysisResult findByTaskId(String taskId);

    // 按日期和类型查询
    List<AnalysisResult> findByDateStrAndTaskType(String dateStr, String taskType);

    // 按数据类型查询历史分析结果
    List<AnalysisResult> findByDataTypeOrderByCreateTimeDesc(String dataType);

    // 按日期查询所有分析
    List<AnalysisResult> findByDateStr(String dateStr);

    // 查询最近N条分析记录
    List<AnalysisResult> findTop20ByOrderByCreateTimeDesc();

    // 删除指定日期结果(重新分析时)
    void deleteByDateStr(String dateStr);

    // 按时间范围查询(dateStr 前缀匹配, 适配 YYYY-MM-DD / YYYY-MM / YYYY)
    @Query("{ 'dateStr' : { $regex: ?0 } }")
    List<AnalysisResult> findByDateStrPrefix(String dateStrPrefix);

    // 多条件:按数据类型 + dateStr 前缀
    @Query("{ 'dataType': ?0, 'dateStr' : { $regex: ?1 } }")
    List<AnalysisResult> findByDataTypeAndDateStrPrefix(String dataType, String dateStrPrefix);
}

