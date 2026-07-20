# 城市级多源海量数据智能挖掘与可视化决策平台 - 前端工程

## 项目结构
```
banaly2-frontend/
├── package.json
├── README.md
└── static/                  ← Vue 3 前端代码
    ├── index.vue
    ├── js/
    │   ├── api.js          ← axios 封装(baseURL=http://localhost:8088)
    │   └── app.js          ← Vue 3 全部业务逻辑
    ├── lib/                ← 本地依赖(Vue/ElementPlus/ECharts/Axios)
    └── css/style.css
```

## 启动方式（前后端分离）
```bash
# 终端 1 - 后端
cd d:\massdatanaly\banaly2-java
mvn spring-boot:run
# 跑在 http://localhost:8088

# 终端 2 - 前端
cd d:\massdatanaly\banaly2-frontend
npx serve -l 5173 .
# 跑在 http://localhost:5173

# 浏览器访问 http://localhost:5173
```

## 技术栈
- Vue 3 (Composition API, CDN)
- Element Plus
- ECharts 5
- Axios
- 后端: Spring Boot 3.2 (REST API, 跨域已配置)

## 账户
- 管理员: admin / 123456
- 普通用户: user / 123456
