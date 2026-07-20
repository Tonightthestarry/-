"""URL 网页检测模块 - 检测网页内容是否包含敏感信息"""
import requests
import re
import json
import os
import sys
from datetime import datetime
from bs4 import BeautifulSoup

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from backend.db_mysql import insert_scan_log, get_scan_logs

SENSITIVE_WORDS_PATH = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "data", "sensitive_words.json"
)


def _load_sensitive_words():
    """加载敏感词库"""
    if not os.path.exists(SENSITIVE_WORDS_PATH):
        return {}
    with open(SENSITIVE_WORDS_PATH, "r", encoding="utf-8") as f:
        return json.load(f)


def scan_url(url: str, timeout: int = 10):
    """
    检测单个URL网页
    
    返回: dict {
        "url": str,
        "status": "success/failed/timeout",
        "title": str,
        "content_preview": str,
        "risk_level": "safe/low/medium/high/critical",
        "sensitive_words": list,
        "total_sensitive": int
    }
    """
    result = {
        "url": url,
        "status": "failed",
        "title": "",
        "content_preview": "",
        "risk_level": "safe",
        "sensitive_words": [],
        "total_sensitive": 0,
        "scan_time": datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    }

    try:
        headers = {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        }
        resp = requests.get(url, headers=headers, timeout=timeout, allow_redirects=True)
        resp.encoding = resp.apparent_encoding or "utf-8"
        result["status"] = "success"

        soup = BeautifulSoup(resp.text, "html.parser")
        result["title"] = soup.title.string.strip() if soup.title else ""

        # 提取正文文本
        for tag in soup(["script", "style", "meta", "link", "nav", "footer"]):
            tag.decompose()
        text = soup.get_text(separator=" ", strip=True)
        result["content_preview"] = text[:500]

        # 敏感词检测
        sensitive_words = _load_sensitive_words()
        all_words = []
        found_words = []
        for category, words in sensitive_words.items():
            all_words.extend(words)
        for word in all_words:
            if word in text:
                found_words.append(word)

        result["sensitive_words"] = list(set(found_words))
        result["total_sensitive"] = len(result["sensitive_words"])

        # 风险评级
        if result["total_sensitive"] >= 10:
            result["risk_level"] = "critical"
        elif result["total_sensitive"] >= 6:
            result["risk_level"] = "high"
        elif result["total_sensitive"] >= 3:
            result["risk_level"] = "medium"
        elif result["total_sensitive"] >= 1:
            result["risk_level"] = "low"
        else:
            result["risk_level"] = "safe"

    except requests.Timeout:
        result["status"] = "timeout"
    except Exception as e:
        result["status"] = "failed"
        result["content_preview"] = str(e)[:200]

    # 写入 MySQL 日志
    try:
        insert_scan_log(result)
    except Exception:
        pass

    return result


def batch_scan_urls(urls: list, timeout=10):
    """批量检测多个URL"""
    results = []
    for url in urls:
        url = url.strip()
        if not url or not url.startswith("http"):
            results.append({
                "url": url or "(空)",
                "status": "failed",
                "content_preview": "无效URL"
            })
            continue
        result = scan_url(url, timeout)
        results.append(result)
    return results
