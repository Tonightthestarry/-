# -*- coding: utf-8 -*-
"""基于多技术栈的数据分析与AI知识库构建 - 主应用（Streamlit）"""
import streamlit as st
import pandas as pd
from datetime import datetime, timedelta
import plotly.express as px

from user_auth import UserAuth
from bilibili_spider import BilibiliSpider
from data_cleaner import DataCleaner
from data_storage import DataStorage
from data_mining import DataMining
from auto_collector import AutoCollector
from user_analysis import UserAnalysis
from comment_analysis import CommentAnalysis

# ===================== 初始化Session State =====================
if "logged_in" not in st.session_state:
    st.session_state.logged_in = False
if "user_role" not in st.session_state:
    st.session_state.user_role = "guest"
if "username" not in st.session_state:
    st.session_state.username = ""
if "current_page" not in st.session_state:
    st.session_state.current_page = "数据采集"

# ===================== 登录页面 =====================
def login_page():
    st.set_page_config(page_title="B站数据分析平台", page_icon="📊", layout="wide")
    
    # 初始化用户表
    auth = UserAuth()
    auth.init_users_table()
    
    st.title("🔐 B站数据分析平台")
    
    with st.form("login_form"):
        st.subheader("用户登录")
        username = st.text_input("用户名")
        password = st.text_input("密码", type="password")
        submit_button = st.form_submit_button("登录", type="primary")
        
        if submit_button:
            result = auth.verify_user(username, password)
            if result["success"]:
                st.session_state.logged_in = True
                st.session_state.user_role = result["role"]
                st.session_state.username = result["username"]
                st.success(f"✅ {'管理员' if result['role'] == 'admin' else '普通用户'}登录成功！")
                st.rerun()
            else:
                st.error("❌ 用户名或密码错误")
    
    st.markdown("---")
    st.info("💡 默认账号：admin/123456（管理员），user/123456（普通用户）")

# ===================== 主应用页面 =====================
def main_app():
    st.set_page_config(page_title="B站数据分析平台", page_icon="📊", layout="wide")
    
    # 启动定时调度器（后台线程，不影响前端）
    if "scheduler_started" not in st.session_state:
        collector = AutoCollector()
        collector.start_scheduler()
        st.session_state.scheduler_started = True
    
    # 侧边栏导航
    with st.sidebar:
        st.image("https://www.bilibili.com/favicon.ico", width=32)
        st.title("功能菜单")
        
        menu_items = ["数据采集", "数据概览", "可视化图表", "关联分析", "用户分析", "评论分析", "AI知识问答"]
        for item in menu_items:
            if st.button(item, key=item, use_container_width=True):
                st.session_state.current_page = item
        
        st.markdown("---")
        st.write(f"👤 当前用户: {st.session_state.username}")
        st.write(f"🔑 角色: {'管理员' if st.session_state.user_role == 'admin' else '普通用户'}")
        if st.button("退出登录", key="logout", use_container_width=True):
            st.session_state.logged_in = False
            st.session_state.user_role = "guest"
            st.session_state.username = ""
            st.rerun()
    
    # 主内容区域
    st.header("基于多技术栈的数据分析与AI知识库构建")
    
    # 页面路由
    if st.session_state.current_page == "数据采集":
        show_data_collection()
    elif st.session_state.current_page == "数据概览":
        show_data_overview()
    elif st.session_state.current_page == "可视化图表":
        show_visualization()
    elif st.session_state.current_page == "关联分析":
        show_association_analysis()
    elif st.session_state.current_page == "用户分析":
        show_user_analysis()
    elif st.session_state.current_page == "评论分析":
        show_comment_analysis()
    elif st.session_state.current_page == "AI知识问答":
        show_ai_qa()

