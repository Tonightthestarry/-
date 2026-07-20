# -*- coding: utf-8 -*-
"""评论分析模块 - 爬取视频/动态评论 + 词云图 + 游戏成分分析"""
import requests
import time
import random
import re
from datetime import datetime
from collections import Counter
import io
import base64
from bilibili_spider import BilibiliSpider

MYSQL_CONFIG = {
    "host": "localhost",
    "port": 3306,
    "user": "root",
    "password": "root",
    "database": "ai_know",
    "charset": "utf8mb4"
}

# 游戏关键词库
GAME_KEYWORDS = {
    "原神": ["原神", "原神启动", "米哈游", "mhy", "旅行者", "钟离", "芙宁娜"],
    "王者荣耀": ["王者", "王者荣耀", "农", "王者荣耀启动", "澜朋友"],
    "英雄联盟": ["LOL", "英雄联盟", "lol", "召唤师", "ADC"],
    "崩坏": ["崩坏", "崩铁", "铁道", "崩坏星穹铁道", "芽衣"],
    "永劫无间": ["永劫", "永劫无间", "天海", "迦南"],
    "CSGO": ["CSGO", "csgo", "CS2", "cs2", "反恐精英"],
    "DOTA": ["DOTA", "dota", "刀塔", "TI"],
    "和平精英": ["和平精英", "吃鸡", "刺激战场"],
    "我的世界": ["MC", "我的世界", "minecraft", "Minecraft", "方块人"],
    "艾尔登法环": ["艾尔登法环", "老头环", "法环"],
    "黑神话悟空": ["黑神话", "黑神话悟空", "悟空"],
    "其他游戏": ["游戏", "steam", "Steam", "游戏里", "玩游戏"]
}

