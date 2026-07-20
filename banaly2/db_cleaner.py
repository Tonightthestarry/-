# -*- coding: utf-8 -*-
"""DB Cleaner - Clear MySQL and MongoDB data, keep table/collection structure"""
import pymysql
from pymongo import MongoClient

MYSQL_CONFIG = {
    "host": "localhost",
    "port": 3306,
    "user": "root",
    "password": "root",
    "database": "ai_know",
    "charset": "utf8mb4"
}

MONGO_CONFIG = {
    "host": "localhost",
    "port": 27017,
    "db_name": "bilibili_analysis",
    "coll_name": "mingchao_video_data"
}


def clear_mysql():
    conn = pymysql.connect(**MYSQL_CONFIG)
    cursor = conn.cursor()
    
    cursor.execute("SHOW TABLES LIKE 'bilibili_videos%'")
    tables = [row[0] for row in cursor.fetchall()]
    
    total_deleted = 0
    for table in tables:
        cursor.execute("SELECT COUNT(*) FROM " + table)
        count = cursor.fetchone()[0]
        if count > 0:
            cursor.execute("DELETE FROM " + table)
            conn.commit()
        print("  %s: deleted %d rows" % (table, count))
        total_deleted += count
    
    cursor.close()
    conn.close()
    print("MySQL total deleted: %d rows\n" % total_deleted)
    return total_deleted


def clear_mongo():
    client = MongoClient(MONGO_CONFIG["host"], MONGO_CONFIG["port"])
    db = client[MONGO_CONFIG["db_name"]]
    coll = db[MONGO_CONFIG["coll_name"]]
    
    count = coll.count_documents({})
    if count > 0:
        coll.delete_many({})
    
    print("  %s.%s: deleted %d docs" % (MONGO_CONFIG["db_name"], MONGO_CONFIG["coll_name"], count))
    client.close()
    print("MongoDB total deleted: %d docs\n" % count)
    return count


if __name__ == "__main__":
    print("=" * 50)
    print("  DB Cleaner - Keep structure, clear data only")
    print("=" * 50)
    print("")
    
    print("[1/2] Clearing MySQL...")
    mysql_count = clear_mysql()
    
    print("[2/2] Clearing MongoDB...")
    mongo_count = clear_mongo()
    
    print("=" * 50)
    print("  Done!")
    print("  MySQL cleared:  %d rows" % mysql_count)
    print("  MongoDB cleared: %d docs" % mongo_count)
    print("=" * 50)
