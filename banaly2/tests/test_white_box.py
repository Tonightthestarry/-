# -*- coding: utf-8 -*-
"""白盒测试 - 测试各个模块的内部函数"""
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

class TestUserAuth(unittest.TestCase):
    """用户认证模块测试"""
    
    def test_verify_user_success(self):
        auth = UserAuth()
        result = auth.verify_user("admin", "123456")
        self.assertTrue(result["success"])
    
    def test_verify_user_failure(self):
        auth = UserAuth()
        result = auth.verify_user("invalid", "invalid")
        self.assertFalse(result["success"])

class TestBilibiliSpider(unittest.TestCase):
    """数据采集模块测试"""
    
    def test_date_validation(self):
        spider = BilibiliSpider()
        self.assertTrue(spider.is_date_valid("2026-06-03"))
        self.assertFalse(spider.is_date_valid("2027-01-01"))

class TestDataCleaner(unittest.TestCase):
    """数据清洗模块测试"""
    
    def test_classify_video(self):
        cleaner = DataCleaner()
        
        video1 = {"播放量": 500}
        video2 = {"播放量": 5000}
        video3 = {"播放量": 50000}
        video4 = {"播放量": 200000}
        
        self.assertEqual(cleaner.classify_video(video1)["type"], "冷门视频")
        self.assertEqual(cleaner.classify_video(video2)["type"], "普通视频")
        self.assertEqual(cleaner.classify_video(video3)["type"], "热门视频")
        self.assertEqual(cleaner.classify_video(video4)["type"], "爆款视频")

class TestDataStorage(unittest.TestCase):
    """数据存储模块测试"""
    
    def test_ensure_tables(self):
        storage = DataStorage()
        result = storage.ensure_tables()
        self.assertTrue(result)
    
    def test_get_video_count(self):
        storage = DataStorage()
        counts = storage.get_video_count()
        self.assertIsInstance(counts, dict)
        self.assertIn("mysql", counts)
        self.assertIn("mongo", counts)

class TestDataMining(unittest.TestCase):
    """数据挖掘模块测试"""
    
    def test_init_tables(self):
        mining = DataMining()
        result = mining.init_analysis_tables()
        self.assertTrue(result)
    
    def test_execute_spark_analysis(self):
        mining = DataMining()
        result = mining.execute_spark_analysis("2026-06-03")
        self.assertTrue(result["success"])

class TestAutoCollector(unittest.TestCase):
    """自动采集模块测试"""
    
    def test_auto_collector_init(self):
        collector = AutoCollector()
        self.assertIsNotNone(collector.spider)
        self.assertIsNotNone(collector.cleaner)
        self.assertIsNotNone(collector.storage)

if __name__ == "__main__":
    unittest.main()