# ===================== 数据采集页面 =====================
def show_data_collection():
    st.subheader("⚙️ 数据采集管理")
    st.info("📌 系统支持自动采集和手动采集两种模式")
    
    # 权限检查
    if st.session_state.user_role != "admin":
        st.warning("⚠️ 只有管理员有权限访问数据采集功能")
        return
    
    # 刷新按钮
    if st.button("🔃 刷新数据", key="refresh_data"):
        st.rerun()
    
    # 采集模式选择（分开的tab）
    tab1, tab2 = st.tabs(["🔄 自动采集", "🎯 手动采集"])
    
    # 自动采集
    with tab1:
        st.subheader("自动采集设置")
        st.info("📅 自动采集指定日期的数据，默认关键词为'新闻'")
        
        col1, col2 = st.columns(2)
        with col1:
            auto_keyword = st.text_input("默认关键词", value="新闻", key="auto_keyword")
        with col2:
            auto_date = st.date_input(
                "采集日期",
                value=datetime.now() - timedelta(days=1),
                max_value=datetime.now(),
                key="auto_date"
            )
        
        col3 = st.columns(1)[0]
        with col3:
            auto_pages = st.number_input("采集页数", min_value=1, max_value=20, value=5, key="auto_pages")
        
        # 日期验证
        if auto_date > datetime.now().date():
            st.error("❌ 不能选择未来日期！")
        
        if st.button("⏰ 执行一次自动采集", type="primary", disabled=(auto_date > datetime.now().date()), key="auto_crawl"):
            with st.spinner("正在执行自动采集..."):
                collector = AutoCollector()
                date_str = auto_date.strftime("%Y-%m-%d")
                result = collector.manual_crawl(keyword=auto_keyword, target_date=date_str, max_page=auto_pages)
                if result["success"]:
                    st.success(f"✅ {result['message']}")
                else:
                    st.error(f"❌ {result['message']}")
            st.rerun()
    
    # 手动采集
    with tab2:
        st.subheader("手动采集设置")
        st.info("🔍 手动指定关键词、日期和页数进行采集")
        
        col1, col2 = st.columns(2)
        with col1:
            manual_keyword = st.text_input("搜索关键词", value="新闻", key="manual_keyword")
        with col2:
            manual_date = st.date_input(
                "采集日期",
                value=datetime.now() - timedelta(days=1),
                max_value=datetime.now(),
                key="manual_date"
            )
        
        col3 = st.columns(1)[0]
        with col3:
            manual_pages = st.number_input("采集页数", min_value=1, max_value=20, value=3, key="manual_pages")
        
        # 日期验证
        if manual_date > datetime.now().date():
            st.error("❌ 不能选择未来日期！")
        
        if st.button("🚀 开始手动采集", type="primary", disabled=(manual_date > datetime.now().date()), key="manual_crawl"):
            with st.spinner("正在采集数据..."):
                collector = AutoCollector()
                date_str = manual_date.strftime("%Y-%m-%d")
                result = collector.manual_crawl(keyword=manual_keyword, target_date=date_str, max_page=manual_pages)
                if result["success"]:
                    st.success(f"✅ {result['message']}")
                else:
                    st.error(f"❌ {result['message']}")
            st.rerun()
    
    # 采集状态
    st.markdown("---")
    st.subheader("📊 实时采集状态")
    storage = DataStorage()
    counts = storage.get_video_count()
    
    col1, col2, col3 = st.columns(3)
    with col1:
        st.metric("MySQL数据", f"{counts['mysql']} 条")
    with col2:
        st.metric("MongoDB数据", f"{counts['mongo']} 条")
    with col3:
        st.metric("最后更新", datetime.now().strftime("%Y-%m-%d %H:%M:%S"))

# ===================== 数据概览页面 =====================
def show_data_overview():
    st.subheader("📊 数据概览")
    
    storage = DataStorage()
    counts = storage.get_video_count()
    videos = storage.get_all_videos()
    
    # 统计卡片
    col1, col2, col3 = st.columns(3)
    with col1:
        st.metric("MySQL数据", f"{counts['mysql']} 条")
    with col2:
        st.metric("MongoDB数据", f"{counts['mongo']} 条")
    with col3:
        st.metric("展示视频数", f"{len(videos)} 条")
    
    # 视频列表
    if videos:
        st.subheader("📹 视频列表")
        df = pd.DataFrame(videos)
        df = df[["title", "up_name", "play", "pubdate", "cluster_type", "video_url"]]
        df.columns = ["标题", "UP主", "播放量", "发布时间", "聚类类别", "视频链接"]
        st.dataframe(df, use_container_width=True)
    else:
        st.info("暂无数据，请先进行数据采集")

