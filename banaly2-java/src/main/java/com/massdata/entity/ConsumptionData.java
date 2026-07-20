package com.massdata.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 消费数据实体 - 存储城市/区域不同品类的消费交易数据
 * 对应 MongoDB 集合: consumption_data
 * 字段包括消费品类、人均消费、占比、同比增速、总交易额、交易笔数、客单价等
 */
@Document(collection = "consumption_data")
public class ConsumptionData {
    @Id
    private String id;
    private String category;        // 消费品类，如"食品烟酒"
    private double perCapita;       // 人均消费(元)
    private double ratio;           // 占比(%)
    private double yoyGrowth;       // 同比增速(%)
    private double totalAmount;     // 当日总交易额(元)
    private int transactionCount;   // 交易笔数
    private double avgPrice;        // 客单价(元)
    private String district;        // 所属区域
    private long timestamp;         // 数据采集时间戳(毫秒)
    private String dateStr;         // 日期 yyyy-MM-dd
    private String source;          // 数据来源
    private String createTime;      // 记录创建时间

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public double getPerCapita() { return perCapita; }
    public void setPerCapita(double perCapita) { this.perCapita = perCapita; }
    public double getRatio() { return ratio; }
    public void setRatio(double ratio) { this.ratio = ratio; }
    public double getYoyGrowth() { return yoyGrowth; }
    public void setYoyGrowth(double yoyGrowth) { this.yoyGrowth = yoyGrowth; }
    public double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(double totalAmount) { this.totalAmount = totalAmount; }
    public int getTransactionCount() { return transactionCount; }
    public void setTransactionCount(int transactionCount) { this.transactionCount = transactionCount; }
    public double getAvgPrice() { return avgPrice; }
    public void setAvgPrice(double avgPrice) { this.avgPrice = avgPrice; }
    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public String getDateStr() { return dateStr; }
    public void setDateStr(String dateStr) { this.dateStr = dateStr; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getCreateTime() { return createTime; }
    public void setCreateTime(String createTime) { this.createTime = createTime; }
}
