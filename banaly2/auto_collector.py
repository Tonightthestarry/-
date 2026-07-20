# -*- coding: utf-8 -*-
"""自动采集器模块 - 定时采集和手动采集"""
from datetime import datetime, timedelta
import threading
import time
from bilibili_spider import BilibiliSpider
from data_cleaner import DataCleaner
from data_storage import DataStorage

# ===================== 定时配置（在这里调整） =====================
SCHEDULE_CONFIG = {
    "crawl_time": "10:52",           # 定时采集时间（24小时制）
    "default_keyword": "新闻",        # 默认采集关键词
    "default_pages": 5,              # 默认采集页数
    "target_day": "yesterday"        # 采集哪天的数据："yesterday"=昨天, "today"=今天
}

class AutoCollector:
    def __init__(self):
        self.spider = BilibiliSpider()
        self.cleaner = DataCleaner()
        self.storage = DataStorage()
        self.scheduler_running = False
        self.scheduler_thread = None
    
    def auto_crawl(self, keyword=SCHEDULE_CONFIG["default_keyword"], 
                   max_page=SCHEDULE_CONFIG["default_pages"]):
        """自动采集（采集前一天的数据，默认关键词"新闻"）"""
        yesterday = (datetime.now() - timedelta(days=1)).strftime("%Y-%m-%d")
        return self.crawl(keyword, yesterday, max_page)
    
    def manual_crawl(self, keyword, target_date, max_page=3):
        """手动采集（指定关键词、日期和页数）"""
        return self.crawl(keyword, target_date, max_page)
    
    def crawl(self, keyword, target_date, max_page):
        """执行采集（完整流程）"""
        print(f"开始采集: 关键词={keyword}, 日期={target_date}, 页数={max_page}")
        
        # 1. 验证日期（不能是未来日期）
        if not self.spider.is_date_valid(target_date):
            return {"success": False, "message": f"日期 {target_date} 无效或为未来日期"}
        
        # 2. 爬取数据
        raw_data = self.spider.crawl(keyword, target_date, max_page)
        
        if not raw_data:
            return {"success": False, "message": "未采集到数据"}
        
        # 3. 清洗和分类（添加聚类标签）
        cleaned_data = self.cleaner.clean_and_classify(raw_data)
        
        # 4. 保存数据到MySQL和MongoDB
        save_result = self.storage.save_videos(cleaned_data)
        
        if save_result["success"]:
            return {"success": True, "message": f"采集完成！共{len(cleaned_data)}条", "count": len(cleaned_data)}
        else:
            return {"success": False, "message": save_result.get("message", "保存失败")}
    
    def get_storage_stats(self):
        """获取存储统计"""
        return self.storage.get_video_count()
    
    def _scheduled_crawl(self):
        """定时执行的采集任务"""
        print(f"[{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}] 定时采集任务启动...")
        
        # 确定采集日期
        if SCHEDULE_CONFIG["target_day"] == "yesterday":
            target_date = (datetime.now() - timedelta(days=1)).strftime("%Y-%m-%d")
        else:  # today
            target_date = datetime.now().strftime("%Y-%m-%d")
        
        # 执行采集
        result = self.crawl(
            SCHEDULE_CONFIG["default_keyword"],
            target_date,
            SCHEDULE_CONFIG["default_pages"]
        )
        
        if result["success"]:
            print(f"[{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}] 定时采集成功: {result['message']}")
        else:
            print(f"[{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}] 定时采集失败: {result['message']}")
    
    def _scheduler_loop(self):
        """调度器主循环"""
        print(f"定时调度器启动，每天 {SCHEDULE_CONFIG['crawl_time']} 执行采集...")
        
        while self.scheduler_running:
            now = datetime.now()
            current_time = now.strftime("%H:%M")
            
            # 检查是否到达定时时间
            if current_time == SCHEDULE_CONFIG["crawl_time"]:
                # 执行采集
                self._scheduled_crawl()
                # 等待60秒，避免同一分钟内多次执行
                time.sleep(60)
            
            # 每秒检查一次
            time.sleep(1)
    
    def start_scheduler(self):
        """启动定时调度器（后台线程）"""
        if self.scheduler_running:
            print("调度器已在运行中")
            return
        
        self.scheduler_running = True
        self.scheduler_thread = threading.Thread(target=self._scheduler_loop, daemon=True)
        self.scheduler_thread.start()
        print(f"定时调度器已启动，每天 {SCHEDULE_CONFIG['crawl_time']} 自动采集")
    
    def stop_scheduler(self):
        """停止定时调度器"""
        self.scheduler_running = False
        if self.scheduler_thread:
            self.scheduler_thread.join(timeout=2)
        print("定时调度器已停止")

if __name__ == "__main__":
    collector = AutoCollector()
    
    # 测试自动采集
    print("=== 测试自动采集 ===")
    result = collector.auto_crawl(keyword="新闻", max_page=1)
    print(result)
    
    # 获取统计
    stats = collector.get_storage_stats()
    print(f"\n当前数据统计: MySQL={stats['mysql']}条, MongoDB={stats['mongo']}条")