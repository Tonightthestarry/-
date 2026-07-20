# -*- coding: utf-8 -*-
"""数据清洗模块 - 清洗爬取数据并分类视频（冷门/普通/热门/爆款）"""
import pandas as pd
import re

class DataCleaner:
    def __init__(self):
        self.html_pattern = re.compile(r'<[^>]+>')
    
    def _remove_html_tags(self, text):
        """移除HTML标签"""
        if not text:
            return ""
        return self.html_pattern.sub('', str(text))
    
    def _standardize_num(self, num_str):
        """标准化数字（处理万/亿单位）"""
        if isinstance(num_str, (int, float)):
            return int(num_str)
        if not num_str or num_str == "":
            return 0
        
        num_str = str(num_str).strip()
        if "万" in num_str:
            return int(float(num_str.replace("万", "")) * 10000)
        elif "亿" in num_str:
            return int(float(num_str.replace("亿", "")) * 100000000)
        
        num = re.findall(r"\d+", num_str)
        return int(num[0]) if num else 0
    
    def clean_data(self, video_list):
        """清洗数据：去重、标准化、填充缺失值、移除HTML标签"""
        if not video_list:
            return []
        
        df = pd.DataFrame(video_list)
        
        # 移除HTML标签（标题、简介等文本字段）
        text_columns = ["标题", "简介", "UP主"]
        for col in text_columns:
            if col in df.columns:
                df[col] = df[col].apply(self._remove_html_tags)
        
        num_columns = ["播放量", "点赞数", "投币数", "收藏数", "评论数", "转发数"]
        for col in num_columns:
            if col in df.columns:
                df[col] = df[col].apply(self._standardize_num)
            else:
                df[col] = 0
        
        if "标题" in df.columns and "视频链接" in df.columns:
            df = df.drop_duplicates(subset=["标题", "视频链接"])
        
        if "简介" in df.columns:
            df["简介"] = df["简介"].fillna("No description")
        df = df.fillna(0)
        
        if "播放量" in df.columns:
            df = df[df["播放量"] >= 0]
        
        return df.to_dict("records")
    
    def classify_video(self, video):
        """根据播放量和点赞数分类视频（冷门/普通/热门/爆款）"""
        play_count = video.get("播放量", 0)
        like_count = video.get("点赞数", 0)
        
        if play_count > 100000 or like_count > 10000:
            return {"label": 2, "type": "爆款视频"}
        elif play_count > 10000:
            return {"label": 1, "type": "热门视频"}
        elif play_count > 1000:
            return {"label": 1, "type": "普通视频"}
        else:
            return {"label": 0, "type": "冷门视频"}
    
    def add_cluster_label(self, video_list):
        """为视频添加默认聚类标签（Spark分析会更新）"""
        if not video_list:
            return []
        
        for video in video_list:
            video["聚类标签"] = 1
            video["聚类类别"] = "普通视频"
        
        return video_list
    
    def clean_and_classify(self, video_list):
        """清洗并分类数据（完整流程）"""
        cleaned_data = self.clean_data(video_list)
        classified_data = self.add_cluster_label(cleaned_data)
        return classified_data

if __name__ == "__main__":
    cleaner = DataCleaner()
    
    test_data = [
        {"标题": "测试视频1", "播放量": 500, "点赞数": 10, "投币数": 5, "收藏数": 3, "评论数": 2, "转发数": 1},
        {"标题": "测试视频2", "播放量": 5000, "点赞数": 100, "投币数": 20, "收藏数": 30, "评论数": 15, "转发数": 5},
        {"标题": "测试视频3", "播放量": 50000, "点赞数": 1000, "投币数": 200, "收藏数": 300, "评论数": 150, "转发数": 50},
        {"标题": "测试视频4", "播放量": 200000, "点赞数": 15000, "投币数": 3000, "收藏数": 5000, "评论数": 2000, "转发数": 500}
    ]
    
    result = cleaner.clean_and_classify(test_data)
    for video in result:
        print(f"{video['标题']} -> {video['聚类类别']} (播放量: {video['播放量']}, 点赞数: {video['点赞数']})")