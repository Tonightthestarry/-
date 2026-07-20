"""校园事件模拟数据生成器 - 模拟学生在校园网内发布的各种事件"""
import random
import time
from datetime import datetime, timedelta
import sys
import os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from backend.db_mysql import insert_event_record

# ========== 校园事件类型模板 ==========
EVENT_TEMPLATES = {
    "体测": [
        ("体测通知：下周一开始1000米测试", "请各位同学做好准备，携带学生证到场", 85),
        ("体测成绩查询通道已开放", "本学期体测成绩已录入系统，可登录查看", 120),
        ("体测补测通知", "错过体测的同学请于本周五下午2点至操场补测", 70),
        ("体测引体向上难度的讨论", "为什么引体向上改成了12个才算及格？", 95),
        ("女生800米体测心得", "分享一些800米备考经验，亲测有效", 60),
    ],
    "活动": [
        ("校园歌手大赛报名开始", "第十九届校园歌手大赛开始报名啦！截止日期本月25号", 150),
        ("社团招新嘉年华本周六举行", "百家社团齐聚田径场，欢迎新生踊跃参加", 200),
        ("学术讲座：人工智能前沿", "特邀清华大学教授来校讲座，地点学术报告厅", 110),
        ("校运动会即将开幕", "本周五上午8点开幕式，请各学院方队做好准备", 130),
        ("秋季招聘会企业名单公布", "共有120家企业参会，请同学们准备好简历", 100),
    ],
    "求助": [
        ("【求助】校园卡丢失", "今天中午在二食堂丢失校园卡一张，卡号2023xxxx，拾到请联系", 45),
        ("【求助】请问图书馆怎么预约座位", "新生不太会用座位预约系统，求教", 55),
        ("【求助】校园网突然连不上了", "从今天早上开始，宿舍区的WiFi一直无法连接", 80),
        ("【求助】教务系统登不上去了", "选课期间系统一直崩溃，有什么解决办法吗", 90),
        ("【求助】谁知道心理健康中心怎么预约", "最近压力有点大想去咨询一下", 40),
    ],
    "社团": [
        ("街舞社训练时间调整通知", "由于场地调整，本周训练时间改为周四晚7点", 50),
        ("摄影社招新", "喜欢摄影的同学看过来！零基础也可加入", 70),
        ("志愿者协会招募", "本周六去福利院志愿服务，欢迎报名", 65),
        ("辩论队内部选拔赛", "下周二晚7点，教五203，欢迎观摩", 40),
        ("动漫社cosplay大赛", "年度cos大赛报名中，奖品丰厚", 85),
    ],
    "课程评价": [
        ("这学期的Python课也太难了吧", "老师讲得太快了完全跟不上", 75),
        ("推荐一门好课：中国传统文化", "老师讲得非常有意思，还不用期末考试", 100),
        ("计科院的算法课怎么样", "想选这门课，求上过的学长学姐评价", 55),
        ("英语四六级冲刺班有用吗", "报了学校的冲刺班，不知道值不值得", 60),
        ("吐槽一下线性代数的考试", "题型完全和平时作业不一样", 90),
    ],
    "投诉": [
        ("食堂饭菜越来越贵了", "一荤一素已经要15块钱了，质量还下降了", 120),
        ("图书馆空调太冷了", "夏天冷得要穿外套，能不能调到26度", 70),
        ("宿舍热水供应时间太短了", "只有晚上7-10点有热水，白天都没热水洗澡", 95),
        ("教室多媒体设备经常出故障", "教三的投影仪都坏了一个月了还没修", 65),
        ("选课系统又崩溃了", "每次选课第一天系统必崩，能不能提前扩容", 110),
    ],
    "其他": [
        ("校园萌宠合集", "学校里的小猫咪日常更新，太治愈了", 55),
        ("分享一波校园美景", "昨天拍的落日，太美了", 40),
        ("二手交易：出售考研资料", "政治英语数学全套，价格面议", 35),
        ("有没有一起考研打卡的", "找研友互相监督，每天打卡学习", 50),
        ("学校周边的美食推荐", "推荐几家性价比超高的小店", 65),
    ],
}