# ===================== 可视化图表页面 =====================
def show_visualization():
    st.subheader("📈 可视化图表")
    
    selected_date = st.date_input("选择日期", value=datetime.now() - timedelta(days=1))
    date_str = selected_date.strftime("%Y-%m-%d")
    
    mining = DataMining()
    basic = mining.get_basic_stats(date_str)
    up = mining.get_up_stats(date_str)
    clusters = mining.get_cluster_stats(date_str)
    top_videos = mining.get_top_videos(date_str)
    
    # 互动指标对比
    st.subheader("互动指标对比")
    if not basic.empty:
        metrics = ['平均播放量', '平均点赞数', '平均投币数', '平均收藏数']
        values = [
            int(basic['avg_play'].iloc[0]),
            int(basic['avg_like'].iloc[0]),
            int(basic['avg_coin'].iloc[0]),
            int(basic['avg_favorite'].iloc[0])
        ]
        metric_df = pd.DataFrame({'指标': metrics, '数值': values})
        fig = px.bar(metric_df, x='指标', y='数值', color='指标', title='互动指标对比')
        st.plotly_chart(fig, use_container_width=True)
    else:
        st.info("暂无数据，请先执行Spark分析")
    
    # UP主播放量TOP10
    st.subheader("UP主播放量TOP10")
    if not up.empty:
        up_play = up.sort_values('total_play', ascending=False).head(10)
        fig = px.bar(up_play, x='up_name', y='total_play', title='UP主播放量TOP10',
                     labels={'up_name': 'UP主', 'total_play': '总播放量'})
        st.plotly_chart(fig, use_container_width=True)
    else:
        st.info("暂无数据")
    
    # 聚类类别分布
    st.subheader("聚类类别分布")
    if not clusters.empty:
        fig = px.pie(clusters, values='video_count', names='cluster_type', title='聚类类别分布')
        st.plotly_chart(fig, use_container_width=True)
    else:
        st.info("暂无数据")
    
    # TOP视频列表
    st.subheader("播放量TOP10视频")
    if not top_videos.empty:
        top_videos_df = top_videos[['title', 'up_name', 'play']]
        top_videos_df.columns = ['标题', 'UP主', '播放量']
        st.dataframe(top_videos_df, use_container_width=True)
    else:
        st.info("暂无数据")
    
    # 气泡散点图（播放量 vs 点赞数）
    st.subheader("播放量与点赞数气泡图")
    storage = DataStorage()
    videos = storage.get_all_videos()
    if videos:
        bubble_df = pd.DataFrame(videos)
        bubble_df['size'] = bubble_df['play'] / 1000
        fig = px.scatter(bubble_df, x='play', y='like', size='size', 
                         color='cluster_type', hover_name='title',
                         title='播放量与点赞数气泡图',
                         labels={'play': '播放量', 'like': '点赞数', 'size': '播放量(千)'})
        st.plotly_chart(fig, use_container_width=True)
    else:
        st.info("暂无数据")
    
    # Spark分析按钮
    if st.button("🔄 执行Spark分析", type="primary"):
        with st.spinner("正在执行Spark分析..."):
            result = mining.execute_spark_analysis(date_str)
            if result["success"]:
                st.success("✅ Spark分析完成！")
                st.rerun()
            else:
                st.error(f"❌ {result['message']}")

