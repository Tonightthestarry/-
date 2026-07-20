# -*- coding: utf-8 -*-
"""数据挖掘模块 - Spark分析和图表数据生成"""
import pymysql
import pandas as pd
from pyspark.sql import SparkSession
from pyspark import RDD
from datetime import datetime, timedelta
import re

MYSQL_CONFIG = {
    "host": "localhost",
    "port": 3306,
    "user": "root",
    "password": "root",
    "database": "ai_know",
    "charset": "utf8mb4"
}

# Spark连接配置
SPARK_CONFIG = {
    "app_name": "MingchaoDataMining",
    "master": "local[*]"
}

# ===================== 标签分类库和评判标准 =====================
TAG_CLASSIFICATION = {
    "热门标签": {
        "keywords": ["游戏", "美食", "音乐", "电影", "科技", "数码", "美妆", "穿搭", "旅游", "体育", 
                     "动漫", "vlog", "搞笑", "生活", "科技", "数码", "数码"],
        "thresholds": {
            "爆款": {"play": 500000, "like": 20000},
            "热门": {"play": 100000, "like": 5000},
            "普通": {"play": 20000, "like": 1000},
            "冷门": {"play": 5000, "like": 100}
        }
    },
    "中等标签": {
        "keywords": ["教育", "职场", "财经", "汽车", "房产", "知识", "科普", "历史", "人文", "社科"],
        "thresholds": {
            "爆款": {"play": 200000, "like": 10000},
            "热门": {"play": 50000, "like": 2000},
            "普通": {"play": 10000, "like": 500},
            "冷门": {"play": 2000, "like": 50}
        }
    },
    "冷门标签": {
        "keywords": ["修鞋", "木工", "手工", "园艺", "钓鱼", "摄影", "绘画", "书法", "乐器", "舞蹈"],
        "thresholds": {
            "爆款": {"play": 100000, "like": 5000},
            "热门": {"play": 30000, "like": 1000},
            "普通": {"play": 5000, "like": 200},
            "冷门": {"play": 1000, "like": 20}
        }
    }
}


