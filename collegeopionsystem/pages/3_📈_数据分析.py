"""页面3：数据分析 - 6大挖掘算法：统计/聚类/关联/预测/异常/分类 + 综合报告"""
import streamlit as st
import sys
import os
import time
import random
import numpy as np
import pandas as pd
import plotly.graph_objects as go
import plotly.express as px
from datetime import datetime

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from backend.db_mysql import get_events_by_date
from backend.mining import (
    statistic_analysis, cluster_analysis, association_analysis,
    prediction_analysis, anomaly_analysis, classification_analysis
)

st.set_page_config(page_title="数据分析", page_icon="📈", layout="wide")

# ========== 算法简介字典 ==========
ALGO_DESC = {
    "统计分析": "通过计数、求和、比例、均值等描述性统计，快速掌握事件整体分布。适用于初步查看数据概况。",
    "聚类分析": "基于 K-Means 算法，将事件按热度、情感、区域多维特征自动分组。适用于发现隐藏的相似事件群组。",
    "关联规则": "计算事件类型与校园区域的支持度、置信度和提升度。用于识别'某区域常见某类事件'的规律。",
    "预测分析": "采用线性回归与移动平均双模型，基于近 7 日事件数据预测未来 3 日走势。适用于趋势预判与资源调配。",
    "异常检测": "采用四分位距（IQR）方法，识别热度显著高于常规水平的事件。适用于发现潜在的热点事件或异常舆情。",
    "分类分级": "根据热度与情绪标签，将事件自动划分为高/中/低关注三级。适用于管理者快速筛选需优先处理的事项。",
}

st.title("📈 数据分析与挖掘")
st.markdown("---")

# 顶部：日期选择 + 概况卡片
c1, c2, c3 = st.columns([2, 1, 1])
with c1:
    analysis_date = st.date_input("选择分析日期", datetime.now(), key="analysis_date")
    date_str = analysis_date.strftime("%Y-%m-%d")
with c2:
    st.markdown("<br>", unsafe_allow_html=True)
    run_all = st.button("📋 执行综合分析", use_container_width=True, type="primary")
with c3:
    st.markdown("<br>", unsafe_allow_html=True)
    st.caption(f"分析日期: {date_str}")

# ========== 读取当日事件，用于详情图表 ==========
t0 = time.time()
try:
    events = get_events_by_date(date_str, 5000)
except Exception:
    events = []

n_events = len(events)
df = pd.DataFrame(events) if events else pd.DataFrame()

# 分析概况卡片
col_k1, col_k2, col_k3, col_k4 = st.columns(4)
col_k1.metric("📦 当日事件规模", f"{n_events} 条")
col_k2.metric("🎯 分析维度", "6 大算法")
col_k3.metric("🗂️ 字段数", len(df.columns) if not df.empty else 0)
elapsed = round(time.time() - t0, 2)
col_k4.metric("⏱️ 数据读取耗时", f"{elapsed}s")
st.markdown("---")

# ========== 侧边栏：算法选择 ==========
with st.sidebar:
    st.subheader("🔧 分析算法")
    algo_choice = st.radio(
        "选择分析方法",
        ["📊 统计分析", "🔮 聚类分析", "🔗 关联规则",
         "📈 预测分析", "⚠️ 异常检测", "🏷️ 分类分级",
         "📋 综合分析报告"],
        index=0,
        help="每个算法都可以独立执行，也可点击顶部『执行综合分析』一键完成全部分析。"
    )
    st.markdown("---")
    st.caption("💡 提示：综合分析会依次执行全部 6 个算法，并在末尾生成自动汇总结论。")

# ========== 辅助函数 ==========
def show_algo_intro(name: str):
    """展示算法简介"""
    st.info(f"📖 **{name}**: {ALGO_DESC.get(name, '')}")