# ===================== 关联分析页面 =====================
def show_association_analysis():
    st.subheader("🔗 互动行为关联规则")
    
    selected_date = st.date_input("选择日期", value=datetime.now() - timedelta(days=1))
    date_str = selected_date.strftime("%Y-%m-%d")
    
    mining = DataMining()
    rules = mining.get_association_rules(date_str)
    clusters = mining.get_cluster_stats(date_str)
    
    # 关联规则表格
    st.subheader("关联规则")
    if not rules.empty:
        rules_df = rules[["rule", "confidence", "support"]]
        rules_df.columns = ["关联规则", "置信度", "支持度"]
        st.dataframe(rules_df, use_container_width=True)
        
        # 关联规则可视化
        fig = px.bar(rules_df, x='关联规则', y='置信度', title='关联规则置信度',
                     labels={'关联规则': '规则', '置信度': '置信度'})
        st.plotly_chart(fig, use_container_width=True)
    else:
        st.info("暂无关联规则数据")
    
    # KMeans聚类结果
    st.subheader("KMeans聚类结果")
    if not clusters.empty:
        cluster_df = clusters[["cluster_type", "video_count", "avg_play"]]
        cluster_df.columns = ["聚类类别", "视频数量", "平均播放量"]
        st.dataframe(cluster_df, use_container_width=True)
        
        fig = px.bar(cluster_df, x='聚类类别', y='视频数量', title='聚类类别数量分布',
                     labels={'视频数量': '视频数量', '聚类类别': '聚类类别'})
        st.plotly_chart(fig, use_container_width=True)
    else:
        st.info("暂无聚类数据")
    
    if st.button("🔄 执行分析", type="primary"):
        with st.spinner("正在执行分析..."):
            result = mining.execute_spark_analysis(date_str)
            if result["success"]:
                st.success("✅ 分析完成！")
                st.rerun()
            else:
                st.error(f"❌ {result['message']}")

# ===================== AI知识问答页面 =====================
def show_ai_qa():
    st.subheader("🤖 AI知识问答")
    
    question = st.text_input("请输入您的问题", placeholder="例如：播放量最高的视频有哪些？")
    
    if st.button("提问", type="primary"):
        if question:
            storage = DataStorage()
            mining = DataMining()
            
            if any(k in question for k in ['多少', '数量', '统计']):
                counts = storage.get_video_count()
                answer = f"📊 当前数据库中共有 {counts['mysql']} 条视频数据"
            elif any(k in question for k in ['播放量', '热门']):
                basic = mining.get_basic_stats((datetime.now() - timedelta(days=1)).strftime("%Y-%m-%d"))
                if not basic.empty:
                    answer = f"🔥 平均播放量：{int(basic['avg_play'].iloc[0]):,} 次"
                else:
                    answer = "暂无播放量数据，请先执行Spark分析"
            elif any(k in question for k in ['UP主', '作者']):
                answer = "👤 您可以在数据概览页面查看UP主相关数据"
            elif any(k in question for k in ['聚类', '分类']):
                answer = "🎯 视频分为四类：冷门视频、普通视频、热门视频、爆款视频"
            elif any(k in question for k in ['关联', '规则']):
                answer = "🔗 互动行为关联规则包括：点赞→投币、收藏→点赞、投币→收藏等"
            else:
                answer = "📚 这是一个很好的问题！您可以尝试查看数据概览或可视化图表获取更多信息。"
            
            st.success(answer)
    
    # 常见问题列表
    st.markdown("---")
    st.subheader("💡 常见问题")
    
    faq_list = [
        {"question": "数据库中有多少条视频数据？", "answer": "可以查看数据概览页面获取最新统计"},
        {"question": "视频是如何分类的？", "answer": "根据播放量和点赞数分为：冷门视频、普通视频、热门视频、爆款视频"},
        {"question": "如何查看播放量最高的视频？", "answer": "在可视化图表页面查看TOP视频列表"},
        {"question": "什么是关联规则？", "answer": "互动行为之间的关联，如：点赞→投币、收藏→点赞等"},
        {"question": "如何查看UP主统计数据？", "answer": "在可视化图表页面查看UP主播放量TOP10"},
        {"question": "数据是如何采集的？", "answer": "通过B站API进行关键词搜索和日期筛选采集"}
    ]
    
    for idx, faq in enumerate(faq_list, 1):
        with st.expander(f"Q{idx}: {faq['question']}"):
            st.write(f"✅ {faq['answer']}")

