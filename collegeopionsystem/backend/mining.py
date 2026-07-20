"""数据挖掘分析模块 - 6大算法：统计/聚类/关联/预测/异常/分类"""
import sys
import os
import numpy as np
import pandas as pd
from datetime import datetime, timedelta
from collections import Counter

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from backend.db_mysql import (
    get_events_by_date, get_event_stats, get_event_area_stats,
    get_event_hourly, get_events_last_7days
)


def statistic_analysis(date_str: str):
    """
    统计分析法：基础统计指标 + 描述性分析
    返回: dict {
        "total": 总数,
        "by_type": 按类型统计,
        "by_area": 按区域统计,
        "by_hour": 按小时分布,
        "sentiment_ratio": 情感比例,
        "conclusion": "文字结论"
    }
    """
    events = get_events_by_date(date_str, 2000)
    if not events:
        return {"conclusion": "暂无数据"}

    total = len(events)
    df = pd.DataFrame(events)

    # 按类型统计
    by_type = df['event_type'].value_counts().to_dict()
    top_type = max(by_type, key=by_type.get) if by_type else "未知"

    # 按区域统计
    by_area = df['campus_area'].value_counts().to_dict()
    top_area = max(by_area, key=by_area.get) if by_area else "未知"

    # 情感比例
    sentiment_counts = df['sentiment'].value_counts().to_dict()
    total_s = sum(sentiment_counts.values()) or 1
    pos_ratio = round(sentiment_counts.get('positive', 0) / total_s * 100, 1)
    neg_ratio = round(sentiment_counts.get('negative', 0) / total_s * 100, 1)

    # 热度统计
    avg_heat = round(df['heat_score'].mean(), 1)
    max_heat_event = df.loc[df['heat_score'].idxmax()] if not df.empty else None

    # 异常事件
    anomaly_count = len(df[df['status'] == 'anomaly'])

    conclusion = (
        f"今日共发现 {total} 起校园事件，主要集中在「{top_type}」类({by_type[top_type]}起)。\n"
        f"高发区域：{top_area}({by_area[top_area]}起)。\n"
        f"整体舆情偏{'正面' if pos_ratio > 50 else '中性'}(正面{pos_ratio}%/负面{neg_ratio}%)。\n"
        f"平均热度 {avg_heat}，检测到 {anomaly_count} 起异常事件需关注。"
    )

    return {
        "total": total,
        "by_type": by_type,
        "by_area": by_area,
        "sentiment": {"positive": round(pos_ratio, 1), "neutral": round(100 - pos_ratio - neg_ratio, 1), "negative": round(neg_ratio, 1)},
        "avg_heat": avg_heat,
        "anomaly_count": anomaly_count,
        "top_type": top_type,
        "top_area": top_area,
        "max_heat_title": max_heat_event['title'] if max_heat_event is not None else "",
        "conclusion": conclusion
    }