# ============================================================
# 1. 统计分析
# ============================================================
if algo_choice == "📊 统计分析" or run_all:
    st.subheader("📊 统计分析")
    show_algo_intro("统计分析")
    with st.spinner("正在进行统计分析..."):
        result = statistic_analysis(date_str)

    if not result:
        st.warning("今日暂无数据，无法执行统计分析。")
    else:
        cols = st.columns(5)
        cols[0].metric("今日事件总数", result.get('total', 0))
        cols[1].metric("平均热度", result.get('avg_heat', 0))
        cols[2].metric("异常事件", result.get('anomaly_count', 0))
        cols[3].metric("最多类型", result.get('top_type', '--'))
        cols[4].metric("高发区域", result.get('top_area', '--'))

        tab1, tab2, tab3 = st.tabs(["事件类型分布", "区域分布", "情感与热度分布"])

        with tab1:
            by_type = result.get('by_type', {})
            if by_type:
                fig1 = go.Figure()
                fig1.add_trace(go.Bar(
                    x=list(by_type.values()),
                    y=list(by_type.keys()),
                    orientation='h',
                    marker=dict(color='cornflowerblue'),
                ))
                fig1.update_layout(title="事件类型分布（横向柱状图）",
                                   xaxis_title="事件数", yaxis_title="类型", height=420)
                st.plotly_chart(fig1, use_container_width=True)

        with tab2:
            by_area = result.get('by_area', {})
            if by_area:
                fig2 = go.Figure()
                fig2.add_trace(go.Bar(
                    x=list(by_area.keys()),
                    y=list(by_area.values()),
                    marker=dict(color='forestgreen'),
                ))
                fig2.update_layout(title="各区域事件数量", height=420,
                                   xaxis_title="区域", yaxis_title="事件数")
                st.plotly_chart(fig2, use_container_width=True)

        with tab3:
            sentiment = result.get('sentiment', {})
            if sentiment:
                c_s1, c_s2 = st.columns([1, 1])
                with c_s1:
                    fig3 = go.Figure()
                    fig3.add_trace(go.Pie(
                        labels=['正面', '中性', '负面'],
                        values=[sentiment.get('positive', 0), sentiment.get('neutral', 0), sentiment.get('negative', 0)],
                        hole=0.55,
                        marker=dict(colors=['#67c23a', '#e6a23c', '#f56c6c']),
                    ))
                    fig3.update_layout(title="情感占比", height=380)
                    st.plotly_chart(fig3, use_container_width=True)
                with c_s2:
                    if not df.empty and 'heat_score' in df.columns:
                        fig4 = go.Figure()
                        fig4.add_trace(go.Histogram(
                            x=df['heat_score'], nbinsx=20,
                            marker=dict(color='cornflowerblue', line=dict(color='white', width=1))
                        ))
                        fig4.update_layout(title="热度分布直方图", height=380,
                                           xaxis_title="热度分", yaxis_title="事件数量")
                        st.plotly_chart(fig4, use_container_width=True)
                    else:
                        st.info("暂无足够数据绘制热度分布。")

        if result.get('conclusion'):
            st.success(f"**📝 结论:**\n\n{result['conclusion']}")

    st.markdown("---")

# ============================================================
# 2. 聚类分析
# ============================================================
if algo_choice == "🔮 聚类分析" or run_all:
    st.subheader("🔮 聚类分析 (K-Means)")
    show_algo_intro("聚类分析")
    with st.spinner("正在进行聚类分析..."):
        result = cluster_analysis(date_str)

    if not result or not result.get('clusters'):
        st.warning("暂无数据或事件不足，无法进行聚类分析。")
    else:
        clusters = result['clusters']
        df_c = pd.DataFrame([
            {
                "簇ID": c['cluster_id'],
                "事件数": c['count'],
                "平均热度": c['avg_heat'],
                "主要情感": c['dominant_sentiment'],
                "样本": "、".join(c.get('samples', [])[:3])
            }
            for c in clusters
        ])

        tab1, tab2 = st.tabs(["聚类结果表格", "聚类可视化"])
        with tab1:
            st.dataframe(df_c, use_container_width=True, hide_index=True, height=360)

        with tab2:
            c_col1, c_col2 = st.columns([1, 1])
            with c_col1:
                fig5 = go.Figure()
                fig5.add_trace(go.Bar(
                    x=df_c['簇ID'].astype(str),
                    y=df_c['事件数'],
                    marker=dict(color=df_c['平均热度'], colorscale='Viridis', showscale=True),
                ))
                fig5.update_layout(title="各簇事件数量（颜色=平均热度）", height=400,
                                   xaxis_title="簇 ID", yaxis_title="事件数")
                st.plotly_chart(fig5, use_container_width=True)

            with c_col2:
                # 用热度和情绪编码绘制二维散点模拟
                import random
                points = []
                for c in clusters:
                    for _ in range(min(c['count'], 30)):
                        # 为每个簇生成带噪声的 (热度, 情绪编码) 点
                        x = c['avg_heat'] + random.uniform(-15, 15)
                        y_map = {'positive': 2, 'neutral': 1, 'negative': 0}
                        y = y_map.get(c['dominant_sentiment'], 1) + random.uniform(-0.3, 0.3)
                        points.append({'簇': f"簇{c['cluster_id']}", '热度': x, '情绪': y})
                df_p = pd.DataFrame(points)
                if not df_p.empty:
                    fig6 = go.Figure()
                    for name, group in df_p.groupby('簇'):
                        fig6.add_trace(go.Scatter(
                            x=group['热度'], y=group['情绪'],
                            mode='markers', name=name,
                            marker=dict(size=9, line=dict(width=0.6, color='white'))
                        ))
                    fig6.update_layout(title="聚类二维散点（热度 vs 情绪）",
                                       xaxis_title="热度",
                                       yaxis_title="情绪（0=负面,1=中性,2=正面）",
                                       height=400)
                    st.plotly_chart(fig6, use_container_width=True)

        if result.get('conclusion'):
            st.info(f"**📝 结论:**\n\n{result['conclusion']}")

    st.markdown("---")

