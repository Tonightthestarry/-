# -*- coding: utf-8 -*-
"""用户分析模块 - 爬取用户动态、弹幕、视频评论等"""
import requests
import time
import random
import re
from datetime import datetime
from bilibili_spider import BilibiliSpider

MYSQL_CONFIG = {
    "host": "localhost",
    "port": 3306,
    "user": "root",
    "password": "root",
    "database": "ai_know",
    "charset": "utf8mb4"
}

class UserAnalysis(BilibiliSpider):
    def __init__(self):
        super().__init__()
    
    def _connect_mysql(self):
        """连接MySQL"""
        try:
            import pymysql
            conn = pymysql.connect(**MYSQL_CONFIG)
            cursor = conn.cursor(pymysql.cursors.DictCursor)
            return conn, cursor
        except Exception as e:
            print(f"MySQL连接失败: {e}")
            return None, None
    
    def _init_tables(self):
        """初始化用户分析相关表"""
        conn, cursor = self._connect_mysql()
        if not conn:
            return
        
        try:
            cursor.execute("""
                CREATE TABLE IF NOT EXISTS user_profile (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    mid BIGINT NOT NULL COMMENT '用户UID',
                    name VARCHAR(100) NOT NULL COMMENT '用户名',
                    avatar VARCHAR(300) COMMENT '头像URL',
                    level INT COMMENT '用户等级',
                    fans INT DEFAULT 0 COMMENT '粉丝数',
                    followings INT DEFAULT 0 COMMENT '关注数',
                    videos INT DEFAULT 0 COMMENT '投稿数',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """)
            
            cursor.execute("""
                CREATE TABLE IF NOT EXISTS user_dynamics (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    mid BIGINT NOT NULL COMMENT '用户UID',
                    content TEXT COMMENT '动态内容',
                    type VARCHAR(50) COMMENT '动态类型',
                    publish_time DATETIME COMMENT '发布时间',
                    like_count INT DEFAULT 0,
                    comment_count INT DEFAULT 0,
                    repost_count INT DEFAULT 0,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """)
            
            cursor.execute("""
                CREATE TABLE IF NOT EXISTS user_comments (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    mid BIGINT NOT NULL COMMENT '用户UID',
                    video_bvid VARCHAR(50) COMMENT '视频BV号',
                    video_title VARCHAR(500) COMMENT '视频标题',
                    comment TEXT COMMENT '评论内容',
                    like_count INT DEFAULT 0,
                    publish_time DATETIME COMMENT '发布时间',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """)
            
            cursor.execute("""
                CREATE TABLE IF NOT EXISTS user_danmaku (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    mid BIGINT NOT NULL COMMENT '用户UID',
                    video_bvid VARCHAR(50) COMMENT '视频BV号',
                    video_title VARCHAR(500) COMMENT '视频标题',
                    danmaku TEXT COMMENT '弹幕内容',
                    video_time FLOAT COMMENT '弹幕出现的视频时间(秒)',
                    send_time DATETIME COMMENT '发送时间',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """)
            
            conn.commit()
            print("用户分析表初始化成功")
            
        except Exception as e:
            print(f"创建表失败: {e}")
        finally:
            if cursor:
                cursor.close()
            if conn:
                conn.close()
    
    def get_user_info(self, mid):
        """获取用户基本信息"""
        try:
            url = f"https://api.bilibili.com/x/space/wbi/acc/info?mid={mid}"
            response = requests.get(url, headers=self.headers, timeout=15)
            response.raise_for_status()
            data = response.json()
            
            if data.get("code") == 0:
                user_data = data.get("data", {})
                return {
                    "mid": mid,
                    "name": user_data.get("name", ""),
                    "avatar": user_data.get("face", ""),
                    "level": user_data.get("level", 0),
                    "fans": user_data.get("fans", 0),
                    "followings": 0,
                    "videos": user_data.get("archive_count", 0)
                }
        except Exception as e:
            print(f"获取用户信息失败: {e}")
        
        return None
    
    def get_user_videos(self, mid, max_page=5):
        """获取用户投稿视频列表"""
        videos = []
        for page in range(1, max_page + 1):
            try:
                url = f"https://api.bilibili.com/x/space/arc/search?mid={mid}&ps=30&pn={page}"
                response = requests.get(url, headers=self.headers, timeout=15)
                response.raise_for_status()
                data = response.json()
                
                if data.get("code") == 0:
                    list_data = data.get("data", {}).get("list", {}).get("vlist", [])
                    if not list_data:
                        break
                    
                    for item in list_data:
                        videos.append({
                            "bvid": item.get("bvid", ""),
                            "title": item.get("title", ""),
                            "play": item.get("play", 0)
                        })
                    
                    time.sleep(random.uniform(0.3, 0.8))
                else:
                    break
            except Exception as e:
                print(f"获取用户视频第{page}页失败: {e}")
                break
        
        return videos
    
    def get_video_comments(self, bvid, target_mid=None, max_page=10):
        """获取视频评论，可选筛选指定UID"""
        comments = []
        for page in range(1, max_page + 1):
            try:
                url = f"https://api.bilibili.com/x/v2/reply?oid={bvid}&type=1&pn={page}&ps=20"
                response = requests.get(url, headers=self.headers, timeout=15)
                response.raise_for_status()
                data = response.json()
                
                if data.get("code") == 0:
                    replies = data.get("data", {}).get("replies", [])
                    if not replies:
                        break
                    
                    for reply in replies:
                        member_mid = reply.get("member", {}).get("mid", 0)
                        
                        if target_mid is None or str(member_mid) == str(target_mid):
                            comment_info = {
                                "video_bvid": bvid,
                                "mid": member_mid,
                                "comment": reply.get("content", {}).get("message", ""),
                                "like_count": reply.get("like", 0),
                                "publish_time": datetime.fromtimestamp(reply.get("ctime", 0))
                            }
                            comments.append(comment_info)
                    
                    time.sleep(random.uniform(0.2, 0.6))
                else:
                    break
            except Exception as e:
                print(f"获取评论第{page}页失败: {e}")
                break
        
        return comments
    
    def get_user_dynamics(self, mid, max_page=10):
        """获取用户动态"""
        dynamics = []
        for page in range(1, max_page + 1):
            try:
                url = f"https://api.bilibili.com/x/polymer/web-dynamic/v1/feed/space?host_mid={mid}&offset={(page-1)*11}&ps=11"
                response = requests.get(url, headers=self.headers, timeout=15)
                response.raise_for_status()
                data = response.json()
                
                if data.get("code") == 0:
                    items = data.get("data", {}).get("items", [])
                    if not items:
                        break
                    
                    for item in items:
                        content = item.get("modules", {}).get("module_dynamic", {}).get("desc", {}).get("text", "")
                        dynamic_type = item.get("type", "")
                        stat = item.get("modules", {}).get("module_stat", {})
                        
                        dynamics.append({
                            "mid": mid,
                            "content": content,
                            "type": dynamic_type,
                            "like_count": stat.get("like", {}).get("count", 0),
                            "comment_count": stat.get("comment", {}).get("count", 0),
                            "repost_count": stat.get("forward", {}).get("count", 0),
                            "publish_time": datetime.fromtimestamp(item.get("modules", {}).get("module_author", {}).get("pub_ts", 0))
                        })
                    
                    time.sleep(random.uniform(0.3, 0.8))
                else:
                    break
            except Exception as e:
                print(f"获取动态第{page}页失败: {e}")
                break
        
        return dynamics
    
    def save_user_data(self, mid, user_info, dynamics=None, comments=None):
        """保存用户数据到MySQL"""
        conn, cursor = self._connect_mysql()
        if not conn:
            return False
        
        try:
            if user_info:
                cursor.execute("DELETE FROM user_profile WHERE mid = %s", (mid,))
                cursor.execute("""
                    INSERT INTO user_profile (mid, name, avatar, level, fans, followings, videos)
                    VALUES (%s, %s, %s, %s, %s, %s, %s)
                """, (
                    user_info["mid"], user_info["name"], user_info["avatar"],
                    user_info["level"], user_info["fans"], user_info["followings"], user_info["videos"]
                ))
            
            if dynamics:
                cursor.execute("DELETE FROM user_dynamics WHERE mid = %s", (mid,))
                for dyn in dynamics:
                    cursor.execute("""
                        INSERT INTO user_dynamics (mid, content, type, publish_time, like_count, comment_count, repost_count)
                        VALUES (%s, %s, %s, %s, %s, %s, %s)
                    """, (
                        dyn["mid"], dyn["content"], dyn["type"], dyn["publish_time"],
                        dyn["like_count"], dyn["comment_count"], dyn["repost_count"]
                    ))
            
            if comments:
                cursor.execute("DELETE FROM user_comments WHERE mid = %s", (mid,))
                for comment in comments:
                    cursor.execute("""
                        INSERT INTO user_comments (mid, video_bvid, video_title, comment, like_count, publish_time)
                        VALUES (%s, %s, %s, %s, %s, %s)
                    """, (
                        comment["mid"], comment["video_bvid"], comment.get("video_title", ""),
                        comment["comment"], comment["like_count"], comment["publish_time"]
                    ))
            
            conn.commit()
            return True
        except Exception as e:
            print(f"保存用户数据失败: {e}")
            return False
        finally:
            if cursor:
                cursor.close()
            if conn:
                conn.close()
    
    def analyze_user(self, mid, max_videos=5, max_comments_per_video=5):
        """综合分析用户（分而治之思想）"""
        self._init_tables()
        
        result = {"success": False, "message": ""}
        
        try:
            print(f"开始分析用户 UID: {mid}")
            
            # 1. 获取用户基本信息
            user_info = self.get_user_info(mid)
            if not user_info:
                result["message"] = "获取用户信息失败"
                return result
            
            print(f"用户信息: {user_info['name']}")
            
            # 2. 获取用户动态
            print("正在获取用户动态...")
            dynamics = self.get_user_dynamics(mid)
            print(f"获取到 {len(dynamics)} 条动态")
            
            # 3. 获取用户视频，并爬取自己视频下的评论
            print("正在获取用户视频...")
            videos = self.get_user_videos(mid, max_page=2)
            
            all_comments = []
            for video in videos[:max_videos]:
                print(f"正在爬取视频 {video['title']} 的评论...")
                comments = self.get_video_comments(video["bvid"], target_mid=mid, max_page=max_comments_per_video)
                for comment in comments:
                    comment["video_title"] = video["title"]
                all_comments.extend(comments)
                time.sleep(random.uniform(0.3, 0.6))
            
            print(f"获取到 {len(all_comments)} 条用户评论")
            
            # 4. 保存数据
            print("正在保存数据...")
            self.save_user_data(mid, user_info, dynamics, all_comments)
            
            result["success"] = True
            result["message"] = f"分析完成！用户: {user_info['name']}，动态: {len(dynamics)} 条，评论: {len(all_comments)} 条"
            result["user_info"] = user_info
            result["dynamics_count"] = len(dynamics)
            result["comments_count"] = len(all_comments)
            
        except Exception as e:
            result["message"] = str(e)
            print(f"分析用户失败: {e}")
        
        return result

if __name__ == "__main__":
    analyzer = UserAnalysis()
    test_mid = "667356508"
    result = analyzer.analyze_user(test_mid)
    print(result)