# 情感对应的模板
SENTIMENT_WEIGHTS = {
    "体测":     [0.4, 0.4, 0.2],  # positive, neutral, negative
    "活动":     [0.7, 0.25, 0.05],
    "求助":     [0.15, 0.55, 0.3],
    "社团":     [0.6, 0.35, 0.05],
    "课程评价": [0.3, 0.4, 0.3],
    "投诉":     [0.05, 0.2, 0.75],
    "其他":     [0.4, 0.45, 0.15],
}

CAMPUS_AREAS = ["教学楼", "宿舍", "食堂", "图书馆", "操场", "体育馆", "其他"]

STUDENT_NAMES = ["张伟", "李娜", "王磊", "刘洋", "陈静", "杨帆", "赵敏", "黄强", "周婷", "吴昊",
                 "林悦", "孙明", "马丽", "朱峰", "何欢", "郭瑞", "苏瑶", "郑龙", "梁雨", "谢安"]

EVENT_STATUSES = {"normal": 0.85, "flagged": 0.12, "anomaly": 0.03}


def _pick_sentiment(event_type):
    weights = SENTIMENT_WEIGHTS.get(event_type, [0.35, 0.45, 0.2])
    r = random.random()
    if r < weights[0]:
        return "positive"
    elif r < weights[0] + weights[1]:
        return "neutral"
    return "negative"


def generate_single_event(event_type=None, with_timestamp=True):
    """生成单个事件记录"""
    if event_type is None:
        event_type = random.choice(list(EVENT_TEMPLATES.keys()))

    templates = EVENT_TEMPLATES.get(event_type, EVENT_TEMPLATES["其他"])
    title, content, base_heat = random.choice(templates)

    # 热度加随机波动
    heat = base_heat + random.randint(-20, 30)
    heat = max(1, heat)

    record = {
        "event_type": event_type,
        "title": title,
        "content": content + (" [热度:%d]" % heat if random.random() > 0.5 else ""),
        "campus_area": random.choice(CAMPUS_AREAS),
        "student_name": random.choice(STUDENT_NAMES),
        "student_id": "2023%04d" % random.randint(1, 9999),
        "heat_score": heat,
        "sentiment": _pick_sentiment(event_type),
        "status": random.choices(list(EVENT_STATUSES.keys()),
                                  weights=list(EVENT_STATUSES.values()))[0],
        "source": "simulated",
        "url": "",
        "create_time": (datetime.now().strftime("%Y-%m-%d %H:%M:%S")
                        if with_timestamp else datetime.now()),
    }
    return record


def batch_generate(n: int, date_str: str = None, to_mysql=True):
    """批量生成N条事件记录，可指定日期"""
    records = []
    for i in range(n):
        record = generate_single_event(with_timestamp=False)

        # 时间分布在当天 6:00 - 23:00
        hour = random.randint(6, 23)
        minute = random.randint(0, 59)
        second = random.randint(0, 59)

        if date_str:
            record["create_time"] = f"{date_str} {hour:02d}:{minute:02d}:{second:02d}"
        else:
            record["create_time"] = datetime.now().strftime(
                "%Y-%m-%d") + f" {hour:02d}:{minute:02d}:{second:02d}"

        if to_mysql:
            try:
                insert_event_record(record)
            except Exception:
                pass

        records.append(record)
    return records


def batch_generate_date_range(start_date: str, end_date: str, per_day: int = 50):
    """批量生成指定日期范围内每天N条事件"""
    start = datetime.strptime(start_date, "%Y-%m-%d")
    end = datetime.strptime(end_date, "%Y-%m-%d")
    total = 0
    current = start
    while current <= end:
        date_str = current.strftime("%Y-%m-%d")
        batch_generate(per_day, date_str)
        total += per_day
        current += timedelta(days=1)
    return total
