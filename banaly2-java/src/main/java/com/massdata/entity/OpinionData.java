package com.massdata.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 舆情数据实体 - 存储来自视频/社交平台的话题热度与情感分析数据
 * 对应 MongoDB 集合: opinion_data
 * 字段包括来源平台、话题信息、UP主、播放/点赞/投币/收藏/分享/评论/弹幕等互动量、
 * 热度评分、情感倾向及情感分数等
 */
@Document(collection = "opinion_data")
public class OpinionData {
    @Id
    private String id;
    private String platform;         // 平台，如"bilibili"
    private String topicId;          // 话题/视频编号，如"BV1xx..."
    private String topicTitle;       // 话题/视频标题
    private String keyword;          // 关键词
    private String upUser;           // UP主/作者
    private long viewCount;          // 播放量
    private long likeCount;          // 点赞数
    private long coinCount;          // 投币数
    private long favoriteCount;      // 收藏数
    private long shareCount;         // 分享数
    private long commentCount;       // 评论数
    private long danmakuCount;       // 弹幕数
    private long heatScore;          // 热度评分
    private String sentiment;        // 情感倾向: positive/negative/neutral
    private double sentimentScore;   // 情感分数 0~1
    private String district;         // 所属区域
    private long timestamp;          // 数据采集时间戳(毫秒)
    private String dateStr;          // 日期 yyyy-MM-dd
    private String source;           // 数据来源 real_bilibili/simulated
    private String createTime;       // 记录创建时间

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }
    public String getTopicId() { return topicId; }
    public void setTopicId(String topicId) { this.topicId = topicId; }
    public String getTopicTitle() { return topicTitle; }
    public void setTopicTitle(String topicTitle) { this.topicTitle = topicTitle; }
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public String getUpUser() { return upUser; }
    public void setUpUser(String upUser) { this.upUser = upUser; }
    public long getViewCount() { return viewCount; }
    public void setViewCount(long viewCount) { this.viewCount = viewCount; }
    public long getLikeCount() { return likeCount; }
    public void setLikeCount(long likeCount) { this.likeCount = likeCount; }
    public long getCoinCount() { return coinCount; }
    public void setCoinCount(long coinCount) { this.coinCount = coinCount; }
    public long getFavoriteCount() { return favoriteCount; }
    public void setFavoriteCount(long favoriteCount) { this.favoriteCount = favoriteCount; }
    public long getShareCount() { return shareCount; }
    public void setShareCount(long shareCount) { this.shareCount = shareCount; }
    public long getCommentCount() { return commentCount; }
    public void setCommentCount(long commentCount) { this.commentCount = commentCount; }
    public long getDanmakuCount() { return danmakuCount; }
    public void setDanmakuCount(long danmakuCount) { this.danmakuCount = danmakuCount; }
    public long getHeatScore() { return heatScore; }
    public void setHeatScore(long heatScore) { this.heatScore = heatScore; }
    public String getSentiment() { return sentiment; }
    public void setSentiment(String sentiment) { this.sentiment = sentiment; }
    public double getSentimentScore() { return sentimentScore; }
    public void setSentimentScore(double sentimentScore) { this.sentimentScore = sentimentScore; }
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