# ============================================================
# 3. 关联规则
# ============================================================
if algo_choice == "🔗 关联规则" or run_all:
    st.subheader("🔗 关联规则分析")
    show_algo_intro("关联规则")
    with st.spinner("正在分析关联规则..."):
        result = association_analysis(date_str)

    if not result or not result.get('rules'):
        st.warning("暂无足够数据进行关联规则分析。")
    else:
        rules = result['rules']
        df_r = pd.DataFrame(rules)
        df_r.columns = ['事件类型', '区域', '计数', '支持度%', '类型→区域置信度%', '区域→类型置信度%', '提升度']

        tab1, tab2 = st.tabs(["规则清单", "关联强度热力图"])
        with tab1:
            st.dataframe(df_r, use_container_width=True, hide_index=True, height=380)

        with tab2:
            # 构建事件类型 × 区域 的支持度矩阵
            pivot = df_r.pivot(index='区域', columns='事件类型', values='支持度%').fillna(0)
            if not pivot.empty:
                import plotly.express as px
                fig7 = px.imshow(
                    pivot, text_auto=True, aspect="auto",
                    color_continuous_scale='Reds',
                    title="事件类型 × 区域支持度热力图（颜色越深，相关性越强）"
                )
                fig7.update_layout(height=420)
                st.plotly_chart(fig7, use_container_width=True)

            # 提升度柱状图 Top 10
            top_lift = df_r.sort_values('提升度', ascending=False).head(10)
            if not top_lift.empty:
                top_lift['规则'] = top_lift['事件类型'] + ' → ' + top_lift['区域']
                fig8 = go.Figure()
                fig8.add_trace(go.Bar(
                    x=top_lift['提升度'],
                    y=top_lift['规则'],
                    orientation='h',
                    marker=dict(color='darkorange'),
                ))
                fig8.update_layout(title="Top 10 规则提升度", height=420,
                                   xaxis_title="提升度", yaxis_title="规则")
                st.plotly_chart(fig8, use_container_width=True)

    if result.get('conclusion'):
        st.info(f"**📝 结论:**\n\n{result['conclusion']}")

    st.markdown("---")

# ============================================================
# 4. 预测分析
# ============================================================
if algo_choice == "📈 预测分析" or run_all:
    st.subheader("📈 趋势预测分析")
    show_algo_intro("预测分析")
    with st.spinner("正在进行趋势预测..."):
        result = prediction_analysis(date_str)

    if not result:
        st.warning("暂无历史数据，无法进行预测分析。")
    else:
        cols = st.columns(3)
        cols[0].metric("趋势方向", result.get('trend', '--'))
        slope = result.get('slope', 0)
        cols[1].metric("日变化", f"{slope:+.1f} 起/天")
        preds = result.get('predictions', [])
        cols[2].metric("明日预测", f"{preds[0]['predicted']} 起" if preds else '--')

        historical = result.get('historical', [])
        if historical:
            import plotly.graph_objects as go
            dates = [h['date'] for h in historical]
            values = [h['count'] for h in historical]
            ma = [h['ma3'] for h in historical]
            pred_dates = [p['day'] for p in preds]
            pred_vals = [p['predicted'] for p in preds]

            tab1, tab2 = st.tabs(["历史 + 预测趋势", "近 7 日热力表"])
            with tab1:
                fig9 = go.Figure()
                fig9.add_trace(go.Bar(name='每日事件数', x=dates, y=values,
                                       marker_color='steelblue'))
                fig9.add_trace(go.Scatter(name='3日移动平均', x=dates, y=ma,
                                           mode='lines+markers',
                                           line=dict(color='orange', width=3)))
                if pred_dates:
                    fig9.add_trace(go.Scatter(name='回归预测', x=pred_dates, y=pred_vals,
                                               mode='lines+markers',
                                               line=dict(color='crimson', width=3, dash='dash')))
                fig9.update_layout(title="近 7 日事件数 + 未来 3 日预测",
                                   xaxis_title="日期", yaxis_title="事件数", height=440)
                st.plotly_chart(fig9, use_container_width=True)

            with tab2:
                df_tbl = pd.DataFrame({
                    "日期": dates + pred_dates,
                    "事件数": values + pred_vals,
                    "移动平均(3日)": ma + ['—'] * len(pred_vals),
                    "类型": ['历史'] * len(dates) + ['预测'] * len(pred_vals),
                })
                st.dataframe(df_tbl, use_container_width=True, hide_index=True, height=400)

    if result.get('conclusion'):
        st.success(f"**📝 结论:**\n\n{result['conclusion']}")

    st.markdown("---")

