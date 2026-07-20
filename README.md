# 城市级多源海量数据智能挖掘与可视化决策平台

## 项目简介

基于 Spring Boot + Vue3 的城市数据实时监控与挖掘分析平台，支持多源数据采集、实时流处理、数据挖掘分析和可视化大屏展示。

## 技术栈

- **后端**: Spring Boot 3.2.0 + Java 17
- **前端**: Vue 3 + Element Plus + ECharts
- **数据库**: MongoDB + H2
- **大数据**: Hadoop 3.5.0 (本地模式)
- **构建工具**: Maven + npm

## 功能模块

1. **数据采集**: 支持交通、气象、舆情、消费、人口五类数据的模拟生成与真实爬虫采集
2. **实时流处理**: 每秒自动生成城市数据并写入 MongoDB，供大屏实时展示
3. **数据挖掘**: 统计分析、聚类分析、关联规则、预测分析、异常检测
4. **可视化大屏**: 12+ 图表实时展示城市态势
5. **HDFS 存储**: 数据归档与恢复

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.8+
- Node.js 16+
- MongoDB 4.0+

### 启动服务

```bash
# 启动后端 (端口 8088)
cd banaly2-java
mvn spring-boot:run

# 启动前端 (端口 5173)
cd my-vue-project
npm run serve
```

### 访问地址

- 前端页面: http://localhost:5173
- 后端 API: http://localhost:8088
- H2 控制台: http://localhost:8088/h2-console

### 登录账号

- 管理员: admin / 123456
- 普通用户: user / 123456

## 项目结构

```
massdatanaly/
├── banaly2-java/          # Java 后端
│   ├── src/main/java/com/massdata/
│   │   ├── controller/    # REST API 控制器
│   │   ├── service/       # 业务逻辑层
│   │   ├── dao/           # 数据访问层
│   │   └── entity/        # 实体类
│   └── src/main/resources/
│       ├── application.yml
│       └── schema.sql
├── my-vue-project/        # Vue 前端
│   ├── src/
│   │   ├── views/         # 页面组件
│   │   ├── api/           # API 请求封装
│   │   └── router/        # 路由配置
│   └── vue.config.js
├── banaly2-frontend/      # 备用前端 (原生 Vue)
├── collegeopionsystem/    # 校园事件监测系统 (Python)
├── hadoop-3.5.0/          # Hadoop 安装包
└── hadoop-data/           # HDFS 本地存储
```

## API 接口示例

```bash
# 登录
POST /api/auth/login
{"username": "admin", "password": "123456"}

# 数据采集
POST /api/data/collect?dataType=traffic&city=上海

# 实时流统计
GET /api/streaming/stats?date=2026-07-20

# 数据大屏
GET /api/streaming/dashboard?date=2026-07-20

# 挖掘分析
POST /api/mining/execute?dataType=traffic&taskType=statistic
```

## 许可证

MIT License
