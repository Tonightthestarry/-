package com.massdata.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.List;
import java.util.Map;

/**
 * 挖掘分析结果实体 - MongoDB存储
 */
@Document(collection = "analysis_results")
public class AnalysisResult {
    @Id
    private String id;
    private String taskId;          // 任务ID
    private String taskType;        // cluster/association/predict/statistic/stream
    private String dataType;        // traffic/weather/opinion/consumption/population
    private String dateStr;         // 分析日期
    private String status;          // running/success/failed
    private Map<String, Object> result;  // 分析结果(JSON)
    private List<Map<String, Object>> details;  // 详细数据(图表用)
    private String createTime;
    private long duration;          // 耗时(ms)

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }
    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }
    public String getDateStr() { return dateStr; }
    public void setDateStr(String dateStr) { this.dateStr = dateStr; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Map<String, Object> getResult() { return result; }
    public void setResult(Map<String, Object> result) { this.result = result; }
    public List<Map<String, Object>> getDetails() { return details; }
    public void setDetails(List<Map<String, Object>> details) { this.details = details; }
    public String getCreateTime() { return createTime; }
    public void setCreateTime(String createTime) { this.createTime = createTime; }
    public long getDuration() { return duration; }
    public void setDuration(long duration) { this.duration = duration; }
}
