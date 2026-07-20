"""MongoDB 连接模块 - 实时事件流存储"""
from pymongo import MongoClient
from datetime import datetime, timedelta
import os
import sys

MONGO_URI = "mongodb://localhost:27017"
MONGO_DB = "campus_monitor"
MONGO_STREAM_COLLECTION = "event_stream"  # 实时事件流
MONGO_ANALYSIS_COLLECTION = "analysis_results"  # 分析结果

_client = None
_db = None


def get_db():
    """获取 MongoDB 数据库实例"""
    global _client, _db
    if _db is None:
        _client = MongoClient(MONGO_URI)
        _db = _client[MONGO_DB]
        # 创建索引
        _db[MONGO_STREAM_COLLECTION].create_index("timestamp")
        _db[MONGO_STREAM_COLLECTION].create_index("event_type")
        _db[MONGO_STREAM_COLLECTION].create_index("campus_area")
    return _db


def push_event_to_stream(event: dict):
    """推送一条事件到实时流"""
    db = get_db()
    event["timestamp"] = datetime.now()
    event["_stream_time"] = datetime.now()
    db[MONGO_STREAM_COLLECTION].insert_one(event)
    # 自动清理24小时前的旧数据
    cutoff = datetime.now() - timedelta(hours=24)
    db[MONGO_STREAM_COLLECTION].delete_many({"timestamp": {"$lt": cutoff}})


def get_recent_stream(limit=100):
    """获取最近N条实时流事件"""
    db = get_db()
    return list(db[MONGO_STREAM_COLLECTION].find(
        {}, {"_id": 0}
    ).sort("timestamp", -1).limit(limit))


def get_stream_stats():
    """实时流统计"""
    db = get_db()
    total = db[MONGO_STREAM_COLLECTION].count_documents({})
    # 按类型统计
    pipeline = [
        {"$group": {"_id": "$event_type", "count": {"$sum": 1}}},
        {"$sort": {"count": -1}}
    ]
    by_type = list(db[MONGO_STREAM_COLLECTION].aggregate(pipeline))
    return {
        "total_in_stream": total,
        "by_type": [{"event_type": r["_id"], "count": r["count"]} for r in by_type]
    }


def save_analysis_result(result: dict):
    """保存分析结果到 MongoDB"""
    db = get_db()
    result["create_time"] = datetime.now()
    db[MONGO_ANALYSIS_COLLECTION].insert_one(result)


def get_analysis_results(limit=20):
    """获取最近分析结果"""
    db = get_db()
    return list(db[MONGO_ANALYSIS_COLLECTION].find(
        {}, {"_id": 0}
    ).sort("create_time", -1).limit(limit))


def get_mongo_status():
    """MongoDB 状态检查"""
    try:
        db = get_db()
        db.command("ping")
        return {"connected": True, "db": MONGO_DB}
    except Exception as e:
        return {"connected": False, "error": str(e)}
