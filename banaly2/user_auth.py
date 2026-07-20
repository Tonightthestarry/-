# -*- coding: utf-8 -*-
"""用户认证模块 - 使用MySQL验证用户登录（管理员/普通用户区分）"""
import pymysql

MYSQL_CONFIG = {
    "host": "localhost",
    "port": 3306,
    "user": "root",
    "password": "root",
    "database": "ai_know",
    "charset": "utf8mb4"
}

class UserAuth:
    def __init__(self):
        self.conn = None
        self.cursor = None
    
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
            self.cursor.close()
        if self.conn:
            self.conn.close()
    
    def verify_user(self, username, password):
        """验证用户登录（明文密码比对）"""
        if not self._connect():
            return {"success": False, "role": None, "username": None}
        
        try:
            self.cursor.execute("SELECT * FROM users WHERE username=%s AND password=%s", (username, password))
            user = self.cursor.fetchone()
            if user:
                return {"success": True, "role": user['role'], "username": user['username']}
            return {"success": False, "role": None, "username": None}
        except Exception as e:
            print(f"用户验证失败: {e}")
            return {"success": False, "role": None, "username": None}
        finally:
            self._close()
    
    def get_user_info(self, username):
        """获取用户信息"""
        if not self._connect():
            return None
        
        try:
            self.cursor.execute("SELECT * FROM users WHERE username=%s", (username,))
            return self.cursor.fetchone()
        except Exception as e:
            print(f"获取用户信息失败: {e}")
            return None
        finally:
            self._close()
    
    def register_user(self, username, password, role="user"):
        """注册新用户"""
        if not self._connect():
            return {"success": False, "message": "数据库连接失败"}
        
        try:
            self.cursor.execute("INSERT INTO users (username, password, role) VALUES (%s, %s, %s)", 
                              (username, password, role))
            self.conn.commit()
            return {"success": True, "message": "注册成功"}
        except Exception as e:
            print(f"注册失败: {e}")
            return {"success": False, "message": str(e)}
        finally:
            self._close()
    
    def init_users_table(self):
        """初始化用户表（如果不存在）"""
        if not self._connect():
            return False
        
        create_table_sql = """
        CREATE TABLE IF NOT EXISTS users (
            id INT AUTO_INCREMENT PRIMARY KEY,
            username VARCHAR(50) NOT NULL UNIQUE,
            password VARCHAR(100) NOT NULL,
            role VARCHAR(20) NOT NULL DEFAULT 'user',
            email VARCHAR(100),
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
        """
        
        try:
            self.cursor.execute(create_table_sql)
            
            # 检查是否存在管理员用户
            self.cursor.execute("SELECT COUNT(*) FROM users WHERE username='admin'")
            count = self.cursor.fetchone()['COUNT(*)']
            if count == 0:
                self.cursor.execute("INSERT INTO users (username, password, role) VALUES ('admin', '123456', 'admin')")
            
            # 检查是否存在测试普通用户
            self.cursor.execute("SELECT COUNT(*) FROM users WHERE username='user'")
            count = self.cursor.fetchone()['COUNT(*)']
            if count == 0:
                self.cursor.execute("INSERT INTO users (username, password, role) VALUES ('user', '123456', 'user')")
            
            self.conn.commit()
            return True
        except Exception as e:
            print(f"初始化用户表失败: {e}")
            return False
        finally:
            self._close()

if __name__ == "__main__":
    auth = UserAuth()
    auth.init_users_table()
    print("用户认证模块初始化完成")