def cluster_analysis(date_str: str):
    """
    聚类分析：按事件特征分组相似事件
    用简单K-means思想（区域+热度+情感编码）做2D分组
    """
    events = get_events_by_date(date_str, 2000)
    if len(events) < 10:
        return {"conclusion": "数据不足(至少10条)"}

    # 编码
    area_map = {a: i for i, a in enumerate(set(e['campus_area'] for e in events))}
    type_map = {t: i for i, t in enumerate(set(e['event_type'] for e in events))}
    sentiment_map = {"positive": 0, "neutral": 0.5, "negative": 1}

    data = []
    for e in events:
        data.append([
            area_map.get(e['campus_area'], 0),
            type_map.get(e['event_type'], 0),
            sentiment_map.get(e['sentiment'], 0.5),
            e['heat_score'] / 100.0
        ])
    X = np.array(data)

    # 简单聚类：3个簇(根据热度+情感分成高/中/低关注)
    from sklearn.cluster import KMeans
    k = min(3, len(events) // 3)
    kmeans = KMeans(n_clusters=k, random_state=42, n_init=10)
    labels = kmeans.fit_predict(X[:, [2, 3]])

    # 统计各簇
    clusters = {}
    for i, label in enumerate(labels):
        label = int(label)
        if label not in clusters:
            clusters[label] = {"count": 0, "events": [], "avg_heat": 0, "sentiments": []}
        clusters[label]["count"] += 1
        clusters[label]["events"].append(events[i]['title'][:20])
        clusters[label]["avg_heat"] += events[i]['heat_score']
        clusters[label]["sentiments"].append(events[i]['sentiment'])

    result = []
    for label, info in clusters.items():
        info["avg_heat"] = round(info["avg_heat"] / info["count"], 1)
        s = Counter(info["sentiments"])
        info["dominant_sentiment"] = max(s, key=s.get)
        info["cluster_id"] = label
        info["samples"] = info["events"][:5]
        result.append(info)

    conclusion = f"聚类为 {len(result)} 组："
    for r in result:
        conclusion += f"\n组{r['cluster_id']}: {r['count']}条, 平均热度{r['avg_heat']}, 以{r['dominant_sentiment']}为主"

    return {"clusters": result, "conclusion": conclusion}


def association_analysis(date_str: str):
    """
    关联规则分析：分析事件类型与区域的关联关系
    使用简单的支持度+置信度
    """
    events = get_events_by_date(date_str, 2000)
    if len(events) < 20:
        return {"conclusion": "数据不足(至少20条)"}

    # 构建事务：每个(type, area)组合
    transactions = [(e['event_type'], e['campus_area']) for e in events]
    total = len(transactions)

    # 计算支持度和置信度
    rules = []
    from collections import Counter
    ta_counts = Counter(transactions)
    t_counts = Counter([t[0] for t in transactions])
    a_counts = Counter([t[1] for t in transactions])

    for (t, a), cnt in ta_counts.items():
        if cnt >= 3:  # 至少出现3次
            support = round(cnt / total * 100, 1)
            confidence_t = round(cnt / t_counts[t] * 100, 1)
            confidence_a = round(cnt / a_counts[a] * 100, 1)
            rules.append({
                "event_type": t,
                "area": a,
                "count": cnt,
                "support": support,
                "confidence_from_type": confidence_t,
                "confidence_from_area": confidence_a,
                "lift": round(confidence_t / (a_counts[a] / total * 100), 2)
            })

    rules.sort(key=lambda x: x['support'], reverse=True)
    rules = rules[:10]

    conclusion = "强关联规则（TOP 5）：\n"
    for r in rules[:5]:
        conclusion += f"「{r['event_type']}」→{r['area']}(支持度{r['support']}%,置信度{r['confidence_from_type']}%)\n"

    return {"rules": rules, "conclusion": conclusion}


def prediction_analysis(date_str: str):
    """
    预测分析：基于7天历史数据预测未来趋势
    用移动平均 + 线性回归
    """
    last7 = get_events_last_7days()
    if len(last7) < 3:
        return {"conclusion": "历史数据不足(至少3天)"}

    dates = [item['date'] for item in last7]
    counts = [item['count'] for item in last7]
    x = np.arange(len(counts)).reshape(-1, 1)
    y = np.array(counts)

    # 线性回归
    from sklearn.linear_model import LinearRegression
    model = LinearRegression()
    model.fit(x, y)
    # 预测未来3天
    future_x = np.arange(len(counts), len(counts) + 3).reshape(-1, 1)
    predictions = model.predict(future_x)
    predictions = [max(0, round(p)) for p in predictions]

    # 计算 MA
    ma3 = [round(np.mean(counts[max(0, i - 2):i + 1]), 1) for i in range(len(counts))]

    # 斜率
    slope = model.coef_[0]
    trend = "上升" if slope > 1 else ("下降" if slope < -1 else "平稳")

    conclusion = (
        f"基于过去 {len(last7)} 天数据，事件总量呈「{trend}」趋势(日变化{slope:+.1f}起)。\n"
        f"预测未来3天每天事件数分别约: {predictions[0]}、{predictions[1]}、{predictions[2]} 起。\n"
        f"{'⚠️ 建议加强校园管理' if slope > 1 else '✅ 趋势稳定，继续保持'}"
    )

    return {
        "historical": [{"date": d, "count": c, "ma3": m} for d, c, m in zip(dates, counts, ma3)],
        "predictions": [{"day": f"第{i+1}天", "predicted": p} for i, p in enumerate(predictions)],
        "slope": round(slope, 2),
        "trend": trend,
        "conclusion": conclusion
    }


def anomaly_analysis(date_str: str):
    """
    异常检测：用 IQR 方法检测异常高热度事件
    """
    events = get_events_by_date(date_str, 2000)
    if len(events) < 10:
        return {"conclusion": "数据不足"}

    heats = [e['heat_score'] for e in events]
    heats_arr = np.array(heats)

    # IQR
    q1 = np.percentile(heats_arr, 25)
    q3 = np.percentile(heats_arr, 75)
    iqr = q3 - q1
    upper = q3 + 1.5 * iqr
    lower = max(0, q1 - 1.5 * iqr)

    anomalies = [e for e in events if e['heat_score'] > upper]
    # 也包含标记为 anomaly 的事件
    flagged = [e for e in events if e['status'] == 'anomaly' and e not in anomalies]
    all_anomalies = anomalies + flagged

    conclusion = (
        f"IQR 阈值: 上界={upper:.1f}, 下界={lower:.1f}, Q1={q1:.0f}, Q3={q3:.0f}。\n"
        f"检测到 {len(anomalies)} 起热度异常 + {len(flagged)} 起标记异常 = {len(all_anomalies)} 起异常事件。\n"
    )

    if all_anomalies:
        conclusion += "异常事件详情：\n"
        for e in all_anomalies[:5]:
            conclusion += f"  ★ {e['title']}(热度{e['heat_score']})\n"

    return {
        "q1": q1, "q3": q3, "iqr": iqr, "upper": upper, "lower": lower,
        "anomalies": [{"title": e['title'], "heat": e['heat_score'], "type": e['event_type']} for e in all_anomalies[:10]],
        "count": len(all_anomalies),
        "conclusion": conclusion
    }


def classification_analysis(date_str: str):
    """
    分类分析：按紧急程度对事件分级（高/中/低关注）
    规则：热度>100 且 sentiment=negative → 高关注；热度>60 → 中关注；其余 → 低关注
    """
    events = get_events_by_date(date_str, 2000)
    if not events:
        return {"conclusion": "暂无数据"}

    high = [e for e in events if e['heat_score'] > 100 and e['sentiment'] == 'negative']
    medium = [e for e in events if e not in high and e['heat_score'] > 60]
    low = [e for e in events if e not in high and e not in medium]

    conclusion = (
        f"事件分级结果：\n"
        f"🔴 高关注({len(high)}起): 热度>100且负面情绪，需立即处理\n"
        f"🟡 中关注({len(medium)}起): 热度>60，需留意\n"
        f"🟢 低关注({len(low)}起): 一般事件"
    )

    return {
        "high": len(high),
        "medium": len(medium),
        "low": len(low),
        "high_samples": [{"title": e['title'][:30], "heat": e['heat_score']} for e in high[:5]],
        "conclusion": conclusion
    }


def run_all_mining(date_str: str):
    """执行全部6项分析"""
    return {
        "date": date_str,
        "statistic": statistic_analysis(date_str),
        "cluster": cluster_analysis(date_str),
        "association": association_analysis(date_str),
        "prediction": prediction_analysis(date_str),
        "anomaly": anomaly_analysis(date_str),
        "classification": classification_analysis(date_str),
    }
