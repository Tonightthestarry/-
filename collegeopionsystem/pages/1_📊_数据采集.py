"""页面1：数据采集 - 模拟生成 + URL网页检测 + 实时流"""
import streamlit as st
import sys
import os
import time
from datetime import datetime

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from backend.data_generator import batch_generate, batch_generate_date_range
from backend.url_scanner import scan_url, batch_scan_urls
from backend.db_mysql import get_scan_logs, get_scan_stats
from backend.db_mongo import push_event_to_stream, get_recent_stream, get_stream_stats

st.set_page_config(page_title="数据采集", page_icon="📊", layout="wide")

st.title("📊 数据采集管理")
st.markdown("---")

tab1, tab2, tab3 = st.tabs(["🎲 模拟数据生成", "🔍 URL 网页检测", "🔄 实时流监控"])

# ========== Tab1: 模拟数据生成 ==========
with tab1:
    col1, col2 = st.columns([1, 1])

    with col1:
        st.subheader("即时批量生成")
        gen_count = st.slider("生成条数", 10, 500, 100, 10)
        gen_date = st.date_input("指定日期", datetime.now(), key="gen_date_batch")
        if st.button("🚀 开始生成", use_container_width=True):
            with st.spinner(f"正在生成 {gen_count} 条校园事件..."):
                date_str = gen_date.strftime("%Y-%m-%d")
                records = batch_generate(gen_count, date_str)
                # 同时推送到 MongoDB 实时流
                for r in records:
                    push_event_to_stream(r)
                st.success(f"✅ 成功生成 {len(records)} 条事件记录（已同步写入 MySQL + MongoDB）")
                st.balloons()

    with col2:
        st.subheader("日期范围批量生成")
        start_date = st.date_input("开始日期", datetime.now().replace(day=1), key="gen_start")
        end_date = st.date_input("结束日期", datetime.now(), key="gen_end")
        per_day = st.number_input("每日条数", 10, 200, 50, 10)
        if st.button("📅 按范围生成", use_container_width=True):
            days = (end_date - start_date).days + 1
            total = per_day * days
            if st.confirm(f"确认: 生成 {start_date} 到 {end_date} 共 {days} 天，每天 {per_day} 条，总计 {total} 条？"):
                with st.spinner(f"正在生成 {total} 条事件..."):
                    batch_generate_date_range(
                        start_date.strftime("%Y-%m-%d"),
                        end_date.strftime("%Y-%m-%d"),
                        per_day
                    )
                    st.success(f"✅ 日期范围生成完成，共 {total} 条事件")

# ========== Tab2: URL 网页检测 ==========
with tab2:
    col_left, col_right = st.columns([1, 1])

    with col_left:
        st.subheader("单 URL 检测")
        single_url = st.text_input("输入 URL", "https://example.com", key="single_url")
        timeout_s = st.slider("超时(秒)", 3, 30, 10, key="scan_timeout")
        if st.button("🔎 开始检测", use_container_width=True):
            if not single_url.startswith("http"):
                st.error("请输入有效的 URL (以 http:// 或 https:// 开头)")
            else:
                with st.spinner(f"正在检测 {single_url} ..."):
                    result = scan_url(single_url, timeout_s)
                    if result['status'] == 'success':
                        risk_color = {"safe": "green", "low": "blue", "medium": "orange", "high": "red", "critical": "darkred"}
                        st.success(f"检测完成！风险等级: **:{risk_color.get(result['risk_level'], 'gray')}[{result['risk_level'].upper()}]**")
                        st.text_area("网页标题", result['title'], height=68)
                        st.text_area("内容摘要", result['content_preview'][:500], height=100)
                        cols = st.columns(4)
                        cols[0].metric("命中敏感词", result['total_sensitive'])
                        cols[1].metric("风险等级", result['risk_level'])
                        if result['sensitive_words']:
                            st.warning(f"⚠️ 命中的敏感词: {'、'.join(result['sensitive_words'])}")
                    else:
                        st.error(f"检测失败: {result.get('content_preview', result['status'])}")

    with col_right:
        st.subheader("批量 URL 检测")
        batch_urls = st.text_area(
            "输入多个 URL（每行一个）",
            "https://www.example.com\nhttps://www.example.org",
            height=150,
            key="batch_urls"
        )
        if st.button("📋 批量检测", use_container_width=True):
            urls = [u.strip() for u in batch_urls.split('\n') if u.strip()]
            if not urls:
                st.error("请至少输入一个 URL")
            else:
                progress_bar = st.progress(0)
                status_text = st.empty()
                results_list = []
                for i, url in enumerate(urls):
                    status_text.text(f"正在检测 {i+1}/{len(urls)}: {url}")
                    progress_bar.progress((i + 1) / len(urls))
                    result = scan_url(url, timeout_s)
                    results_list.append(result)

                st.success(f"✅ 批量检测完成，共 {len(results_list)} 个")
                status_text.empty()

                # 统计
                risks = [r['risk_level'] for r in results_list]
                cols = st.columns(4)
                cols[0].metric("总数", len(results_list))
                cols[1].metric("安全", risks.count('safe'))
                cols[2].metric("中危", risks.count('medium'))
                cols[3].metric("高危+严重", risks.count('high') + risks.count('critical'))

    # 检测历史
    st.markdown("---")
    st.subheader("📋 最近检测日志")
    try:
        logs = get_scan_logs(20)
        if logs:
            import pandas as pd
            df = pd.DataFrame(logs)
            df_display = df[['url', 'risk_level', 'sensitive_words', 'scan_time']].copy()
            df_display.columns = ['URL', '风险等级', '敏感词', '检测时间']
            st.dataframe(df_display, use_container_width=True, hide_index=True)
        else:
            st.info("暂无检测记录")
    except Exception:
        st.info("暂无检测记录（数据库未就绪）")

# ========== Tab3: 实时流监控 ==========
with tab3:
    st.subheader("🔄 实时事件流监控")

    col1, col2, col3 = st.columns(3)
    try:
        stats = get_stream_stats()
        col1.metric("实时流中事件数", stats.get('total_in_stream', 0))
        by_type = stats.get('by_type', [])
        top_type = by_type[0] if by_type else {}
        col2.metric("最多类型", f"{top_type.get('event_type', '--')} ({top_type.get('count', 0)})")
        col3.metric("类型数", len(by_type))
    except Exception:
        col1.metric("实时流中事件数", "--")
        col2.metric("最多类型", "--")

    if st.button("🔄 刷新实时流", use_container_width=True):
        st.rerun()

    try:
        recent = get_recent_stream(50)
        if recent:
            import pandas as pd
            df = pd.DataFrame(recent)
            df_display = df[['event_type', 'title', 'campus_area', 'student_name', 'heat_score', 'sentiment', 'timestamp']].copy()
            df_display.columns = ['类型', '标题', '区域', '发布者', '热度', '情感', '时间']
            df_display['时间'] = pd.to_datetime(df_display['时间']).dt.strftime('%H:%M:%S')
            st.dataframe(df_display, use_container_width=True, hide_index=True)
        else:
            st.info("实时流暂无数据，请先生成模拟数据")
    except Exception:
        st.info("MongoDB 未连接，实时流功能暂不可用")
