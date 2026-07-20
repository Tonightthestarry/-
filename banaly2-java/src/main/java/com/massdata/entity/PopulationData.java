package com.massdata.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 人口数据实体 - 存储城市/区域人口结构与分布信息
 * 对应 MongoDB 集合: population_data
 * 字段包括总人口、流动人口、人口密度、性别比例、年龄分段、学历分布、城乡人口、家庭户数等
 */
@Document(collection = "population_data")
public class PopulationData {
    @Id
    private String id;
    private String district;             // 所属区域
    private long totalPopulation;        // 总人口
    private long mobilePopulation;       // 流动人口
    private int populationDensity;       // 人口密度 人/km²
    private long male;                   // 男性人口
    private long female;                 // 女性人口
    private long age014;                 // 0-14岁人口
    private long age1559;                // 15-59岁人口
    private long age60Plus;              // 60岁及以上人口
    private long educationCollege;       // 大专以上学历人口
    private long educationHighSchool;    // 高中学历人口
    private long urban;                  // 城镇人口
    private long rural;                  // 乡村人口
    private long households;             // 家庭户数
    private double avgHouseholdSize;     // 平均户规模
    private long timestamp;              // 数据采集时间戳(毫秒)
    private String dateStr;              // 日期 yyyy-MM-dd
    private String source;               // 数据来源
    private String createTime;           // 记录创建时间

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }
    public long getTotalPopulation() { return totalPopulation; }
    public void setTotalPopulation(long totalPopulation) { this.totalPopulation = totalPopulation; }
    public long getMobilePopulation() { return mobilePopulation; }
    public void setMobilePopulation(long mobilePopulation) { this.mobilePopulation = mobilePopulation; }
    public int getPopulationDensity() { return populationDensity; }
    public void setPopulationDensity(int populationDensity) { this.populationDensity = populationDensity; }
    public long getMale() { return male; }
    public void setMale(long male) { this.male = male; }
    public long getFemale() { return female; }
    public void setFemale(long female) { this.female = female; }
    public long getAge014() { return age014; }
    public void setAge014(long age014) { this.age014 = age014; }
    public long getAge1559() { return age1559; }
    public void setAge1559(long age1559) { this.age1559 = age1559; }
    public long getAge60Plus() { return age60Plus; }
    public void setAge60Plus(long age60Plus) { this.age60Plus = age60Plus; }
    public long getEducationCollege() { return educationCollege; }
    public void setEducationCollege(long educationCollege) { this.educationCollege = educationCollege; }
    public long getEducationHighSchool() { return educationHighSchool; }
    public void setEducationHighSchool(long educationHighSchool) { this.educationHighSchool = educationHighSchool; }
    public long getUrban() { return urban; }
    public void setUrban(long urban) { this.urban = urban; }
    public long getRural() { return rural; }
    public void setRural(long rural) { this.rural = rural; }
    public long getHouseholds() { return households; }
    public void setHouseholds(long households) { this.households = households; }
    public double getAvgHouseholdSize() { return avgHouseholdSize; }
    public void setAvgHouseholdSize(double avgHouseholdSize) { this.avgHouseholdSize = avgHouseholdSize; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public String getDateStr() { return dateStr; }
    public void setDateStr(String dateStr) { this.dateStr = dateStr; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getCreateTime() { return createTime; }
    public void setCreateTime(String createTime) { this.createTime = createTime; }
}