# ============================================================
# 5. 异常检测
# ============================================================
if algo_choice == "⚠️ 异常检测" or run_all:
    st.subheader("⚠️ 异常检测（IQR 四分位距法）")
    show_algo_intro("异常检测")
    with st.spinner("正在检测异常..."):
        result = anomaly_analysis(date_str)

    if not result:
        st.warning("暂无数据，无法进行异常检测。")
    else:
        cols = st.columns(5)
        cols[0].metric("Q1", f"{result.get('q1', 0):.0f}")
        cols[1].metric("Q3", f"{result.get('q3', 0):.0f}")
        cols[2].metric("IQR", f"{result.get('iqr', 0):.0f}")
        cols[3].metric("异常上界", f"{result.get('upper', 0):.0f}")
        cols[4].metric("异常事件数", result.get('count', 0))

        tab1, tab2 = st.tabs(["热度箱线图", "异常事件列表"])
        with tab1:
            if not df.empty and 'heat_score' in df.columns:
                # 构造箱线图
                import plotly.express as px
                fig10 = go.Figure()
                fig10.add_trace(go.Box(
                    y=df['heat_score'], name='热度分布',
                    marker_color='royalblue',
                ))
                # 画异常上界横线
                upper = result.get('upper', 0)
                fig10.add_hline(y=upper, line_dash="dash", line_color="red",
                                annotation_text=f"异常上界 = {upper:.0f}",
                                annotation_position="bottom right")
                fig10.update_layout(title="热度分布箱线图（红线为异常上界）",
                                    height=420, yaxis_title="热度分")
                st.plotly_chart(fig10, use_container_width=True)

                # 异常散点高亮
                anomalies = result.get('anomalies', [])
                if anomalies:
                    df_a = pd.DataFrame(anomalies)
                    fig11 = go.Figure()
                    fig11.add_trace(go.Scatter(
                        x=list(range(len(df))), y=df['heat_score'],
                        mode='markers', name='正常事件',
                        marker=dict(color='gray', size=6)
                    ))
                    fig11.add_trace(go.Scatter(
                        x=list(range(len(df_a))), y=df_a['热度'],
                        mode='markers', name='异常事件',
                        marker=dict(color='red', size=12, line=dict(color='white', width=1))
                    ))
                    fig11.add_hline(y=upper, line_dash="dash", line_color="red")
                    fig11.update_layout(title="事件热度散点（红色=异常）", height=420,
                                        xaxis_title="事件序号", yaxis_title="热度分")
                    st.plotly_chart(fig11, use_container_width=True)

        with tab2:
            anomalies = result.get('anomalies', [])
            if anomalies:
                df_anom = pd.DataFrame(anomalies)
                df_anom.columns = ['标题', '热度', '类型']
                st.dataframe(df_anom, use_container_width=True, hide_index=True, height=420)
            else:
                st.info("未检测到异常事件。")

    if result.get('conclusion'):
        st.warning(f"**📝 结论:**\n\n{result['conclusion']}")

    st.markdown("---")

