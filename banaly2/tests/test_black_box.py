# -*- coding: utf-8 -*-
"""黑盒测试 - 模拟用户行为测试整个应用"""
import unittest
import sys
import os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from user_auth import UserAuth
from bilibili_spider import BilibiliSpider
from data_cleaner import DataCleaner
from data_storage import DataStorage
from data_mining import DataMining
from auto_collector import AutoCollector

class TestFullWorkflow(unittest.TestCase):
    """完整工作流测试"""
    
    def test_login_workflow(self):
        """测试登录流程"""
        print("=== 测试1: 用户登录 ===")
        auth = UserAuth()
        result = auth.verify_user("admin", "123456")
        self.assertTrue(result["success"], "登录失败")
        print("✅ 登录成功")
    
    def test_spider_workflow(self):
        """测试爬虫功能"""
        print("\n=== 测试2: 数据采集 ===")
        spider = BilibiliSpider()
        videos = spider.crawl(keyword="新闻", target_date="2026-06-03", max_page=1)
        print(f"✅ 采集成功，共 {len(videos)} 条数据")
    
    def test_cleaner_workflow(self):
        """测试数据清洗"""
        print("\n=== 测试3: 数据清洗 ===")
        cleaner = DataCleaner()
        test_data = [{"标题": "test", "播放量": "1000", "点赞数": "50"}]
        cleaned = cleaner.clean_data(test_data)
        self.assertEqual(len(cleaned), 1)
        print("✅ 数据清洗成功")
    
    def test_storage_workflow(self):
        """测试数据存储"""
        print("\n=== 测试4: 数据存储 ===")
        storage = DataStorage()
        storage.ensure_tables()
        counts = storage.get_video_count()
        print(f"✅ 存储成功 - MySQL: {counts['mysql']} 条")
    
    def test_mining_workflow(self):
        """测试Spark分析"""
        print("\n=== 测试5: Spark分析 ===")
        mining = DataMining()
        mining.init_analysis_tables()
        result = mining.execute_spark_analysis("2026-06-03")
        self.assertTrue(result["success"], "Spark分析失败")
        print("✅ Spark分析成功")
    
    def test_collector_workflow(self):
        """测试自动采集器"""
        print("\n=== 测试6: 自动采集器 ===")
        collector = AutoCollector()
        result = collector.auto_crawl(keyword="新闻", max_page=1)
        print(f"✅ 自动采集完成: {result['message']}")
    
    def test_video_classification(self):
        """测试视频分类"""
        print("\n=== 测试7: 视频分类 ===")
        cleaner = DataCleaner()
        
        video1 = {"播放量": 500, "点赞数": 10}
        video2 = {"播放量": 5000, "点赞数": 100}
        video3 = {"播放量": 50000, "点赞数": 1000}
        video4 = {"播放量": 200000, "点赞数": 15000}
        
        self.assertEqual(cleaner.classify_video(video1)["type"], "冷门视频")
        self.assertEqual(cleaner.classify_video(video2)["type"], "普通视频")
        self.assertEqual(cleaner.classify_video(video3)["type"], "热门视频")
        self.assertEqual(cleaner.classify_video(video4)["type"], "爆款视频")
        print("✅ 视频分类测试通过")
    
    def test_date_validation(self):
        """测试日期验证"""
        print("\n=== 测试8: 日期验证 ===")
        spider = BilibiliSpider()
        self.assertTrue(spider.is_date_valid("2026-06-03"))
        self.assertFalse(spider.is_date_valid("2027-01-01"))
        print("✅ 日期验证测试通过")

if __name__ == "__main__":
    print("=" * 50)
    print("黑盒测试 - B站数据分析平台")
    print("=" * 50)
    
    suite = unittest.TestLoader().loadTestsFromTestCase(TestFullWorkflow)
    runner = unittest.TextTestRunner(verbosity=2)
    runner.run(suite)
