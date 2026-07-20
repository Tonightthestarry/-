"""MySQL 连接模块 - 业务数据存储（用户、事件记录、URL检测日志）"""
import pymysql
from contextlib import contextmanager
from datetime import datetime
import os
import sys
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

MYSQL_CONFIG = {
    "host": "localhost",
    "port": 3306,
    "user": "root",
    "password": "root",
    "database": "campus_monitor",
    "charset": "utf8mb4"
}


def get_connection():
    """获取 MySQL 连接"""
    return pymysql.connect(**MYSQL_CONFIG)


@contextmanager
def get_cursor():
    """获取游标的上下文管理器，自动提交和关闭"""
    conn = get_connection()
    try:
        cursor = conn.cursor()
        yield cursor
        conn.commit()
    except Exception as e:
        conn.rollback()
        raise e
    finally:
        cursor.close()
        conn.close()


def init_db():
    """初始化数据库表"""
    create_sql = """
    CREATE TABLE IF NOT EXISTS users (
        id INT AUTO_INCREMENT PRIMARY KEY,
        username VARCHAR(50) NOT NULL UNIQUE,
        password VARCHAR(100) NOT NULL,
        role ENUM('admin', 'user') DEFAULT 'user',
        create_time DATETIME DEFAULT CURRENT_TIMESTAMP
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

    CREATE TABLE IF NOT EXISTS event_records (
        id INT AUTO_INCREMENT PRIMARY KEY,
        event_type VARCHAR(30) NOT NULL COMMENT '体测/活动/求助/失物招领/社团/课程评价/投诉/其他',
        title VARCHAR(200) NOT NULL,
        content TEXT,
        campus_area VARCHAR(50) COMMENT '教学楼/宿舍/食堂/图书馆/操场/体育馆/其他',
        student_name VARCHAR(50),
        student_id VARCHAR(30),
        heat_score INT DEFAULT 0 COMMENT '热度分',
        sentiment VARCHAR(20) COMMENT 'positive/neutral/negative',
        status VARCHAR(20) DEFAULT 'normal' COMMENT 'normal/flagged/anomaly',
        source VARCHAR(30) DEFAULT 'simulated' COMMENT 'simulated/url_crawl/manual',
        url VARCHAR(500),
        create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
        INDEX idx_type (event_type),
        INDEX idx_area (campus_area),
        INDEX idx_time (create_time),
        INDEX idx_source (source)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

    CREATE TABLE IF NOT EXISTS url_scan_logs (
        id INT AUTO_INCREMENT PRIMARY KEY,
        url VARCHAR(500) NOT NULL,
        status ENUM('success','failed','timeout') DEFAULT 'success',
        title TEXT COMMENT '网页标题',
        content_preview TEXT COMMENT '内容摘要(前500字)',
        risk_level ENUM('safe','low','medium','high','critical') DEFAULT 'safe',
        sensitive_words TEXT COMMENT '命中的敏感词(逗号分隔)',
        scan_time DATETIME DEFAULT CURRENT_TIMESTAMP,
        INDEX idx_risk (risk_level),
        INDEX idx_time (scan_time)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
    """
    with get_cursor() as cursor:
        for stmt in create_sql.split(';'):
            stmt = stmt.strip()
            if stmt:
                cursor.execute(stmt)

    # 插入默认管理员
    try:
        with get_cursor() as cursor:
            cursor.execute(
                "INSERT IGNORE INTO users (username, password, role) VALUES (%s, %s, %s)",
                ("admin", "admin123", "admin")
            )
            cursor.execute(
                "INSERT IGNORE INTO users (username, password, role) VALUES (%s, %s, %s)",
                ("user1", "123456", "user")
            )
    except Exception:
        pass
    print("[MySQL] 数据库表初始化完成")


# ========== 用户 CRUD ==========
def get_all_users():
    with get_cursor() as c:
        c.execute("SELECT id, username, role, create_time FROM users ORDER BY id")
        cols = ['id', 'username', 'role', 'create_time']
        return [dict(zip(cols, row)) for row in c.fetchall()]


def add_user(username, password, role='user'):
    with get_cursor() as c:
        c.execute("INSERT INTO users (username, password, role) VALUES (%s,%s,%s)", (username, password, role))
        return c.lastrowid


def update_user_role(user_id, new_role):
    with get_cursor() as c:
        c.execute("UPDATE users SET role=%s WHERE id=%s", (new_role, user_id))
        return c.rowcount


def update_user_password(user_id, new_password):
    with get_cursor() as c:
        c.execute("UPDATE users SET password=%s WHERE id=%s", (new_password, user_id))
        return c.rowcount


def delete_user(user_id):
    with get_cursor() as c:
        c.execute("DELETE FROM users WHERE id=%s AND role!='admin'", (user_id,))
        return c.rowcount


