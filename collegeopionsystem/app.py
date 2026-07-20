"""校园事件监测系统 - Streamlit 主入口"""
import streamlit as st
import sys
import os

# 确保项目根目录在 path 中
project_root = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, project_root)

from backend.db_mysql import init_db
from backend.db_mongo import get_mongo_status

# ========== 页面配置 ==========
st.set_page_config(
    page_title="校园事件监测系统",
    page_icon="🏫",
    layout="wide",
    initial_sidebar_state="expanded"
)

# ========== 侧边栏 ==========
with st.sidebar:
    st.title("🏫 校园事件监测系统")
    st.markdown("---")

    # 数据库状态
    st.subheader("📡 系统状态")

    # MongoDB
    try:
        mongo_status = get_mongo_status()
        if mongo_status.get('connected'):
            st.success(f"MongoDB: ✅ 已连接 ({mongo_status.get('db')})")
        else:
            st.error(f"MongoDB: ❌ {mongo_status.get('error', '未连接')}")
    except Exception:
        st.error("MongoDB: ❌ 未启动")

    # MySQL
    try:
        from backend.db_mysql import get_connection
        conn = get_connection()
        conn.close()
        st.success("MySQL: ✅ 已连接 (campus_monitor)")
    except Exception:
        st.error("MySQL: ❌ 未连接")

    st.markdown("---")

    # 导航
    st.subheader("📋 功能导航")
    st.markdown("""
    - 📊 [数据采集](/数据采集) - 生成模拟数据 + URL检测
    - 👥 [用户管理](/用户管理) - 用户CRUD
    - 📈 [数据分析](/数据分析) - 6大挖掘算法
    - 🖥️ [可视化大屏](/可视化大屏) - 一目了然
    """)

    st.markdown("---")
    st.caption("Powered by Streamlit + MySQL + MongoDB")
    st.caption(f"Python: {sys.version}")

# ========== 初始化数据库 ==========
@st.cache_resource
def initialize():
    """初始化数据库（只执行一次）"""
    try:
        init_db()
        print("[系统] 数据库初始化完成")
        return True
    except Exception as e:
        print(f"[系统] 数据库初始化失败: {e}")
        return False

db_ok = initialize()

if not db_ok:
    st.error("⚠️ 数据库连接失败！请确认 MySQL 和 MongoDB 已启动。")
    st.stop()

# ========== 主页内容 ==========
st.title("🏫 校园事件监测系统")
st.markdown("---")

col1, col2 = st.columns([2, 1])

with col1:
    st.subheader("系统概述")
    st.markdown("""
    本系统是一个智能化的**校园事件实时监测与数据挖掘**平台，主要功能包括：

    ### 📊 核心功能
    - **🎲 数据采集**：模拟学生在校园网内发布的各类事件（体测、活动、求助、社团、课程评价、投诉等），支持批量生成和 URL 网页检测
    - **🔍 URL 巡检**：自动爬取指定网页内容，基于敏感词库进行风险评级（安全/低/中/高/严重）
    - **📈 数据分析**：集成 6 大经典数据挖掘算法（统计分析、聚类、关联规则、预测、异常检测、分类分级）
    - **🖥️ 大屏可视化**：6 大图表一目了然掌控校园态势，每张图自动给出文字结论

    ### 🏗️ 技术架构
    | 层 | 技术 |
    |---|---|
    | 前端 | Streamlit + Plotly |
    | 业务数据 | MySQL (campus_monitor) |
    | 实时流 | MongoDB |
    | 数据分析 | NumPy + Scikit-learn + Pandas |
    | URL 检测 | Requests + BeautifulSoup |
    """)

with col2:
    st.subheader("🚀 快速开始")
    st.markdown("""
    **第1步**：查看数据采集页面，批量生成模拟数据
    > 路径：侧边栏 → 📊 数据采集 → 🎲 模拟数据生成

    **第2步**：体验 URL 网页检测
    > 路径：侧边栏 → 📊 数据采集 → 🔍 URL 网页检测

    **第3步**：执行数据分析挖掘
    > 路径：侧边栏 → 📈 数据分析

    **第4步**：查看可视化大屏
    > 路径：侧边栏 → 🖥️ 可视化大屏
    """)

    st.subheader("👤 默认用户")
    st.markdown("""
    - **管理员**: admin / admin123
    - **普通用户**: user1 / 123456
    """)

st.markdown("---")

# 实时统计快照
st.subheader("📸 系统实时快照")
try:
    from backend.db_mysql import get_event_count, get_event_stats, get_scan_stats
    from backend.db_mongo import get_stream_stats

    total_events = get_event_count()
    stream = get_stream_stats()
    scans = get_scan_stats()

    cols = st.columns(4)
    cols[0].metric("📋 事件总数", total_events)
    cols[1].metric("🔄 实时流中", stream.get('total_in_stream', 0))
    cols[2].metric("🔍 URL扫描次数", sum(scans.values()) if scans else 0)
    cols[3].metric("⚠️ 高危URL", scans.get('high', 0) + scans.get('critical', 0) if scans else 0)
except Exception:
    st.info("部分数据库未就绪，快照数据不完整")

st.markdown("---")
st.caption("校园事件监测系统 v1.0 | Python + Streamlit | MySQL + MongoDB")
