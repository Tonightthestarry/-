# 城市级多源海量数据智能挖掘与可视化决策平台 — 配置参考手册

> **编制日期**: 2026-06-20  
> **项目根目录**: `D:\massdatanaly`  
> **扫描范围**: 全部子工程 (`banaly2-java`, `banaly2-frontend`, `hadoop-3.5.0`)  
> **敏感信息处理**: 所有密码/密钥/令牌统一以 `***` 脱敏

---

## 目录

- [一、后端 Spring Boot 配置](#一后端-spring-boot-配置)
  - [1.1 application.yml（主配置文件）](#11-applicationyml主配置文件)
  - [1.2 pom.xml（Maven 构建与依赖）](#12-pomxmlmaven-构建与依赖)
  - [1.3 schema.sql（H2 数据库初始化）](#13-schemasqlh2-数据库初始化)
  - [1.4 WebConfig.java（CORS + JWT 拦截器）](#14-webconfigjava跨域与认证拦截)
  - [1.5 MongoConfig.java（MongoDB 连接）](#15-mongoconfigjavamongodb-连接)
  - [1.6 SparkConfig.java（Spark 集群配置）](#16-sparkconfigjavaspark-集群配置)
  - [1.7 DeepSeekService.java（AI 大模型）](#17-deepseekservicejavaai-大模型)
  - [1.8 HdfsService.java（HDFS 归档配置）](#18-hdfsservicejavahdfs-归档配置)
  - [1.9 JwtInterceptor.java（JWT 拦截）](#19-jwtinterceptorjavajwt-拦截)
  - [1.10 DataCollectionService.java（爬虫与模拟数据）](#110-datacollectionservicejava外部爬虫配置)
- [二、前端配置](#二前端配置)
  - [2.1 package.json](#21-packagejson)
  - [2.2 api.js（API 封装层）](#22-apijsapi-封装层)
- [三、Hadoop 集群配置](#三hadoop-集群配置)
  - [3.1 core-site.xml](#31-core-sitexml)
  - [3.2 hdfs-site.xml](#32-hdfs-sitexml)
  - [3.3 yarn-site.xml / mapred-site.xml](#33-yarn-sitexml--mapred-sitexml)
  - [3.4 capacity-scheduler.xml](#34-capacity-schedulerxml)
  - [3.5 hadoop-env.cmd](#35-hadoop-envcmd)
  - [3.6 log4j.properties](#36-log4jproperties)
- [四、敏感信息速查](#四敏感信息速查)
- [五、配置修改注意事项](#五配置修改注意事项)

---

## 一、后端 Spring Boot 配置

### 1.1 application.yml（主配置文件）

> **路径**: `banaly2-java\src\main\resources\application.yml`  
> **重要程度**: 🔴🔴🔴 **高**  
> **生效范围**: Spring Boot 全局

```yaml
server:
  port: 8088                           # 服务端口

spring:
  autoconfigure:
    exclude:
      - RedisAutoConfiguration         # 排除未安装的 Redis 自动配置
      - RedisRepositoriesAutoConfiguration

  data:
    mongodb:
      host: localhost                  # MongoDB 地址
      port: 27017                      # MongoDB 端口
      database: bilibili_analysis      # 数据库名（历史命名保留）
      auto-index-creation: true        # 自动创建索引

  datasource:                          # H2 嵌入式数据库（仅用户认证）
    url: jdbc:h2:file:./data/userdb;DB_CLOSE_DELAY=-1;MODE=MySQL
    driver-class-name: org.h2.Driver
    username: sa
    password:                          # 空密码

  h2:
    console:
      enabled: true                    # 启用 H2 Web 控制台
      path: /h2-console                # 访问路径

  sql:
    init:
      mode: always                     # 每次启动执行 schema.sql
      schema-locations: classpath:schema.sql

mybatis:
  mapper-locations: classpath:mapper/*.xml
  type-aliases-package: com.massdata.entity
  configuration:
    map-underscore-to-camel-case: true # 下划线转驼峰

jwt:
  secret: ***                          # JWT 签名密钥
  expiration: 86400000                 # Token 有效期 24h (ms)

spider:                                # B站爬虫配置
  api-url: https://api.bilibili.com/x/web-interface/search/all/v2
  user-agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64)...

spark:                                 # Spark 集群
  enabled: false                       # ⚠️ 当前关闭（避免 javax/jakarta 冲突）
  app-name: CityDataMining
  master: local[*]                     # 本地伪集群，使用所有 CPU 核
  checkpoint-dir: ./spark-checkpoint

hdfs:                                  # Hadoop HDFS 归档
  hadoop-home: D:\massdatanaly\hadoop-3.5.0
  base-path: /banaly2                  # HDFS 根路径
  archive-dir: /archive                # 归档子目录
```

<details>
<summary>📋 配置项明细表（展开查看全部）</summary>

| 配置路径 | 参数值 | 说明 | 重要度 | 修改注意事项 |
|----------|--------|------|--------|-------------|
| `server.port` | `8088` | 后端服务端口 | 高 | 需与前端 API_BASE 同步修改 |
| `spring.data.mongodb.host` | `localhost` | MongoDB主机 | 高 | 生产环境改为实际 IP |
| `spring.data.mongodb.port` | `27017` | MongoDB端口 | 高 | 默认端口，一般不修改 |
| `spring.data.mongodb.database` | `bilibili_analysis` | 数据库名 | 高 | 含所有业务数据，勿随意改名 |
| `spring.data.mongodb.auto-index-creation` | `true` | 自动创建索引 | 中 | 生产环境建议手动管理 |
| `spring.datasource.url` | `jdbc:h2:file:./data/userdb` | H2数据库文件路径 | 高 | 仅存用户认证数据 |
| `spring.datasource.username` | `sa` | H2用户名 | 中 | 默认超级管理员 |
| `spring.datasource.password` | (空) | H2密码 | 中 | 当前无密码 |
| `spring.sql.init.mode` | `always` | 每次启动建表 | 中 | 插入时用 MERGE，不会重复 |
| `mybatis.map-underscore-to-camel-case` | `true` | 驼峰映射 | 低 | 保持开启 |
| `jwt.secret` | `***` | JWT签名密钥 | 🔴高 | 泄露后任何人都可伪造 Token |
| `jwt.expiration` | `86400000` | Token过期时间 | 中 | 24小时，可调整 |
| `spider.api-url` | B站搜索API地址 | B站爬虫接口 | 中 | 可能随 B站 API 更新变化 |
| `spider.user-agent` | Chrome UA 字符串 | 请求头伪装 | 低 | 一般不需要改 |
| `spark.enabled` | `false` | Spark是否启用 | 高 | ⚠️ 开启需 JDK17 开放模块 |
| `spark.app-name` | `CityDataMining` | Spark应用名 | 中 | 监控时区分 |
| `spark.master` | `local[*]` | Spark运行模式 | 高 | 生产改为 `yarn` 或 `spark://...` |
| `spark.checkpoint-dir` | `./spark-checkpoint` | 检查点目录 | 中 | Streaming 容错所需 |
| `hdfs.hadoop-home` | `D:\massdatanaly\hadoop-3.5.0` | Hadoop安装目录 | 高 | 路径变更需同步修改 |
| `hdfs.base-path` | `/banaly2` | HDFS基础路径 | 中 | 归档时自动创建 |
| `hdfs.archive-dir` | `/archive` | HDFS归档目录 | 低 | 子路径 |

</details>

---

### 1.2 pom.xml（Maven 构建与依赖）

> **路径**: `banaly2-java\pom.xml`  
> **重要程度**: 🔴🔴🔴 **高**  
> **生效范围**: 全项目构建、依赖管理

| 配置节 | 参数值 | 说明 | 重要度 |
|--------|--------|------|--------|
| `groupId` | `com.massdata` | Maven 组织标识 | 低 |
| `artifactId` | `city-platform` | 项目标识 | 中 |
| `version` | `1.0` | 项目版本 | 低 |
| `packaging` | `jar` | 打包方式 | 低 |
| `parent.version` | `3.2.0` | Spring Boot 版本 | 🔴高 |
| `java.version` | `17` | JDK 版本 | 🔴高 |
| `spark.version` | `3.4.1` | Spark 版本 | 高 |

**核心依赖清单**:

| 依赖 | 版本 | 用途 | 重要度 |
|------|------|------|--------|
| `spring-boot-starter-web` | 3.2.0 | Web 框架 | 🔴高 |
| `spring-boot-starter-data-mongodb` | 3.2.0 | MongoDB 驱动 | 🔴高 |
| `spring-boot-starter-data-redis` | 3.2.0 | Redis 客户端（预留） | 低 |
| `mybatis-spring-boot-starter` | 3.0.3 | MyBatis ORM | 高 |
| `h2` | 2.x | H2 嵌入式数据库 | 高 |
| `spark-core_2.12` | 3.4.1 | Spark 核心 | 高 |
| `spark-sql_2.12` | 3.4.1 | Spark SQL | 高 |
| `spark-mllib_2.12` | 3.4.1 | Spark 机器学习 | 高 |
| `spark-streaming_2.12` | 3.4.1 | Spark 流处理 | 高 |
| `jjwt-api` | 0.12.3 | JWT 认证 | 🔴高 |
| `httpclient5` | 5.2.1 | HTTP 客户端 | 中 |
| `gson` | 2.10.1 | JSON 处理 | 中 |
| `lombok` | 1.18.x | 代码简化 | 中 |
| `javax.servlet-api` | 4.0.1 | ⚠️ Spark 3.4.1 兼容依赖 | 高 |

**JVM 参数（JDK 17 + Spark 兼容必需）**:
```
--add-opens java.base/java.nio=ALL-UNNAMED
--add-opens java.base/sun.nio.ch=ALL-UNNAMED
--add-opens java.base/java.lang=ALL-UNNAMED
--add-opens java.base/sun.util.calendar=ALL-UNNAMED
--add-opens java.base/java.util=ALL-UNNAMED
--add-opens java.base/jdk.internal.misc=ALL-UNNAMED
--add-opens java.base/sun.net.dns=ALL-UNNAMED
--add-opens java.base/java.lang.invoke=ALL-UNNAMED
```

> **修改注意事项**: 这 8 个 `--add-opens` 是 JDK 17 + Spark 3.4.1 的必需品，缺少任何一个都会导致 Spark 无法启动。生产环境部署时需同步配置到启动脚本。

---

### 1.3 schema.sql（H2 数据库初始化）

> **路径**: `banaly2-java\src\main\resources\schema.sql`  
> **重要程度**: 🔴🔴🔴 **高**  
> **生效范围**: 每次 Spring Boot 启动时自动执行

**用户表 `SYS_USER`**:
| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT AUTO_INCREMENT | 主键 |
| `username` | VARCHAR(50) UNIQUE | 用户名 |
| `password` | VARCHAR(200) | bcrypt 哈希密码 |
| `role` | VARCHAR(20) | 角色: admin / user |
| `nickname` | VARCHAR(50) | 昵称 |
| `create_time` | TIMESTAMP | 创建时间 |

**日志表 `SYS_LOG`**:
| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT AUTO_INCREMENT | 主键 |
| `username` | VARCHAR(50) | 操作用户 |
| `operation` | VARCHAR(100) | 操作类型 |
| `target` | VARCHAR(200) | 操作对象 |
| `status` | VARCHAR(20) | 操作状态 |
| `detail` | TEXT | 操作详情 |
| `create_time` | TIMESTAMP | 操作时间 |

**默认账户**（bcrypt 加密，密码均为 `123456`）:
| 用户名 | 密码 | 角色 |
|--------|------|------|
| `admin` | `***` | `admin` |
| `user` | `***` | `user` |

> **安全提醒**: 默认密码哈希值为演示用途。生产环境务必更换为用户自定义的 bcrypt 哈希值。

---

### 1.4 WebConfig.java（跨域与认证拦截）

> **路径**: `banaly2-java\src\main\java\com\massdata\config\WebConfig.java`  
> **重要程度**: 🔴🔴🔴 **高**  
> **生效范围**: Spring MVC 请求处理

**CORS 跨域白名单**:
```
http://localhost:5173     ← 前端独立端口
http://localhost:8088     ← 后端自身（内嵌静态资源）
http://localhost:5174     ← 备用前端端口
http://localhost:8080~8082 ← 其他备用端口
```

**JWT 拦截规则** (`addInterceptors`):

| 路径 | 是否需要 Token |
|------|---------------|
| `/api/auth/login` | ❌ 放行 |
| `/api/auth/register` | ❌ 放行 |
| `/api/streaming/recent` | ❌ 放行（前端 5 秒轮询） |
| `/api/streaming/stats` | ❌ 放行（KPI 刷新） |
| `/api/monitor/streaming-status` | ❌ 放行（监控面板） |
| `/h2-console/**` | ❌ 放行 |
| `/api/**`（其他） | ✅ 需要 |

**MIME 类型映射**:
- `.vue` → `text/html`（允许直接访问 `.vue` 文件）

> **修改注意事项**: 新增第三方前端域名需加入 CORS 白名单；新增无需登录的 API 需加入 `excludePathPatterns`。

---

### 1.5 MongoConfig.java（MongoDB 连接）

> **路径**: `banaly2-java\src\main\java\com\massdata\config\MongoConfig.java`  
> **重要程度**: 🔴🔴🔴 **高**  
> **生效范围**: MongoDB 数据访问

| 配置项 | 值 | 来源 |
|--------|-----|------|
| 连接地址 | `mongodb://localhost:27017` | 读取 `application.yml` |
| 数据库 | `bilibili_analysis` | 读取 `application.yml` |
| Bean 名称 | `mongoClient`, `mongoTemplate` | 注入 Spring 容器 |

> **修改注意事项**: 连接字符串为无认证的简化形式。生产环境需加上 `mongodb://user:pass@host:port/admin`。

---

### 1.6 SparkConfig.java（Spark 集群配置）

> **路径**: `banaly2-java\src\main\java\com\massdata\config\SparkConfig.java`  
> **重要程度**: ⚠️ 中 — **当前关闭 (`spark.enabled: false`)**  
> **生效范围**: 仅在 `spark.enabled=true` 时激活

| 配置项 | 值 | 说明 |
|--------|-----|------|
| App Name | `CityDataMining` | Spark 应用名 |
| Master | `local[*]` | 本地模式，所有核 |
| Adaptive Query Execution | `true` | Spark SQL 自适应优化 |
| Serializer | `KryoSerializer` | 高效序列化 |
| Driver Memory | `2g` | Driver 进程堆内存 |
| Spark UI | `false` | ⚠️ 关闭，避免 javax/jakarta 冲突 |
| Streaming Batch | `5 秒` | 微批次间隔 |
| Checkpoint Dir | `./spark-checkpoint` | 流处理检查点 |

> **⚠️ 开启 Spark 需满足的前置条件**:
> 1. 安装 `winutils.exe` 到 `%HADOOP_HOME%\bin\`
> 2. JDK 17 必须添加 8 个 `--add-opens` JVM 参数
> 3. 环境变量 `HADOOP_HOME` 指向正确路径
> 4. 将 `spark.enabled` 改为 `true` 后重启

---

### 1.7 DeepSeekService.java（AI 大模型）

> **路径**: `banaly2-java\src\main\java\com\massdata\service\DeepSeekService.java`  
> **重要程度**: 🔴🔴🔴 **高** — 挖掘分析核心组件  
> **生效范围**: 挖掘分析 → AI 结论生成

| 配置项 | 参数值 | 说明 |
|--------|--------|------|
| API Key | `***` | DeepSeek API 密钥（代码硬编码） |
| API 地址 | `https://api.deepseek.com/v1/chat/completions` | DeepSeek 官方接口 |
| 模型 | `deepseek-chat` | 对话模型 |
| 温度 | `0.7` | 创造性 0~1 |
| 最大 Token | `800` | 单次回复长度上限 |
| 连接超时 | `15 秒` | HTTP 连接超时 |
| System Prompt | `"你是城市管理数据分析专家..."` | 角色设定 |

**降级策略**: API 调用失败时自动执行 `buildFallbackConclusion()`，返回预设规则建议（5类数据 × 4条规则 = 20条静态建议）。

> **🔴 安全警告**:
> - API Key 以硬编码明文存储在源码中，任何人可读取
> - **建议**: 移至环境变量或 Spring Cloud Config
> - **有效期**: DeepSeek API Key 通常无固定过期，但平台可能回收
> - **更新方式**: 修改 `DeepSeekService.java` 第 22 行 `API_KEY` 常量

---

### 1.8 HdfsService.java（HDFS 归档配置）

> **路径**: `banaly2-java\src\main\java\com\massdata\service\HdfsService.java`  
> **重要程度**: 🔴🔴🔴 **高**  
> **生效范围**: HDFS 归档读写

| 配置项 | 参数值 | 说明 |
|--------|--------|------|
| Hadoop 安装目录 | `D:\massdatanaly\hadoop-3.5.0` | 硬编码路径 |
| Hadoop 命令 | `D:\...\bin\hadoop.cmd` | Windows 命令 |
| HDFS 基础路径 | `/banaly2` | 根目录 |
| 本地存储（降级） | `D:\massdatanaly\hadoop-data\hdfs-store` | 本地 JSON 归档 |
| 归档子目录 | `archive` | 归档文件目录 |
| 流数据子目录 | `stream` | 实时流数据目录 |

**HDFS 可用性三级检测**:
1. `hadoop.cmd` 文件存在 → 检查 NameNode `localhost:9870` 端口（3 秒超时）
2. 端口通 → 执行 `hadoop fs -ls /` 命令（15 秒超时）
3. 命令成功 → 分布式 HDFS 在线；任何一个失败 → 降级到本地存储

> **修改注意事项**: `HADOOP_HOME` 路径变更需同步修改 `HdfsService.java` 第 29 行。

---

### 1.9 JwtInterceptor.java（JWT 拦截）

> **路径**: `banaly2-java\src\main\java\com\massdata\interceptor\JwtInterceptor.java`  
> **重要程度**: 🔴🔴🔴 **高**  
> **生效范围**: 所有 `/api/**` 请求（排除路径除外）

| 配置项 | 说明 |
|--------|------|
| Token 位置 | HTTP Header `Authorization: Bearer <token>` |
| 兜底方式 | URL 参数 `?token=<token>` |
| 验证逻辑 | `JwtUtil.validate(token)` |
| 过期处理 | 返回 401 + `{"code":401,"message":"token无效或已过期"}` |
| 前端 401 响应 | 清除本地 Token，刷新页面重新登录 |

---

### 1.10 DataCollectionService.java（外部爬虫配置）

> **路径**: `banaly2-java\src\main\java\com\massdata\service\DataCollectionService.java`  
> **重要程度**: 中  
> **生效范围**: 数据采集模块

| 配置项 | 参数值 | 说明 |
|--------|--------|------|
| B站搜索 API | `https://api.bilibili.com/x/web-interface/search/all/v2` | 舆情真实数据源 |
| 天气 API | `https://wttr.in/Shanghai?format=j1` | 气象真实数据源 |
| 请求 User-Agent | Chrome UA 字符串 | 防止反爬 |
| 采集超时 | `5000ms` | HTTP 连接超时 |
| 模拟数据量 | 默认 5000 条/次 | 可自定义 |
| 实时流间隔 | `1000ms` | 每秒生成 5 条 |

---

## 二、前端配置

### 2.1 package.json

> **路径**: `banaly2-frontend\package.json`  
> **重要程度**: 中  
> **生效范围**: 前端启动

```json
{
  "name": "banaly2-frontend",
  "version": "1.0.0",
  "private": true,
  "description": "城市级多源海量数据智能挖掘与可视化决策平台 - 前端工程",
  "scripts": {
    "start": "serve -l 5173 .",
    "dev": "serve -l 5173 ."
  },
  "dependencies": {
    "serve": "^14.2.0"
  }
}
```

| 配置项 | 参数值 | 说明 | 重要度 |
|--------|--------|------|--------|
| 启动命令 | `serve -l 5173 .` | 静态文件服务器 | 高 |
| 端口 | `5173` | 前端访问端口 | 高 |
| 唯一依赖 | `serve 14.x` | 零构建静态服务器 | 低 |

---

### 2.2 api.js（API 封装层）

> **路径**: `banaly2-frontend\static\js\api.js`  
> **重要程度**: 🔴🔴🔴 **高**  
> **生效范围**: 前端所有 API 请求

| 配置项 | 参数值 | 说明 | 重要度 |
|--------|--------|------|--------|
| `API_BASE` | `http://localhost:8088` | 后端地址 | 🔴高 |
| `timeout` | `30000` (30s) | 请求超时 | 中 |
| `withCredentials` | `true` | 跨域携带 Cookie | 中 |
| Token 存储 | `localStorage.getItem('token')` | 浏览器本地存储 | 高 |
| 401 处理 | 清除 Token + 刷新页面 | 自动重新登录 | 高 |

**所有 API 端点**（共 10 个分组，59 个接口）:

| 分组 | 变量名 | 接口数量 | 路径前缀 |
|------|--------|---------|---------|
| 认证 | `authAPI` | 6 | `/api/auth/*`, `/api/admin/*` |
| 数据采集 | `dataAPI` | 8 | `/api/data/*` |
| 挖掘分析 | `miningAPI` | 6 | `/api/mining/*` |
| 可视化大屏 | `visualAPI` | 14 | `/api/visual/*` |
| 定时调度 | `schedulerAPI` | 2 | `/api/scheduler/*` |
| 实时监控 | `monitorAPI` | 6 | `/api/monitor/*` |
| MapReduce/Streaming | `streamingAPI` | 3 | `/api/streaming/*` |
| HDFS 归档 | `hdfsAPI` | 4 | `/api/hdfs/*` |

> **修改注意事项**: API_BASE 变更后需重启前端静态服务器。

---

## 三、Hadoop 集群配置

### 3.1 core-site.xml

> **路径**: `hadoop-3.5.0\etc\hadoop\core-site.xml`  
> **重要程度**: 🔴🔴🔴 **高**  
> **生效范围**: Hadoop 全局

| 配置项 | 参数值 | 说明 |
|--------|--------|------|
| `fs.defaultFS` | `hdfs://localhost:9000` | HDFS 默认文件系统地址 |

---

### 3.2 hdfs-site.xml

> **路径**: `hadoop-3.5.0\etc\hadoop\hdfs-site.xml`  
> **重要程度**: 🔴🔴🔴 **高**  
> **生效范围**: HDFS 存储

| 配置项 | 参数值 | 说明 |
|--------|--------|------|
| `dfs.replication` | `1` | 副本数（伪分布式=1） |
| `dfs.namenode.name.dir` | `file:///D:/massdatanaly/hadoop-data/namenode` | NameNode 元数据目录 |
| `dfs.datanode.data.dir` | `file:///D:/massdatanaly/hadoop-data/datanode` | DataNode 数据目录 |

---

### 3.3 yarn-site.xml / mapred-site.xml

> **路径**: `hadoop-3.5.0\etc\hadoop\yarn-site.xml`, `mapred-site.xml`  
> **重要程度**: 低 — **均为空配置（未启用 YARN 和 MapReduce）**  
> **生效范围**: 预留，无实际效果

---

### 3.4 capacity-scheduler.xml

> **路径**: `hadoop-3.5.0\etc\hadoop\capacity-scheduler.xml`  
> **重要程度**: 低（YARN 未启用，此项不生效）  
> **生效范围**: YARN 资源调度

| 配置项 | 值 | 说明 |
|--------|-----|------|
| `maximum-applications` | `10000` | 最大并发应用数 |
| `maximum-am-resource-percent` | `0.1` | AM 资源占比上限 10% |
| `resource-calculator` | `DefaultResourceCalculator` | 仅按内存调度 |
| `root.queues` | `default` | 仅一个队列 |
| `root.default.capacity` | `100` | 默认队列占全部资源 |
| `root.default.acl_submit_applications` | `*` | 全员可提交 |
| `node-locality-delay` | `40` | 错过调度次数后放宽本地 |

---

### 3.5 hadoop-env.cmd

> **路径**: `hadoop-3.5.0\etc\hadoop\hadoop-env.cmd`  
> **重要程度**: 🔴🔴🔴 **高**  
> **生效范围**: Hadoop 所有守护进程

| 配置项 | 参数值 | 说明 |
|--------|--------|------|
| `JAVA_HOME` | `E:\jdk\jdk17` | JDK 路径（⚠️ 非标准路径） |
| `HADOOP_CLIENT_OPTS` | `-Xms128m -Xmx512m` | 客户端 JVM 堆内存 |

> **⚠️ 注意**: `JAVA_HOME=E:\jdk\jdk17` 是绝对路径，若 JDK 位置变化需同步修改。

---

### 3.6 log4j.properties

> **路径**: `hadoop-3.5.0\etc\hadoop\log4j.properties`  
> **重要程度**: 低  
> **生效范围**: Hadoop 所有组件日志

| 配置项 | 值 | 说明 |
|--------|-----|------|
| 根日志级别 | `INFO` | 默认输出到控制台 |
| 日志文件大小 | `256MB` | 单文件上限 |
| 备份数量 | `20` | 滚动保留 20 个 |
| NameNode 指标日志 | `64MB × 2` | 独立日志文件 |
| DataNode 指标日志 | `64MB × 2` | 独立日志文件 |

---

## 四、敏感信息速查

| # | 类型 | 位置 | 脱敏值 | 风险等级 |
|---|------|------|--------|---------|
| 1 | DeepSeek API Key | `DeepSeekService.java:22` | `sk-***` | 🔴 高 |
| 2 | JWT 签名密钥 | `application.yml:48` | `***` | 🔴 高 |
| 3 | H2 数据库密码 | `application.yml:29` | (空) | 中 |
| 4 | B站搜索 API | `application.yml:53` | 公开接口 | 低 |
| 5 | wttr.in 天气 API | `DataCollectionService.java:386` | 公开接口 | 低 |
| 6 | 管理员密码哈希 | `schema.sql:24` | `***` (bcrypt) | 中 |
| 7 | 用户密码哈希 | `schema.sql:28` | `***` (bcrypt) | 中 |
| 8 | Hadoop 用户 | `HdfsService.java:311` | `Administrator` | 低 |

---

## 五、配置修改注意事项

### 5.1 重要度标准

| 标识 | 含义 | 影响 |
|------|------|------|
| 🔴🔴🔴 高 | 修改后系统可能不可用 | 需要重启 + 验证 |
| 🔴🔴 中 | 影响部分功能 | 功能降级但不崩溃 |
| 🔴 低 | 无功能影响 | 仅日志/UI/调试 |

### 5.2 修改权限要求

| 配置类型 | 需要权限 | 生效方式 |
|----------|---------|---------|
| `application.yml` | 开发者 | 重启 Spring Boot |
| `pom.xml` | 开发者 | 重新编译打包 |
| `schema.sql` | 开发者 | 重启 Spring Boot |
| Hadoop `*-site.xml` | 运维 | 重启 Hadoop 服务 |
| 前端 API 地址 | 开发者 | 重启 `serve` |
| 硬编码常量（Java） | 开发者 | 重新编译打包 |
| 数据库配置 | DBA | 无需重启（连接池自动恢复） |

### 5.3 关键注意事项

1. **`spark.enabled` 不要随意开启**：当前 `false` 可正常使用全部功能（挖掘算法为纯 Java 实现降级版）。开启需 JDK 17 添加 8 个 JVM 启动参数
2. **前端端口 5173 和后端端口 8088 必须同步修改**：分别修改前端的 `API_BASE` 和后端的 `CORS` 白名单
3. **JWT secret 泄露后的应对**：立即更换密钥 → 重启后端 → 所有用户需重新登录
4. **HDFS 路径变更**：需同步修改 `application.yml` + `HdfsService.java` 两处
5. **B站 API** 偶尔会限流/改接口，爬虫有降级方案（纯模拟数据）

---

> **文档维护**: 每次部署/配置变更后更新本文档对应章节  
> **最后更新**: 2026-06-20, 由自动化配置扫描生成