# ===================== 用户分析页面 =====================
def show_user_analysis():
    st.subheader("👤 用户分析")
    st.info("📌 输入用户UID，爬取用户动态、视频评论等信息（分而治之思想）")
    
    uid = st.text_input("请输入用户UID", placeholder="例如：667356508")
    max_videos = st.number_input("爬取视频数", min_value=1, max_value=20, value=5)
    max_comments_per_video = st.number_input("每个视频爬取页数", min_value=1, max_value=20, value=5)
    
    if st.button("🚀 开始分析用户", type="primary") and uid:
        with st.spinner("正在分析用户..."):
            analyzer = UserAnalysis()
            result = analyzer.analyze_user(uid, max_videos, max_comments_per_video)
            if result["success"]:
                st.success(f"✅ {result['message']}")
                
                if result.get("user_info"):
                    st.subheader("📋 用户基本信息")
                    user_info = result["user_info"]
                    col1, col2, col3, col4 = st.columns(4)
                    col1.metric("用户名", user_info["name"])
                    col2.metric("等级", f"Lv.{user_info['level']}")
                    col3.metric("粉丝数", user_info["fans"])
                    col4.metric("投稿数", user_info["videos"])
                
                st.subheader("📊 数据统计")
                col_a, col_b = st.columns(2)
                col_a.metric("动态条数", result.get("dynamics_count", 0))
                col_b.metric("评论条数", result.get("comments_count", 0))
                
            else:
                st.error(f"❌ {result['message']}")

# ===================== 评论分析页面 =====================
def show_comment_analysis():
    st.subheader("💬 评论分析")
    st.info("📌 输入视频链接，爬取评论并分析游戏成分和词云")
    
    url = st.text_input("请输入B站视频链接", placeholder="例如：https://www.bilibili.com/video/BV1xx4y167iX")
    max_page = st.number_input("爬取评论页数", min_value=1, max_value=50, value=20)
    
    if st.button("🚀 开始分析评论", type="primary") and url:
        with st.spinner("正在分析评论..."):
            analyzer = CommentAnalysis()
            result = analyzer.analyze_link(url, max_page)
            if result["success"]:
                st.success(f"✅ {result['message']}")
                
                if result.get("video_info"):
                    st.subheader("📋 视频信息")
                    st.info(f"标题: {result['video_info']['title']}")
                
                st.subheader("📊 评论统计")
                st.metric("评论总数", result.get("comments_count", 0))
                
                if result.get("game_analysis"):
                    st.subheader("🎮 游戏成分分析")
                    game_df = pd.DataFrame([
                        {"游戏名称": game, "提及次数": data["count"], "占比(%)": data["percentage"]}
                        for game, data in result["game_analysis"].items()
                    ])
                    st.dataframe(game_df, use_container_width=True)
                    
                    fig = px.bar(game_df, x='游戏名称', y='提及次数', title='游戏提及次数',
                                 labels={'游戏名称': '游戏', '提及次数': '次数'})
                    st.plotly_chart(fig, use_container_width=True)
                
                if result.get("wordcloud_top"):
                    st.subheader("☁️ 热词TOP20")
                    word_df = pd.DataFrame(result["wordcloud_top"], columns=["词", "词频"])
                    st.dataframe(word_df, use_container_width=True)
                    
                    fig = px.bar(word_df, x='词', y='词频', title='热词TOP20',
                                 labels={'词': '词语', '词频': '词频'})
                    st.plotly_chart(fig, use_container_width=True)
                
            else:
                st.error(f"❌ {result['message']}")

# ===================== 程序入口 =====================
if __name__ == "__main__":
    if not st.session_state.logged_in:
        login_page()
    else:
        main_app()