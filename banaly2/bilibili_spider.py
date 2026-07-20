# -*- coding: utf-8 -*-
"""B站爬虫模块 - 关键词搜索 + 日期筛选 + 完整字段采集"""
import requests
import pandas as pd
import time
import urllib.parse
import random
from datetime import datetime

# B站Cookie（来自用户提供的配置）
BILIBILI_COOKIE = "buvid3=7E0EC2F7-94E2-28D3-B4B3-1C0BFE1F944A25127infoc; b_nut=1777214125; _uuid=B94510B7E-7756-1D6B-E4A10-237AD27EC77E29732infoc; buvid4=ECC97036-F4B7-9B37-74D2-173624C2AAFA25721-026042622-jUP6q9+Hjb0XrilOVg2rPQ%3D%3D; buvid_fp=686d0c92bdb4b49a623b2a68847ef1f8; SESSDATA=3116283d%2C1792766222%2C0cfc6%2A42CjAUWG-VdxWLh10oLap-W3aVYJM3P4oMWhq-Ds8w7ArVu_8QUXvdAioiR30NCueirS0SVkwyaHlOd0Rxc0NBN1VveVBpUUpHZG5Nb2FYdjUtVXpsREthY3k5MjNXd2xmR2NMNXJOcUwzWnZUVFNET3JyaEppNVhKS2FWSDRwMV9xMndfOUIwOFJRIIEC; bili_jct=6271d7b3dd4ff3fe9558dca3c698e6fd; DedeUserID=667356508; DedeUserID__ckMd5=17870b8cf367d17; sid=8keutph9; theme-tip-show=SHOWED; CURRENT_QUALITY=0; rpdid=|(u)l|Jku|J~0J'u~~~kuRkJm; theme-avatar-tip-show=SHOWED; PVID=1; LIVE_BUVID=AUTO5317774376848442; home_feed_column=5; browser_resolution=1699-828; bp_t_offset_667356508=1208020252772794368; bili_ticket=eyJhbGciOiJIUzI1NiIsImtpZCI6InMwMyIsInR5cCI6IkpXVCJ9.eyJleHAiOjE3ODA1NjIxNjIsImlhdCI6MTc4MDMwMjkwMiwicGx0IjotMX0.gle2g0fknyjcYee3nxCVPswX48KSg69WkYltmlfj2uk; bili_ticket_expires=1780562102; bmg_af_switch=1; bmg_src_def_domain=i1.hdslb.com; CURRENT_FNVAL=4048; b_lsid=38DF2A0B_19E8649E7A2"

