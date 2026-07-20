"""页面4：可视化大屏 - 6大图表 + 文字结论"""
import streamlit as st
import sys
import os
from datetime import datetime
import plotly.graph_objects as go
import plotly.express as px
from plotly.subplots import make_subplots

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from backend.visualization import (
    get_overview_cards, get_event_type_pie, get_campus_area_heatmap,
    get_hourly_distribution, get_sentiment_gauge, get_prediction_chart
)
from backend.db_mysql import get_event_stats, get_event_area_stats, get_event_hourly

st.set_page_config(page_title="可视化大屏", page_icon="🖥️", layout="wide")

st.title("🖥️ 校园事件监测大屏")
st.caption("一目了然掌握校园态势")

# 日期选择
col_d1, col_d2 = st.columns([1, 5])
with col_d1:
    view_date = st.date_input("查看日期", datetime.now(), key="dash_date")
    date_str = view_date.strftime("%Y-%m-%d")

if st.button("🔄 刷新大屏", use_container_width=True):
    st.rerun()

st.markdown("---")

# ========== 第一行：4个概览卡片 ==========
cards = get_overview_cards()
cols = st.columns(4)
for i, card in enumerate(cards):
    cols[i].metric(
        f"{card['icon']} {card['title']}",
        card['value']
    )

st.markdown("---")

# ========== 第二行：图表 + 结论 ==========
row1_cols = st.columns(2)

with row1_cols[0]:
    st.subheader("🚨 事件类型分布")
    pie_data = get_event_type_pie(date_str)
    if pie_data['labels']:
        fig = px.pie(
            names=pie_data['labels'],
            values=pie_data['values'],
            hole=0.4
        )
        fig.update_layout(height=350, margin=dict(t=0, b=0))
        st.plotly_chart(fig, use_container_width=True)
        st.info(f"**📝 结论:** {pie_data['conclusion']}")
    else:
        st.info("暂无数据")

with row1_cols[1]:
    st.subheader("📍 区域事件热力分布")
    area_data = get_campus_area_heatmap(date_str)
    if area_data['labels']:
        fig = px.bar(
            x=area_data['labels'],
            y=area_data['values'],
            color=area_data['values'],
            color_continuous_scale='reds',
            labels={'x': '区域', 'y': '事件数'}
        )
        fig.update_layout(height=350, margin=dict(t=0, b=0))
        st.plotly_chart(fig, use_container_width=True)
        st.warning(f"**📝 结论:** {area_data['conclusion']}")
    else:
        st.info("暂无数据")

st.markdown("---")

# ========== 第三行：24h事件分布 + 舆情仪表 ==========
row2_cols = st.columns(2)

with row2_cols[0]:
    st.subheader("⏰ 24h 事件时间分布")
    hourly_data = get_hourly_distribution(date_str)
    if hourly_data['values']:
        fig = go.Figure()
        fig.add_trace(go.Scatter(
            x=list(range(24)),
            y=hourly_data['values'],
            mode='lines+markers',
            fill='tozeroy',
            fillcolor='rgba(100, 149, 237, 0.2)',
            line=dict(color='cornflowerblue', width=2)
        ))
        # 标记高峰
        peak = max(hourly_data['values'])
        peak_hour = hourly_data['values'].index(peak) if peak > 0 else 0
        fig.add_vline(x=peak_hour, line_dash="dash", line_color="red",
                      annotation_text=f"高峰 {peak_hour}:00")
        fig.update_layout(height=350, xaxis_title="小时", yaxis_title="事件数")
        st.plotly_chart(fig, use_container_width=True)
        if peak > 0:
            st.info(f"**📝 结论:** 事件高峰在 **{peak_hour}:00**，共 **{peak}** 起。建议该时段加强巡逻。")
        else:
            st.info("暂无今日数据")
    else:
        st.info("暂无数据")

with row2_cols[1]:
    st.subheader("💬 校园舆情仪表盘")
    gauge_data = get_sentiment_gauge(date_str)
    if gauge_data:
        from plotly.subplots import make_subplots
        fig = make_subplots(
            rows=1, cols=2,
            specs=[[{'type': 'pie'}, {'type': 'indicator'}]],
            column_widths=[0.6, 0.4]
        )
        fig.add_trace(
            go.Pie(
                labels=['正面', '中性', '负面'],
                values=[gauge_data['positive'], gauge_data['neutral'], gauge_data['negative']],
                hole=0.5,
                marker=dict(colors=['#67c23a', '#e6a23c', '#f56c6c'])
            ), row=1, col=1
        )
        fig.add_trace(
            go.Indicator(
                mode="gauge+number",
                value=gauge_data['positive'],
                title={'text': "正面舆情占比%"},
                gauge={
                    'axis': {'range': [0, 100]},
                    'bar': {'color': '#67c23a'},
                    'steps': [
                        {'range': [0, 30], 'color': 'rgba(245,108,108,0.3)'},
                        {'range': [30, 60], 'color': 'rgba(230,162,60,0.3)'},
                        {'range': [60, 100], 'color': 'rgba(103,194,58,0.3)'},
                    ]
                }
            ), row=1, col=2
        )
        fig.update_layout(height=350)
        st.plotly_chart(fig, use_container_width=True)
        st.info(f"**📝 结论:** {gauge_data['conclusion']}")
    else:
        st.info("暂无数据")

st.markdown("---")

# ========== 第四行：预测趋势（全宽） ==========
st.subheader("🔮 未来趋势预测")
pred_data = get_prediction_chart()
if pred_data.get('dates'):
    fig = go.Figure()
    fig.add_trace(go.Bar(
        name='历史事件数', x=pred_data['dates'], y=pred_data['values'],
        marker_color='lightslategray'
    ))
    fig.add_trace(go.Scatter(
        name='3日移动平均', x=pred_data['dates'], y=pred_data['ma'],
        mode='lines+markers', line=dict(color='orange', width=3)
    ))
    if pred_data.get('predictions'):
        fig.add_trace(go.Scatter(
            name='预测值（虚线）', x=pred_data['prediction_dates'], y=pred_data['predictions'],
            mode='lines+markers', line=dict(color='crimson', width=3, dash='dash')
        ))
    fig.update_layout(height=400)
    st.plotly_chart(fig, use_container_width=True)
    st.success(f"**📝 结论:**\n\n{pred_data.get('conclusion', '暂无结论')}")
else:
    st.info("历史数据不足，需要至少3天数据才能显示趋势预测")

st.markdown("---")
st.caption("校园事件监测系统 | 数据每分钟自动更新 | MongoDB实时流 + MySQL业务存储")