def verify_user(username, password):
    with get_cursor() as c:
        c.execute("SELECT id, username, role FROM users WHERE username=%s AND password=%s", (username, password))
        row = c.fetchone()
        if row:
            return {'id': row[0], 'username': row[1], 'role': row[2]}
        return None


# ========== 事件记录 CRUD ==========
def insert_event_record(record: dict):
    sql = """INSERT INTO event_records (event_type, title, content, campus_area, student_name, student_id, heat_score, sentiment, status, source, url, create_time)
             VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)"""
    with get_cursor() as c:
        c.execute(sql, (
            record.get('event_type', '其他'),
            record.get('title', ''),
            record.get('content', ''),
            record.get('campus_area', ''),
            record.get('student_name', ''),
            record.get('student_id', ''),
            record.get('heat_score', 0),
            record.get('sentiment', 'neutral'),
            record.get('status', 'normal'),
            record.get('source', 'simulated'),
            record.get('url', ''),
            record.get('create_time', datetime.now().strftime('%Y-%m-%d %H:%M:%S'))
        ))
        return c.lastrowid


def get_events_by_date(date_str: str, limit=500):
    with get_cursor() as c:
        c.execute("""SELECT id, event_type, title, content, campus_area, student_name, heat_score, sentiment, status, source, create_time 
                     FROM event_records WHERE DATE(create_time)=%s ORDER BY create_time DESC LIMIT %s""",
                  (date_str, limit))
        cols = ['id', 'event_type', 'title', 'content', 'campus_area', 'student_name', 'heat_score', 'sentiment', 'status', 'source', 'create_time']
        return [dict(zip(cols, row)) for row in c.fetchall()]


def get_event_stats(date_str: str):
    """按类型统计今日事件数"""
    with get_cursor() as c:
        c.execute("""SELECT event_type, COUNT(*) as cnt FROM event_records 
                     WHERE DATE(create_time)=%s GROUP BY event_type""", (date_str,))
        return [{'event_type': r[0], 'count': r[1]} for r in c.fetchall()]


def get_event_area_stats(date_str: str):
    """按区域统计"""
    with get_cursor() as c:
        c.execute("""SELECT campus_area, COUNT(*) as cnt FROM event_records 
                     WHERE DATE(create_time)=%s GROUP BY campus_area""", (date_str,))
        return [{'area': r[0], 'count': r[1]} for r in c.fetchall()]


def get_event_hourly(date_str: str):
    """按小时统计24h事件分布"""
    with get_cursor() as c:
        c.execute("""SELECT HOUR(create_time) as h, COUNT(*) as cnt FROM event_records 
                     WHERE DATE(create_time)=%s GROUP BY HOUR(create_time) ORDER BY h""", (date_str,))
        result = {h: 0 for h in range(24)}
        for r in c.fetchall():
            result[r[0]] = r[1]
        return result


def get_events_last_7days():
    """最近7天每日事件数"""
    with get_cursor() as c:
        c.execute("""SELECT DATE(create_time) as d, COUNT(*) as cnt FROM event_records 
                     WHERE create_time >= DATE_SUB(CURDATE(), INTERVAL 7 DAY) GROUP BY DATE(create_time) ORDER BY d""")
        return [{'date': str(r[0]), 'count': r[1]} for r in c.fetchall()]


def get_event_count():
    with get_cursor() as c:
        c.execute("SELECT COUNT(*) FROM event_records")
        return c.fetchone()[0]


# ========== URL扫描日志 ==========
def insert_scan_log(log: dict):
    sql = """INSERT INTO url_scan_logs (url, status, title, content_preview, risk_level, sensitive_words, scan_time)
             VALUES (%s,%s,%s,%s,%s,%s,%s)"""
    with get_cursor() as c:
        c.execute(sql, (
            log.get('url', ''),
            log.get('status', 'success'),
            log.get('title', ''),
            log.get('content_preview', ''),
            log.get('risk_level', 'safe'),
            ','.join(log.get('sensitive_words', [])),
            log.get('scan_time', datetime.now().strftime('%Y-%m-%d %H:%M:%S'))
        ))
        return c.lastrowid


def get_scan_logs(limit=50):
    with get_cursor() as c:
        c.execute("SELECT * FROM url_scan_logs ORDER BY scan_time DESC LIMIT %s", (limit,))
        cols = ['id', 'url', 'status', 'title', 'content_preview', 'risk_level', 'sensitive_words', 'scan_time']
        return [dict(zip(cols, row)) for row in c.fetchall()]


def get_scan_stats():
    """扫描统计"""
    with get_cursor() as c:
        c.execute("SELECT risk_level, COUNT(*) FROM url_scan_logs GROUP BY risk_level")
        return {r[0]: r[1] for r in c.fetchall()}