class BilibiliSpider:
    def __init__(self):
        self.headers = {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36",
            "Referer": "https://search.bilibili.com/",
            "Cookie": BILIBILI_COOKIE
        }
        self.default_keyword = "新闻"
        # 延时配置：防止请求过快被B站限制
        self.delay_between_pages = (0.5, 1.5)      # 翻页延时：0.5-1.5秒
        self.delay_between_video_details = (0.3, 0.8)  # 视频详情请求延时：0.3-0.8秒
        self.delay_between_api_requests = 0.3     # 每个API请求的基础延时
    
    def is_date_valid(self, target_date):
        """验证日期是否有效（不能是未来日期）"""
        try:
            target = datetime.strptime(target_date, "%Y-%m-%d")
            now = datetime.now()
            return target <= now
        except Exception as e:
            print(f"日期验证失败: {e}")
            return False
    
    def _get_time_range(self, target_date):
        """根据目标日期计算时间范围参数"""
        if not target_date:
            return 0
        
        try:
            target = datetime.strptime(target_date, "%Y-%m-%d")
            today = datetime.now()
            diff_days = (today - target).days
            
            if diff_days <= 1:
                return 1
            elif diff_days <= 7:
                return 7
            elif diff_days <= 30:
                return 30
            elif diff_days <= 90:
                return 90
            else:
                return 0
        except Exception as e:
            print(f"计算时间范围失败: {e}")
            return 0
    
    def _get_video_detail(self, bvid):
        """获取视频详细信息"""
        try:
            time.sleep(self.delay_between_api_requests)  # 基础延时
            url = f"https://api.bilibili.com/x/web-interface/view?bvid={bvid}"
            response = requests.get(url, headers=self.headers, timeout=15)
            response.raise_for_status()
            data = response.json()
            
            if 'data' in data:
                video_data = data['data']
                return {
                    '播放量': video_data.get('stat', {}).get('view', 0),
                    '点赞数': video_data.get('stat', {}).get('like', 0),
                    '投币数': video_data.get('stat', {}).get('coin', 0),
                    '收藏数': video_data.get('stat', {}).get('favorite', 0),
                    '评论数': video_data.get('stat', {}).get('reply', 0),
                    '转发数': video_data.get('stat', {}).get('share', 0)
                }
        except Exception as e:
            print(f"获取视频详情失败 {bvid}: {e}")
        
        return None
    
    def search_videos(self, keyword=None, target_date=None, max_page=3):
        """搜索B站视频（关键词+日期筛选）"""
        keyword = keyword or self.default_keyword
        
        if target_date and not self.is_date_valid(target_date):
            print(f"错误：日期 {target_date} 无效或为未来日期！")
            return []
        
        print(f"开始采集 [{keyword}]，日期: {target_date or '全部'}，页数: {max_page}")
        data_list = []
        encoded_keyword = urllib.parse.quote(keyword)
        time_range = self._get_time_range(target_date)
        
        for page in range(1, max_page + 1):
            url = f"https://api.bilibili.com/x/web-interface/search/type?keyword={encoded_keyword}&page={page}&search_type=video&page_size=30"
            
            if time_range > 0:
                url += f"&duration=0&tids=0&pubdate={time_range}"
            
            try:
                time.sleep(self.delay_between_api_requests)  # 每个搜索请求都加延时
                response = requests.get(url, headers=self.headers, timeout=20)
                response.raise_for_status()
                json_data = response.json()
                
                if json_data.get("code") != 0:
                    print(f"第{page}页无数据，跳过")
                    continue
                
                for item in json_data.get("data", {}).get("result", []):
                    pubdate = item.get("pubdate", "")
                    if isinstance(pubdate, int):
                        pubdate_str = datetime.fromtimestamp(pubdate).strftime("%Y-%m-%d")
                    else:
                        pubdate_str = str(pubdate)[:10]
                    
                    # 如果指定了日期，过滤不匹配的视频
                    if target_date and pubdate_str != target_date:
                        continue
                    
                    bvid = item.get("bvid", "")
                    
                    video_info = {
                        "标题": item.get("title", "").replace("<em>", "").replace("</em>", ""),
                        "播放量": int(item.get("play", 0)),
                        "点赞数": int(item.get("like", 0)),
                        "投币数": int(item.get("coin", 0)),
                        "收藏数": int(item.get("favorites", 0)),
                        "UP主": item.get("author", "Unknown"),
                        "发布时间": pubdate_str,
                        "视频链接": f"https://www.bilibili.com/video/{bvid}",
                        "简介": item.get("description", "No description"),
                        "评论数": int(item.get("comment", 0)),
                        "转发数": int(item.get("share", 0)),
                        "bvid": bvid
                    }
                    
                    # 如果投币数或收藏数为0，尝试获取视频详情补充
                    if bvid and (video_info['投币数'] == 0 or video_info['收藏数'] == 0):
                        detail = self._get_video_detail(bvid)
                        if detail:
                            video_info['播放量'] = detail['播放量'] or video_info['播放量']
                            video_info['点赞数'] = detail['点赞数'] or video_info['点赞数']
                            video_info['投币数'] = detail['投币数'] or video_info['投币数']
                            video_info['收藏数'] = detail['收藏数'] or video_info['收藏数']
                            video_info['评论数'] = detail['评论数'] or video_info['评论数']
                            video_info['转发数'] = detail['转发数'] or video_info['转发数']
                        time.sleep(random.uniform(0.3, 0.8))
                    
                    data_list.append(video_info)
                
                print(f"第{page}/{max_page}页完成 | 已采集: {len(data_list)}条")
                time.sleep(random.uniform(0.5, 1.5))
            
            except Exception as e:
                print(f"第{page}页采集失败: {str(e)}")
                time.sleep(2)
                continue
        
        print(f"\n采集完成！共: {len(data_list)}条")
        return data_list
    
    def crawl(self, keyword=None, target_date=None, max_page=3):
        """爬取入口函数"""
        return self.search_videos(keyword, target_date, max_page)

if __name__ == "__main__":
    spider = BilibiliSpider()
    result = spider.crawl(keyword="新闻", target_date="2026-06-03", max_page=1)
    print(f"采集结果: {len(result)}条")
    if result:
        for video in result[:3]:
            print(f"标题: {video['标题']} | 播放量: {video['播放量']}")