class CommentAnalysis(BilibiliSpider):
    def __init__(self):
        super().__init__()
        self._init_tables()
    
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
        """初始化评论分析相关表"""
        conn, cursor = self._connect_mysql()
        if not conn:
            return
        
        try:
            cursor.execute("""
                CREATE TABLE IF NOT EXISTS video_comments (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    video_bvid VARCHAR(50) NOT NULL COMMENT '视频BV号',
                    video_title VARCHAR(500) COMMENT '视频标题',
                    comment_id BIGINT COMMENT '评论ID',
                    user_mid BIGINT COMMENT '用户UID',
                    user_name VARCHAR(100) COMMENT '用户名',
                    content TEXT COMMENT '评论内容',
                    like_count INT DEFAULT 0,
                    publish_time DATETIME COMMENT '发布时间',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """)
            
            cursor.execute("""
                CREATE TABLE IF NOT EXISTS comment_wordcloud (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    video_bvid VARCHAR(50) NOT NULL COMMENT '视频BV号',
                    word VARCHAR(100) NOT NULL COMMENT '词',
                    count INT DEFAULT 0 COMMENT '词频',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """)
            
            cursor.execute("""
                CREATE TABLE IF NOT EXISTS game_analysis (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    video_bvid VARCHAR(50) NOT NULL COMMENT '视频BV号',
                    game_name VARCHAR(100) NOT NULL COMMENT '游戏名',
                    count INT DEFAULT 0 COMMENT '提及次数',
                    percentage FLOAT DEFAULT 0 COMMENT '占比',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """)
            
            conn.commit()
            print("评论分析表初始化成功")
            
        except Exception as e:
            print(f"创建表失败: {e}")
        finally:
            if cursor:
                cursor.close()
            if conn:
                conn.close()
    
    def parse_link(self, url):
        """解析链接，返回BV号或动态ID"""
        try:
            if "/video/" in url:
                match = re.search(r'BV[a-zA-Z0-9]+', url)
                if match:
                    return {"type": "video", "id": match.group(0)}
            return None
        except:
            return None
    
    def get_video_comments(self, bvid, max_page=20):
        """爬取视频所有评论"""
        comments = []
        for page in range(1, max_page + 1):
            try:
                url = f"https://api.bilibili.com/x/v2/reply?oid={bvid}&type=1&pn={page}&ps=49"
                response = requests.get(url, headers=self.headers, timeout=15)
                response.raise_for_status()
                data = response.json()
                
                if data.get("code") == 0:
                    replies = data.get("data", {}).get("replies", [])
                    if not replies:
                        break
                    
                    for reply in replies:
                        member = reply.get("member", {})
                        comment_info = {
                            "video_bvid": bvid,
                            "comment_id": reply.get("rpid", 0),
                            "user_mid": member.get("mid", 0),
                            "user_name": member.get("uname", ""),
                            "content": reply.get("content", {}).get("message", ""),
                            "like_count": reply.get("like", 0),
                            "publish_time": datetime.fromtimestamp(reply.get("ctime", 0))
                        }
                        comments.append(comment_info)
                        
                        if reply.get("replies"):
                            for sub_reply in reply.get("replies"):
                                sub_member = sub_reply.get("member", {})
                                sub_comment = {
                                    "video_bvid": bvid,
                                    "comment_id": sub_reply.get("rpid", 0),
                                    "user_mid": sub_member.get("mid", 0),
                                    "user_name": sub_member.get("uname", ""),
                                    "content": sub_reply.get("content", {}).get("message", ""),
                                    "like_count": sub_reply.get("like", 0),
                                    "publish_time": datetime.fromtimestamp(sub_reply.get("ctime", 0))
                                }
                                comments.append(sub_comment)
                    
                    time.sleep(random.uniform(0.2, 0.5))
                else:
                    break
            except Exception as e:
                print(f"获取评论第{page}页失败: {e}")
                break
        
        return comments
    
    def get_video_info(self, bvid):
        """获取视频标题"""
        try:
            url = f"https://api.bilibili.com/x/web-interface/view?bvid={bvid}"
            response = requests.get(url, headers=self.headers, timeout=15)
            response.raise_for_status()
            data = response.json()
            
            if data.get("code") == 0:
                return {
                    "title": data.get("data", {}).get("title", "")
                }
        except Exception as e:
            print(f"获取视频信息失败: {e}")
        
        return {"title": ""}
    
    def analyze_game_content(self, comments):
        """分析评论中的游戏成分"""
        game_counts = {}
        total_game = 0
        
        for game, keywords in GAME_KEYWORDS.items():
            count = 0
            for comment in comments:
                content = comment.get("content", "")
                for keyword in keywords:
                    if keyword in content:
                        count += 1
                        break
            if count > 0:
                game_counts[game] = count
                total_game += count
        
        result = {}
        for game, count in sorted(game_counts.items(), key=lambda x: -x[1]):
            percentage = round(count / total_game * 100, 2) if total_game > 0 else 0
            result[game] = {
                "count": count,
                "percentage": percentage
            }
        
        return result, total_game
    
    def get_wordcloud_data(self, comments):
        """获取词频统计数据"""
        all_text = " ".join([c.get("content", "") for c in comments])
        all_text = re.sub(r'[^\u4e00-\u9fa5a-zA-Z0-9]', ' ', all_text)
        
        words = all_text.split()
        words = [w for w in words if len(w) >= 2]
        
        word_count = Counter(words)
        
        return word_count.most_common(100)
    
    def generate_wordcloud_image(self, comments):
        """生成词云图（纯Python实现，避免依赖）"""
        try:
            import matplotlib
            matplotlib.use('Agg')
            import matplotlib.pyplot as plt
            
            word_count = self.get_wordcloud_data(comments)
            if not word_count:
                return None
            
            words = [w[0] for w in word_count[:30]]
            counts = [w[1] for w in word_count[:30]]
            
            plt.figure(figsize=(10, 6))
            plt.barh(words[::-1], counts[::-1])
            plt.title("评论热词TOP30")
            plt.xlabel("词频")
            plt.tight_layout()
            
            buf = io.BytesIO()
            plt.savefig(buf, format='png')
            buf.seek(0)
            img_base64 = base64.b64encode(buf.getvalue()).decode('utf-8')
            plt.close()
            
            return img_base64
        except:
            return None
    
    def save_comments(self, bvid, video_title, comments):
        """保存评论到数据库"""
        conn, cursor = self._connect_mysql()
        if not conn:
            return False
        
        try:
            cursor.execute("DELETE FROM video_comments WHERE video_bvid = %s", (bvid,))
            for comment in comments:
                cursor.execute("""
                    INSERT INTO video_comments (video_bvid, video_title, comment_id, user_mid, user_name, content, like_count, publish_time)
                    VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
                """, (
                    bvid, video_title,
                    comment["comment_id"], comment["user_mid"], comment["user_name"],
                    comment["content"], comment["like_count"], comment["publish_time"]
                ))
            
            word_data = self.get_wordcloud_data(comments)
            cursor.execute("DELETE FROM comment_wordcloud WHERE video_bvid = %s", (bvid,))
            for word, count in word_data[:50]:
                cursor.execute("""
                    INSERT INTO comment_wordcloud (video_bvid, word, count)
                    VALUES (%s, %s, %s)
                """, (bvid, word, count))
            
            game_result, _ = self.analyze_game_content(comments)
            cursor.execute("DELETE FROM game_analysis WHERE video_bvid = %s", (bvid,))
            for game, data in game_result.items():
                cursor.execute("""
                    INSERT INTO game_analysis (video_bvid, game_name, count, percentage)
                    VALUES (%s, %s, %s, %s)
                """, (bvid, game, data["count"], data["percentage"]))
            
            conn.commit()
            return True
        except Exception as e:
            print(f"保存评论失败: {e}")
            return False
        finally:
            if cursor:
                cursor.close()
            if conn:
                conn.close()
    
    def analyze_link(self, url, max_page=20):
        """分析链接入口"""
        parsed = self.parse_link(url)
        if not parsed:
            return {"success": False, "message": "无法解析链接，请输入有效的B站视频链接"}
        
        result = {"success": False, "message": ""}
        
        try:
            if parsed["type"] == "video":
                bvid = parsed["id"]
                print(f"开始分析视频: {bvid}")
                
                video_info = self.get_video_info(bvid)
                print(f"视频标题: {video_info['title']}")
                
                print("正在爬取评论...")
                comments = self.get_video_comments(bvid, max_page)
                print(f"获取到 {len(comments)} 条评论")
                
                print("正在分析游戏成分...")
                game_result, total_game = self.analyze_game_content(comments)
                
                print("正在生成词云...")
                wordcloud_data = self.get_wordcloud_data(comments)
                
                print("正在保存数据...")
                self.save_comments(bvid, video_info["title"], comments)
                
                result["success"] = True
                result["message"] = f"分析完成！共 {len(comments)} 条评论"
                result["video_info"] = video_info
                result["comments_count"] = len(comments)
                result["game_analysis"] = game_result
                result["total_game"] = total_game
                result["wordcloud_top"] = wordcloud_data[:20]
            
            return result
        
        except Exception as e:
            result["message"] = str(e)
            print(f"分析失败: {e}")
            return result

if __name__ == "__main__":
    analyzer = CommentAnalysis()
    test_url = "https://www.bilibili.com/video/BV1xx4y167iX"
    result = analyzer.analyze_link(test_url)
    print(result)