# ============================================================
# 6. 分类分级
# ============================================================
if algo_choice == "🏷️ 分类分级" or run_all:
    st.subheader("🏷️ 事件分类分级")
    show_algo_intro("分类分级")
    with st.spinner("正在进行分级分类..."):
        result = classification_analysis(date_str)

    if not result:
        st.warning("暂无数据。")
    else:
        cols = st.columns(3)
        cols[0].metric("🔴 高关注", result.get('high', 0))
        cols[1].metric("🟡 中关注", result.get('medium', 0))
        cols[2].metric("🟢 低关注", result.get('low', 0))

        tab1, tab2 = st.tabs(["分级占比图", "高关注事件"])
        with tab1:
            fig12 = go.Figure()
            fig12.add_trace(go.Pie(
                labels=['🔴 高关注', '🟡 中关注', '🟢 低关注'],
                values=[result.get('high', 0), result.get('medium', 0), result.get('low', 0)],
                hole=0.5,
                marker=dict(colors=['#f56c6c', '#e6a23c', '#67c23a']),
            ))
            fig12.update_layout(title="分级占比", height=420)
            st.plotly_chart(fig12, use_container_width=True)

            # 漏斗图
            fig13 = go.Figure()
            fig13.add_trace(go.Funnel(
                y=['低关注', '中关注', '高关注'],
                x=[result.get('low', 0), result.get('medium', 0), result.get('high', 0)],
                marker=dict(color=['#67c23a', '#e6a23c', '#f56c6c']),
            ))
            fig13.update_layout(title="关注分级漏斗", height=380)
            st.plotly_chart(fig13, use_container_width=True)

        with tab2:
            high_samples = result.get('high_samples', [])
            if high_samples:
                df_high = pd.DataFrame(high_samples)
                df_high.columns = ['标题', '热度']
                st.dataframe(df_high, use_container_width=True, hide_index=True, height=420)
            else:
                st.info("今日无高关注事件。")

    if result.get('conclusion'):
        st.success(f"**📝 结论:**\n\n{result['conclusion']}")

    st.markdown("---")

# ============================================================
# 7. 综合分析报告
# ============================================================
if algo_choice == "📋 综合分析报告":
    st.subheader("📋 综合分析报告")
    st.info("📖 按顺序执行 6 大数据挖掘算法，自动汇总结论。")

    sections = []

    # 统计
    t1 = time.time()
    r1 = statistic_analysis(date_str)
    sections.append(("📊 统计分析", r1, time.time() - t1))

    # 聚类
    t2 = time.time()
    r2 = cluster_analysis(date_str)
    sections.append(("🔮 聚类分析", r2, time.time() - t2))

    # 关联
    t3 = time.time()
    r3 = association_analysis(date_str)
    sections.append(("🔗 关联规则", r3, time.time() - t3))

    # 预测
    t4 = time.time()
    r4 = prediction_analysis(date_str)
    sections.append(("📈 预测分析", r4, time.time() - t4))

    # 异常
    t5 = time.time()
    r5 = anomaly_analysis(date_str)
    sections.append(("⚠️ 异常检测", r5, time.time() - t5))

    # 分级
    t6 = time.time()
    r6 = classification_analysis(date_str)
    sections.append(("🏷️ 分类分级", r6, time.time() - t6))

    total_t = round(sum(s[2] for s in sections), 2)

    # 总览
    st.subheader("🧾 分析总览")
    o_col1, o_col2, o_col3 = st.columns(3)
    o_col1.metric("分析算法数", "6")
    o_col2.metric("数据日期", date_str)
    o_col3.metric("总耗时", f"{total_t} s")

    for name, r, t in sections:
        with st.expander(f"{name}（耗时 {round(t, 2)} s）", expanded=True):
            if r and r.get('conclusion'):
                st.write(r['conclusion'])
            else:
                st.caption("（未产出结论）")

    # 综合结论
    st.subheader("🏁 综合自动结论")
    final_lines = []
    if r1 and r1.get('total'):
        final_lines.append(f"• 今日共监测 {r1['total']} 条校园事件，主要集中于「{r1.get('top_type', '—')}」类。")
    if r1 and r1.get('top_area'):
        final_lines.append(f"• 高发区域为「{r1['top_area']}」，需重点关注。")
    if r4 and r4.get('trend'):
        final_lines.append(f"• 近 7 日事件呈「{r4['trend']}」趋势，{('建议提前调配资源。' if r4['trend'] != '平稳' else '态势稳定，保持常规监控。')}")
    if r5 and r5.get('count'):
        final_lines.append(f"• 检测到 {r5['count']} 起异常高热度事件，请及时核查处理。")
    if r6 and r6.get('high'):
        final_lines.append(f"• 识别 {r6['high']} 起高关注事件，建议优先处理。")

    if final_lines:
        st.success("\n\n".join(final_lines))
    else:
        st.warning("今日事件数据不足，综合分析未能产出结论。")

    st.markdown("---")

# ========== 底部提示 ==========
st.caption("💡 小提示：所有图表支持鼠标悬停查看数值、框选缩放、单图导出 PNG。")