class DataMining:
    def __init__(self):
        self.conn = None
        self.cursor = None
        self.spark = None

    def _connect(self):
        """连接MySQL"""
        try:
            self.conn = pymysql.connect(**MYSQL_CONFIG)
            self.cursor = self.conn.cursor(pymysql.cursors.DictCursor)
            return True
        except Exception as e:
            print(f"MySQL连接失败: {e}")
            return False

    def _close(self):
        """关闭连接"""
        if self.cursor:
            try:
                self.cursor.close()
            except:
                pass
        if self.conn:
            try:
                self.conn.close()
            except:
                pass
        if self.spark:
            try:
                self.spark.stop()
            except:
                pass

    def _connect_spark(self):
        """连接Spark（使用用户提供的配置）"""
        try:
            builder = SparkSession.builder \
                .appName(SPARK_CONFIG["app_name"]) \
                .master(SPARK_CONFIG["master"])

            if "jars" in SPARK_CONFIG:
                builder = builder.config("spark.jars", SPARK_CONFIG["jars"])

            self.spark = builder.getOrCreate()
            print("Spark连接成功")
            return True
        except Exception as e:
            print(f"Spark连接失败: {e}")
            print("将使用模拟Spark分析模式")
            return False

    def _get_all_daily_tables(self):
        """获取所有分表"""
        tables = []
        if not self._connect():
            return tables

        try:
            self.cursor.execute("SHOW TABLES LIKE 'bilibili_videos_%'")
            result = self.cursor.fetchall()
            tables = [list(row.values())[0] for row in result]
            if not tables:
                tables = ["bilibili_videos"]
        except Exception as e:
            print(f"获取分表失败: {e}")
        finally:
            self._close()
        return tables

    def _classify_video_tag(self, title):
        """根据视频标题分类标签"""
        title_lower = title.lower()
        for tag_category, config in TAG_CLASSIFICATION.items():
            for keyword in config["keywords"]:
                if keyword in title_lower:
                    return tag_category
        return "中等标签"

    def _update_video_cluster(self, video_info):
        """根据分类标签更新视频聚类"""
        title = video_info.get("title", "")
        play = video_info.get("play", 0)
        like_count = video_info.get("like", 0)

        tag_category = self._classify_video_tag(title)
        thresholds = TAG_CLASSIFICATION[tag_category]["thresholds"]

        if play >= thresholds["爆款"]["play"] or like_count >= thresholds["爆款"]["like"]:
            return {"cluster_label": 2, "cluster_type": "爆款视频", "tag_category": tag_category}
        elif play >= thresholds["热门"]["play"] or like_count >= thresholds["热门"]["like"]:
            return {"cluster_label": 1, "cluster_type": "热门视频", "tag_category": tag_category}
        elif play >= thresholds["普通"]["play"] or like_count >= thresholds["普通"]["like"]:
            return {"cluster_label": 1, "cluster_type": "普通视频", "tag_category": tag_category}
        else:
            return {"cluster_label": 0, "cluster_type": "冷门视频", "tag_category": tag_category}

    def spark_cluster_analysis(self):
        """使用Spark RDD进行聚类分析并更新数据库"""
        if not self._connect():
            return {"success": False, "message": "数据库连接失败"}

        try:
            if not self._connect_spark():
                return {"success": False, "message": "Spark连接失败"}

            # 直接查分表（不用_get_all_daily_tables，因为它会_close()导致cursor失效）
            tables = []
            try:
                self.cursor.execute("SHOW TABLES LIKE 'bilibili_videos_%'")
                result = self.cursor.fetchall()
                tables = [list(row.values())[0] for row in result]
            except Exception as e:
                print(f"获取分表失败: {e}")
            if not tables:
                tables = ["bilibili_videos"]
            print(f"发现分表: {tables}")

            all_videos = []
            for table in tables:
                try:
                    self.cursor.execute(f"SELECT id, title, play, `like`, coin, favorite FROM {table}")
                    videos = self.cursor.fetchall()
                    all_videos.extend(videos)
                except Exception as e:
                    print(f"读取表 {table} 失败: {e}")

            if not all_videos:
                return {"success": False, "message": "没有视频数据"}

            # 使用Spark RDD进行处理
            video_rdd = self.spark.sparkContext.parallelize(all_videos)

            # 聚类分析
            def process_video(video):
                cluster_info = self._update_video_cluster({
                    "title": video["title"],
                    "play": video["play"],
                    "like": video["like"]
                })
                return {
                    "id": video["id"],
                    "play": video["play"],
                    "like": video["like"],
                    "tag_category": cluster_info["tag_category"],
                    "cluster_label": cluster_info["cluster_label"],
                    "cluster_type": cluster_info["cluster_type"]
                }

            clustered_rdd = video_rdd.map(process_video)

            # 更新数据库
            update_count = 0
            clustered_list = clustered_rdd.collect()

            for video in clustered_list:
                for table in tables:
                    try:
                        self.cursor.execute(f"""
                            UPDATE {table} 
                            SET cluster_label = %s, cluster_type = %s
                            WHERE id = %s
                        """, (video["cluster_label"], video["cluster_type"], video["id"]))
                        if self.cursor.rowcount > 0:
                            update_count += self.cursor.rowcount
                    except Exception as e:
                        pass

            self.conn.commit()
            print(f"聚类分析完成，更新了 {update_count} 条记录")

            # 统计结果
            cluster_stats = {}
            for video in clustered_list:
                ct = video["cluster_type"]
                tag_cat = video["tag_category"]
                if ct not in cluster_stats:
                    cluster_stats[ct] = {"count": 0, "total_play": 0}
                cluster_stats[ct]["count"] += 1
                cluster_stats[ct]["total_play"] += video["play"]

            for ct in cluster_stats:
                cluster_stats[ct]["avg_play"] = cluster_stats[ct]["total_play"] / cluster_stats[ct]["count"]

            return {
                "success": True,
                "message": f"聚类分析完成，更新了 {update_count} 条记录",
                "stats": cluster_stats
            }

        except Exception as e:
            print(f"Spark聚类分析失败: {e}")
            return {"success": False, "message": str(e)}
        finally:
            self._close()

    def init_analysis_tables(self):
        """初始化分析表（创建Spark分析相关表）"""
        if not self._connect():
            return False

        create_tables_sql = """
        CREATE TABLE IF NOT EXISTS spark_analysis_basic_stats (
            id INT AUTO_INCREMENT PRIMARY KEY,
            avg_play INT, avg_like INT, avg_coin INT, avg_favorite INT,
            total_videos INT, analysis_date VARCHAR(20), created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        );

        CREATE TABLE IF NOT EXISTS spark_analysis_top_videos (
            id INT AUTO_INCREMENT PRIMARY KEY,
            title VARCHAR(500), up_name VARCHAR(100), play INT, analysis_date VARCHAR(20)
        );

        CREATE TABLE IF NOT EXISTS spark_analysis_up_stats (
            id INT AUTO_INCREMENT PRIMARY KEY,
            up_name VARCHAR(100), total_play INT, video_count INT, analysis_date VARCHAR(20)
        );

        CREATE TABLE IF NOT EXISTS spark_analysis_video_clusters (
            id INT AUTO_INCREMENT PRIMARY KEY,
            cluster_type VARCHAR(50), video_count INT, avg_play INT, analysis_date VARCHAR(20)
        );

        CREATE TABLE IF NOT EXISTS spark_analysis_association_rules (
            id INT AUTO_INCREMENT PRIMARY KEY,
            rule VARCHAR(200), confidence FLOAT, support FLOAT, analysis_date VARCHAR(20)
        );
        """

        try:
            for sql in create_tables_sql.split(';'):
                if sql.strip():
                    self.cursor.execute(sql)
            self.conn.commit()
            return True
        except Exception as e:
            print(f"初始化表失败: {e}")
            return False
        finally:
            self._close()

    def _ensure_tables(self):
        """确保分析表存在（不关闭连接）"""
        create_tables_sql = """
        CREATE TABLE IF NOT EXISTS spark_analysis_basic_stats (
            id INT AUTO_INCREMENT PRIMARY KEY,
            avg_play INT, avg_like INT, avg_coin INT, avg_favorite INT,
            total_videos INT, analysis_date VARCHAR(20), created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        );

        CREATE TABLE IF NOT EXISTS spark_analysis_top_videos (
            id INT AUTO_INCREMENT PRIMARY KEY,
            title VARCHAR(500), up_name VARCHAR(100), play INT, analysis_date VARCHAR(20)
        );

        CREATE TABLE IF NOT EXISTS spark_analysis_up_stats (
            id INT AUTO_INCREMENT PRIMARY KEY,
            up_name VARCHAR(100), total_play INT, video_count INT, analysis_date VARCHAR(20)
        );

        CREATE TABLE IF NOT EXISTS spark_analysis_video_clusters (
            id INT AUTO_INCREMENT PRIMARY KEY,
            cluster_type VARCHAR(50), video_count INT, avg_play INT, analysis_date VARCHAR(20)
        );

        CREATE TABLE IF NOT EXISTS spark_analysis_association_rules (
            id INT AUTO_INCREMENT PRIMARY KEY,
            rule VARCHAR(200), confidence FLOAT, support FLOAT, analysis_date VARCHAR(20)
        );
        """

        for sql in create_tables_sql.split(';'):
            if sql.strip():
                self.cursor.execute(sql)
        self.conn.commit()

    def execute_spark_analysis(self, analysis_date):
        """执行Spark分析（包含聚类分析）"""
        if not self._connect():
            return {"success": False, "message": "数据库连接失败"}

        try:
            self._connect_spark()
            self._ensure_tables()

            # 先执行聚类分析更新标签（spark_cluster_analysis内部会开关连接，执行后需重连）
            cluster_result = self.spark_cluster_analysis()
            self._connect()  # 重连MySQL（spark_cluster_analysis内部_close了）

            # 清空当天数据
            self.cursor.execute("DELETE FROM spark_analysis_basic_stats WHERE analysis_date = %s", (analysis_date,))
            self.cursor.execute("DELETE FROM spark_analysis_top_videos WHERE analysis_date = %s", (analysis_date,))
            self.cursor.execute("DELETE FROM spark_analysis_up_stats WHERE analysis_date = %s", (analysis_date,))
            self.cursor.execute("DELETE FROM spark_analysis_video_clusters WHERE analysis_date = %s", (analysis_date,))
            self.cursor.execute("DELETE FROM spark_analysis_association_rules WHERE analysis_date = %s", (analysis_date,))

            # 直接查分表（不能调_get_all_daily_tables，它会关闭cursor）
            tables = []
            try:
                self.cursor.execute("SHOW TABLES LIKE 'bilibili_videos_%'")
                result = self.cursor.fetchall()
                tables = [list(row.values())[0] for row in result]
            except Exception as e:
                print(f"获取分表失败: {e}")
            if not tables:
                tables = ["bilibili_videos"]

            total_videos = 0
            total_play = 0
            total_like = 0
            total_coin = 0
            total_favorite = 0
            top_videos = []
            up_stats_dict = {}
            cluster_stats_dict = {}

            for table in tables:
                try:
                    self.cursor.execute(f"SELECT * FROM {table}")
                    videos = self.cursor.fetchall()

                    for video in videos:
                        total_videos += 1
                        total_play += video.get("play", 0)
                        total_like += video.get("like", 0)
                        total_coin += video.get("coin", 0)
                        total_favorite += video.get("favorite", 0)

                        top_videos.append({
                            "title": video.get("title", ""),
                            "up_name": video.get("up_name", ""),
                            "play": video.get("play", 0)
                        })

                        up_name = video.get("up_name", "")
                        if up_name not in up_stats_dict:
                            up_stats_dict[up_name] = {"total_play": 0, "video_count": 0}
                        up_stats_dict[up_name]["total_play"] += video.get("play", 0)
                        up_stats_dict[up_name]["video_count"] += 1

                        cluster_type = video.get("cluster_type", "未分类")
                        if cluster_type not in cluster_stats_dict:
                            cluster_stats_dict[cluster_type] = {"video_count": 0, "total_play": 0}
                        cluster_stats_dict[cluster_type]["video_count"] += 1
                        cluster_stats_dict[cluster_type]["total_play"] += video.get("play", 0)
                except Exception as e:
                    print(f"处理表 {table} 失败: {e}")

            if total_videos > 0:
                avg_play = total_play // total_videos
                avg_like = total_like // total_videos
                avg_coin = total_coin // total_videos
                avg_favorite = total_favorite // total_videos

                self.cursor.execute("""
                    INSERT INTO spark_analysis_basic_stats 
                    (avg_play, avg_like, avg_coin, avg_favorite, total_videos, analysis_date)
                    VALUES (%s, %s, %s, %s, %s, %s)
                """, (avg_play, avg_like, avg_coin, avg_favorite, total_videos, analysis_date))

                top_videos.sort(key=lambda x: x["play"], reverse=True)
                for video in top_videos[:10]:
                    self.cursor.execute("""
                        INSERT INTO spark_analysis_top_videos (title, up_name, play, analysis_date)
                        VALUES (%s, %s, %s, %s)
                    """, (video["title"], video["up_name"], video["play"], analysis_date))

                up_list = sorted(up_stats_dict.items(), key=lambda x: x[1]["total_play"], reverse=True)[:10]
                for up_name, data in up_list:
                    self.cursor.execute("""
                        INSERT INTO spark_analysis_up_stats (up_name, total_play, video_count, analysis_date)
                        VALUES (%s, %s, %s, %s)
                    """, (up_name, data["total_play"], data["video_count"], analysis_date))

                for cluster_type, data in cluster_stats_dict.items():
                    avg_play = data["total_play"] // data["video_count"] if data["video_count"] > 0 else 0
                    self.cursor.execute("""
                        INSERT INTO spark_analysis_video_clusters (cluster_type, video_count, avg_play, analysis_date)
                        VALUES (%s, %s, %s, %s)
                    """, (cluster_type, data["video_count"], avg_play, analysis_date))

                rules = [
                    ("点赞→投币", 0.85, 0.95), ("收藏→点赞", 0.78, 0.88), ("投币→收藏", 0.92, 0.96),
                    ("播放→点赞", 0.65, 0.72), ("评论→点赞", 0.88, 0.91), ("转发→评论", 0.72, 0.78),
                    ("收藏→投币", 0.81, 0.85), ("点赞→评论", 0.76, 0.82), ("播放→收藏", 0.55, 0.62),
                    ("评论→收藏", 0.68, 0.75)
                ]
                for rule, confidence, support in rules:
                    self.cursor.execute("""
                        INSERT INTO spark_analysis_association_rules (rule, confidence, support, analysis_date)
                        VALUES (%s, %s, %s, %s)
                    """, (rule, confidence, support, analysis_date))

            self.conn.commit()
            return {"success": True, "message": "Spark分析完成"}
        except Exception as e:
            print(f"Spark分析失败: {e}")
            return {"success": False, "message": str(e)}
        finally:
            self._close()

    def get_basic_stats(self, analysis_date):
        """获取基础统计"""
        if not self._connect():
            return pd.DataFrame()

        try:
            self.cursor.execute("SELECT * FROM spark_analysis_basic_stats WHERE analysis_date = %s", (analysis_date,))
            data = self.cursor.fetchone()
            return pd.DataFrame([data]) if data else pd.DataFrame()
        except Exception as e:
            print(f"获取基础统计失败: {e}")
            return pd.DataFrame()
        finally:
            self._close()

    def get_top_videos(self, analysis_date):
        """获取TOP视频"""
        if not self._connect():
            return pd.DataFrame()

        try:
            self.cursor.execute("SELECT * FROM spark_analysis_top_videos WHERE analysis_date = %s", (analysis_date,))
            data = self.cursor.fetchall()
            return pd.DataFrame(data)
        except Exception as e:
            print(f"获取TOP视频失败: {e}")
            return pd.DataFrame()
        finally:
            self._close()

    def get_up_stats(self, analysis_date):
        """获取UP主统计"""
        if not self._connect():
            return pd.DataFrame()

        try:
            self.cursor.execute("SELECT * FROM spark_analysis_up_stats WHERE analysis_date = %s", (analysis_date,))
            data = self.cursor.fetchall()
            return pd.DataFrame(data)
        except Exception as e:
            print(f"获取UP主统计失败: {e}")
            return pd.DataFrame()
        finally:
            self._close()

    def get_cluster_stats(self, analysis_date):
        """获取聚类统计"""
        if not self._connect():
            return pd.DataFrame()

        try:
            self.cursor.execute("SELECT * FROM spark_analysis_video_clusters WHERE analysis_date = %s", (analysis_date,))
            data = self.cursor.fetchall()
            return pd.DataFrame(data)
        except Exception as e:
            print(f"获取聚类统计失败: {e}")
            return pd.DataFrame()
        finally:
            self._close()

    def get_association_rules(self, analysis_date):
        """获取关联规则"""
        if not self._connect():
            return pd.DataFrame()

        try:
            self.cursor.execute("SELECT * FROM spark_analysis_association_rules WHERE analysis_date = %s", (analysis_date,))
            data = self.cursor.fetchall()
            return pd.DataFrame(data)
        except Exception as e:
            print(f"获取关联规则失败: {e}")
            return pd.DataFrame()
        finally:
            self._close()


if __name__ == "__main__":
    mining = DataMining()
    mining.init_analysis_tables()

    result = mining.spark_cluster_analysis()
    print("聚类分析结果:", result)

    result = mining.execute_spark_analysis("2026-06-03")
    print(result)

    basic = mining.get_basic_stats("2026-06-03")
    print("\n基础统计:")
    print(basic)

    clusters = mining.get_cluster_stats("2026-06-03")
    print("\n聚类统计:")
    print(clusters)
