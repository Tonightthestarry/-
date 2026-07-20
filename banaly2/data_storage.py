# -*- coding: utf-8 -*-
"""数据存储模块 - MySQL+MongoDB双库存储（按天分表）"""
import pymysql
from pymongo import MongoClient
from datetime import datetime
import re

# MySQL配置（来自用户提供的配置）
MYSQL_CONFIG = {
    "host": "localhost",
    "port": 3306,
    "user": "root",
    "password": "root",
    "database": "ai_know",
    "charset": "utf8mb4"
}

# MongoDB配置（来自用户提供的配置）
MONGO_CONFIG = {
    "host": "localhost",
    "port": 27017,
    "db_name": "bilibili_analysis",
    "coll_name": "mingchao_video_data"
}

class DataStorage:
    def __init__(self):
        self.mysql_conn = None
        self.mysql_cursor = None
        self.mongo_client = None
    
    def _connect_mysql(self):
        """连接MySQL"""
        try:
            self.mysql_conn = pymysql.connect(**MYSQL_CONFIG)
            self.mysql_cursor = self.mysql_conn.cursor()
            return True
        except Exception as e:
            print(f"MySQL连接失败: {e}")
            return False
    
    def _connect_mongo(self):
        """连接MongoDB"""
        try:
            self.mongo_client = MongoClient(MONGO_CONFIG["host"], MONGO_CONFIG["port"])
            return True
        except Exception as e:
            print(f"MongoDB连接失败: {e}")
            return False
    
    def _close_mysql(self):
        """关闭MySQL连接"""
        if self.mysql_cursor:
            try:
                self.mysql_cursor.close()
            except:
                pass
        if self.mysql_conn:
            try:
                self.mysql_conn.close()
            except:
                pass
    
    def _close_mongo(self):
        """关闭MongoDB连接"""
        if self.mongo_client:
            try:
                self.mongo_client.close()
            except:
                pass
    
    def _get_table_name(self, date_str=None):
        """获取按天分隔的表名，格式：bilibili_videos_YYYYMMDD"""
        if date_str is None:
            date_str = datetime.now().strftime("%Y%m%d")
        return f"bilibili_videos_{date_str}"
    
    def _create_daily_table(self, table_name):
        """创建指定日期的数据表"""
        if not re.match(r'^bilibili_videos_\d{8}$', table_name):
            print(f"无效的表名格式: {table_name}")
            return False
        
        create_table_sql = f"""
        CREATE TABLE IF NOT EXISTS {table_name} (
            id INT AUTO_INCREMENT PRIMARY KEY,
            title VARCHAR(500) NOT NULL COMMENT '标题',
            play INT NOT NULL DEFAULT 0 COMMENT '播放量',
            `like` INT NOT NULL DEFAULT 0 COMMENT '点赞数',
            coin INT NOT NULL DEFAULT 0 COMMENT '投币数',
            favorite INT NOT NULL DEFAULT 0 COMMENT '收藏数',
            up_name VARCHAR(100) NOT NULL COMMENT 'UP主',
            pubdate VARCHAR(20) COMMENT '发布时间',
            video_url VARCHAR(200) COMMENT '视频链接',
            `desc` TEXT COMMENT '简介',
            comment INT NOT NULL DEFAULT 0 COMMENT '评论数',
            share INT NOT NULL DEFAULT 0 COMMENT '转发数',
            cluster_label INT DEFAULT NULL COMMENT '聚类标签0/1/2',
            cluster_type VARCHAR(50) DEFAULT NULL COMMENT '聚类类别：冷门/普通/热门/爆款',
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间'
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
        """
        
        try:
            self.mysql_cursor.execute(create_table_sql)
            self.mysql_conn.commit()
            return True
        except Exception as e:
            print(f"创建表失败 {table_name}: {e}")
            return False
    
    def ensure_tables(self):
        """确保数据表存在（创建当天的分表）"""
        if not self._connect_mysql():
            return False
        
        try:
            table_name = self._get_table_name()
            result = self._create_daily_table(table_name)
            return result
        finally:
            self._close_mysql()
    
    def save_to_mysql(self, video_list):
        """保存数据到MySQL（按发布日期分表存储）"""
        if not video_list:
            return False
        
        if not self._connect_mysql():
            return False
        
        try:
            videos_by_date = {}
            
            for video in video_list:
                pubdate = video.get("发布时间", "")
                if not pubdate:
                    pubdate = datetime.now().strftime("%Y-%m-%d")
                
                date_key = pubdate.replace("-", "")
                if date_key not in videos_by_date:
                    videos_by_date[date_key] = []
                videos_by_date[date_key].append(video)
            
            total_saved = 0
            for date_key, videos in videos_by_date.items():
                table_name = f"bilibili_videos_{date_key}"
                self._create_daily_table(table_name)
                
                insert_sql = f"""
                INSERT INTO {table_name} 
                (title, play, `like`, coin, favorite, up_name, pubdate, video_url, `desc`, comment, share, cluster_label, cluster_type)
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
                ON DUPLICATE KEY UPDATE
                play=VALUES(play), `like`=VALUES(`like`), coin=VALUES(coin), favorite=VALUES(favorite),
                comment=VALUES(comment), share=VALUES(share), cluster_label=VALUES(cluster_label), cluster_type=VALUES(cluster_type);
                """
                
                data_rows = []
                for video in videos:
                    row = (
                        video.get("标题", ""),
                        video.get("播放量", 0),
                        video.get("点赞数", 0),
                        video.get("投币数", 0),
                        video.get("收藏数", 0),
                        video.get("UP主", ""),
                        video.get("发布时间", ""),
                        video.get("视频链接", ""),
                        video.get("简介", ""),
                        video.get("评论数", 0),
                        video.get("转发数", 0),
                        video.get("聚类标签", 0),
                        video.get("聚类类别", "普通视频")
                    )
                    data_rows.append(row)
                
                self.mysql_cursor.executemany(insert_sql, data_rows)
                self.mysql_conn.commit()
                saved_count = len(data_rows)
                total_saved += saved_count
                print(f"MySQL保存成功，{saved_count}条存储到表: {table_name}")
            
            print(f"MySQL保存完成，共{total_saved}条")
            return True
        except Exception as e:
            print(f"MySQL保存失败: {e}")
            return False
        finally:
            self._close_mysql()
    
    def save_to_mongodb(self, video_list):
        """保存数据到MongoDB"""
        if not video_list:
            return False
        
        if not self._connect_mongo():
            return False
        
        try:
            db = self.mongo_client[MONGO_CONFIG["db_name"]]
            coll = db[MONGO_CONFIG["coll_name"]]
            
            data_with_cluster = []
            for video in video_list:
                video_data = {
                    "标题": video.get("标题", ""),
                    "播放量": video.get("播放量", 0),
                    "点赞数": video.get("点赞数", 0),
                    "投币数": video.get("投币数", 0),
                    "收藏数": video.get("收藏数", 0),
                    "UP主": video.get("UP主", ""),
                    "发布时间": video.get("发布时间", ""),
                    "视频链接": video.get("视频链接", ""),
                    "简介": video.get("简介", ""),
                    "评论数": video.get("评论数", 0),
                    "转发数": video.get("转发数", 0),
                    "聚类标签": video.get("聚类标签", 0),
                    "聚类类别": video.get("聚类类别", "普通视频"),
                    "created_at": datetime.now()
                }
                data_with_cluster.append(video_data)
            
            coll.insert_many(data_with_cluster)
            print(f"MongoDB保存成功，共{len(video_list)}条")
            return True
        except Exception as e:
            print(f"MongoDB保存失败: {e}")
            return False
        finally:
            self._close_mongo()
    
    def save_videos(self, video_list):
        """保存视频数据到双库（MySQL为主，MongoDB为辅助）"""
        mysql_ok = self.save_to_mysql(video_list)
        mongo_ok = self.save_to_mongodb(video_list)
        
        if mysql_ok:
            if mongo_ok:
                return {"success": True, "mysql": True, "mongo": True, "message": "双库保存成功"}
            else:
                return {"success": True, "mysql": True, "mongo": False, "message": "MySQL保存成功，MongoDB保存失败"}
        return {"success": False, "mysql": False, "mongo": mongo_ok, "message": "MySQL保存失败"}
    
    def _get_all_daily_tables(self):
        """获取所有分表列表"""
        tables = []
        try:
            self.mysql_cursor.execute("SHOW TABLES LIKE 'bilibili_videos_%'")
            results = self.mysql_cursor.fetchall()
            for row in results:
                tables.append(row[0])
        except Exception as e:
            print(f"获取分表列表失败: {e}")
        return tables
        
    def get_video_count(self):
        """获取数据库中的视频数量（汇总旧表和所有分表）"""
        mysql_count = 0
        mongo_count = 0
        
        if self._connect_mysql():
            try:
                # 统计旧的 bilibili_videos 表
                self.mysql_cursor.execute("SELECT COUNT(*) FROM bilibili_videos")
                mysql_count += self.mysql_cursor.fetchone()[0]
                
                # 统计所有分表
                tables = self._get_all_daily_tables()
                for table in tables:
                    self.mysql_cursor.execute(f"SELECT COUNT(*) FROM {table}")
                    mysql_count += self.mysql_cursor.fetchone()[0]
            except Exception as e:
                print(f"获取MySQL数量失败: {e}")
            finally:
                self._close_mysql()
        
        if self._connect_mongo():
            try:
                db = self.mongo_client[MONGO_CONFIG["db_name"]]
                coll = db[MONGO_CONFIG["coll_name"]]
                mongo_count = coll.count_documents({})
            except Exception as e:
                print(f"获取MongoDB数量失败: {e}")
            finally:
                self._close_mongo()
        
        return {"mysql": mysql_count, "mongo": mongo_count}
    
    def get_all_videos(self, limit=100):
        """获取所有视频数据（从旧表和所有分表中查询）"""
        videos = []
        
        if self._connect_mysql():
            try:
                self.mysql_cursor = self.mysql_conn.cursor(pymysql.cursors.DictCursor)
                remaining = limit
                
                # 先从旧的 bilibili_videos 表获取数据
                self.mysql_cursor.execute(f"SELECT * FROM bilibili_videos ORDER BY id DESC LIMIT {remaining}")
                old_table_videos = self.mysql_cursor.fetchall()
                videos.extend(old_table_videos)
                remaining -= len(old_table_videos)
                
                # 再从所有分表获取数据
                tables = self._get_all_daily_tables()
                tables.sort(reverse=True)
                
                for table in tables:
                    if remaining <= 0:
                        break
                    self.mysql_cursor.execute(f"SELECT * FROM {table} ORDER BY id DESC LIMIT {remaining}")
                    table_videos = self.mysql_cursor.fetchall()
                    videos.extend(table_videos)
                    remaining -= len(table_videos)
            except Exception as e:
                print(f"查询视频数据失败: {e}")
            finally:
                self._close_mysql()
        
        return videos
    
    def get_cluster_stats(self):
        """获取聚类统计（汇总旧表和所有分表）"""
        stats = {}
        
        if self._connect_mysql():
            try:
                self.mysql_cursor = self.mysql_conn.cursor(pymysql.cursors.DictCursor)
                
                # 先统计旧的 bilibili_videos 表
                self.mysql_cursor.execute("""
                    SELECT cluster_type, COUNT(*) as count, AVG(play) as avg_play 
                    FROM bilibili_videos 
                    WHERE cluster_type IS NOT NULL 
                    GROUP BY cluster_type
                """)
                table_stats = self.mysql_cursor.fetchall()
                for row in table_stats:
                    cluster_type = row['cluster_type']
                    if cluster_type not in stats:
                        stats[cluster_type] = {'count': 0, 'total_play': 0}
                    stats[cluster_type]['count'] += row['count']
                    stats[cluster_type]['total_play'] += row['count'] * row['avg_play']
                
                # 再统计所有分表
                tables = self._get_all_daily_tables()
                for table in tables:
                    self.mysql_cursor.execute(f"""
                        SELECT cluster_type, COUNT(*) as count, AVG(play) as avg_play 
                        FROM {table} 
                        WHERE cluster_type IS NOT NULL 
                        GROUP BY cluster_type
                    """)
                    table_stats = self.mysql_cursor.fetchall()
                    for row in table_stats:
                        cluster_type = row['cluster_type']
                        if cluster_type not in stats:
                            stats[cluster_type] = {'count': 0, 'total_play': 0}
                        stats[cluster_type]['count'] += row['count']
                        stats[cluster_type]['total_play'] += row['count'] * row['avg_play']
                
                result = []
                for cluster_type, data in stats.items():
                    result.append({
                        'cluster_type': cluster_type,
                        'count': data['count'],
                        'avg_play': int(data['total_play'] / data['count']) if data['count'] > 0 else 0
                    })
            except Exception as e:
                print(f"获取聚类统计失败: {e}")
                result = []
            finally:
                self._close_mysql()
        
        return result

if __name__ == "__main__":
    storage = DataStorage()
    storage.ensure_tables()
    print("数据存储模块初始化完成")
    stats = storage.get_video_count()
    print(f"当前数据量 - MySQL: {stats['mysql']}, MongoDB: {stats['mongo']}")