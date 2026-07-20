"""可视化图表生成模块 - 为 Streamlit 页面提供图表数据"""
import sys
import os
from datetime import datetime, timedelta
import numpy as np
import pandas as pd

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from backend.db_mysql import (
    get_events_by_date, get_event_stats, get_event_area_stats,
    get_event_hourly, get_events_last_7days, get_event_count
)
from backend.db_mongo import get_recent_stream, get_stream_stats


def get_overview_cards():
    """概览卡片数据"""
    today = datetime.now().strftime("%Y-%m-%d")
    events = get_events_by_date(today, 2000)
    df = pd.DataFrame(events) if events else pd.DataFrame()

    anomaly_count = len(df[df['status'] == 'anomaly']) if not df.empty else 0
    total_all = get_event_count()

    return [
        {"title": "今日事件", "value": len(events), "icon": "📋"},
        {"title": "异常事件", "value": anomaly_count, "icon": "⚠️"},
        {"title": "历史总量", "value": total_all, "icon": "📦"},
        {"title": "实时流中", "value": get_stream_stats().get("total_in_stream", 0), "icon": "🔄"},
    ]


def get_event_type_pie(date_str=None):
    """事件类型饼图数据"""
    if date_str is None:
        date_str = datetime.now().strftime("%Y-%m-%d")
    stats = get_event_stats(date_str)
    return {
        "labels": [s['event_type'] for s in stats],
        "values": [s['count'] for s in stats],
        "conclusion": f"今日以「{stats[0]['event_type']}」类事件最多({stats[0]['count']}起)" if stats else "暂无数据"
    }


def get_campus_area_heatmap(date_str=None):
    """校园区域热度分布"""
    if date_str is None:
        date_str = datetime.now().strftime("%Y-%m-%d")
    stats = get_event_area_stats(date_str)
    return {
        "labels": [s['area'] for s in stats],
        "values": [s['count'] for s in stats],
        "conclusion": f"「{stats[0]['area']}」是事件高发区({stats[0]['count']}起)" if stats else "暂无数据"
    }


def get_hourly_distribution(date_str=None):
    """24h事件分布"""
    if date_str is None:
        date_str = datetime.now().strftime("%Y-%m-%d")
    hourly = get_event_hourly(date_str)
    return {
        "hours": list(range(24)),
        "values": [hourly[h] for h in range(24)],
        "peak_hour": max(hourly, key=hourly.get) if hourly else 0,
        "conclusion": ""
    }


def get_sentiment_gauge(date_str=None):
    """情感仪表盘"""
    if date_str is None:
        date_str = datetime.now().strftime("%Y-%m-%d")
    events = get_events_by_date(date_str, 2000)
    if not events:
        return {"positive": 0, "neutral": 0, "negative": 0, "conclusion": "暂无数据"}

    sentiments = [e['sentiment'] for e in events]
    total = len(sentiments)
    pos = round(sum(1 for s in sentiments if s == 'positive') / total * 100, 1)
    neg = round(sum(1 for s in sentiments if s == 'negative') / total * 100, 1)
    neu = round(100 - pos - neg, 1)

    conclusion = f"整体舆情偏{'正面' if pos > 50 else ('负面' if neg > 40 else '中性')} (正面{pos}%)"
    return {"positive": pos, "neutral": neu, "negative": neg, "conclusion": conclusion}


def get_prediction_chart():
    """7天预测趋势"""
    last7 = get_events_last_7days()
    historical = []
    dates = [item['date'] for item in last7]
    values = [item['count'] for item in last7]

    # 移动平均
    ma = []
    for i in range(len(values)):
        start = max(0, i - 2)
        ma.append(round(np.mean(values[start:i + 1]), 1))

    # 线预测未来3天
    from sklearn.linear_model import LinearRegression
    if len(values) >= 3:
        x = np.arange(len(values)).reshape(-1, 1)
        model = LinearRegression()
        model.fit(x, np.array(values))
        future_x = np.arange(len(values), len(values) + 3).reshape(-1, 1)
        preds = [max(0, round(p)) for p in model.predict(future_x)]
        slope = model.coef_[0]
    else:
        preds = []
        slope = 0

    return {
        "dates": dates,
        "values": values,
        "ma": ma,
        "prediction_dates": [f"预测+{i+1}天" for i in range(3)],
        "predictions": preds,
        "trend": "上升" if slope > 1 else ("下降" if slope < -1 else "平稳"),
        "conclusion": f"趋势: {('上升' if slope > 1 else ('下降' if slope < -1 else '平稳'))}, 预测未来3天每日约{preds[0] if preds else '?'}起"
    }
