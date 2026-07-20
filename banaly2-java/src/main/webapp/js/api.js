// API请求封装
const BASE_URL = '';
const api = axios.create({ baseURL: BASE_URL, timeout: 30000 });

// 请求拦截器: 自动附加token
api.interceptors.request.use(config => {
    const token = localStorage.getItem('token');
    if (token) config.headers['Authorization'] = 'Bearer ' + token;
    return config;
});

// 响应拦截器: 统一错误处理
api.interceptors.response.use(
    res => res.data,
    err => {
        if (err.response && err.response.status === 401) {
            localStorage.removeItem('token');
            location.reload();
        }
        return Promise.reject(err);
    }
);

// 认证API
const authAPI = {
    login: (data) => api.post('/api/auth/login', data),
    userinfo: () => api.get('/api/auth/userinfo'),
    logs: (limit) => api.get('/api/auth/logs', { params: { limit } })
};

// 数据API
const dataAPI = {
    collect: (dataType, city) => api.post('/api/data/collect', null, { params: { dataType, city } }),
    collectAll: (city) => api.post('/api/data/collect-all', null, { params: { city } }),
    collectWithKeyword: (dataType, city, count, keyword) =>
        api.post('/api/data/collect-with-keyword', null, { params: { dataType, city, count, keyword } }),
    collectAllWithKeyword: (city, count, keyword) =>
        api.post('/api/data/collect-all-with-keyword', null, { params: { city, count, keyword } }),
    query: (dataType, dateStr) => api.get('/api/data/query', { params: { dataType, dateStr } }),
    latest: (dataType, limit) => api.get('/api/data/latest', { params: { dataType, limit } }),
    collectStatus: () => api.get('/api/data/collect-status')
};

// 挖掘API
const miningAPI = {
    execute: (dataType, taskType, dateStr) =>
        api.post('/api/mining/execute', null, { params: { dataType, taskType, dateStr } }),
    executeAll: (dateStr) =>
        api.post('/api/mining/execute-all', null, { params: { dateStr } }),
    results: (dataType) => api.get('/api/mining/results', { params: { dataType } }),
    result: (taskId) => api.get('/api/mining/result/' + taskId)
};

// 可视化API
const visualAPI = {
    overview: () => api.get('/api/visual/overview'),
    trafficHeatmap: (date) => api.get('/api/visual/traffic-heatmap', { params: { date } }),
    congestionPie: (date) => api.get('/api/visual/congestion-pie', { params: { date } }),
    populationBar: (date) => api.get('/api/visual/population-bar', { params: { date } }),
    weatherLine: (date) => api.get('/api/visual/weather-line', { params: { date } }),
    consumptionScatter: (date) => api.get('/api/visual/consumption-scatter', { params: { date } }),
    opinionGauge: (date) => api.get('/api/visual/opinion-gauge', { params: { date } }),
    analysisSummary: () => api.get('/api/visual/analysis-summary'),
    // 事故风险分类
    accidentRisk(date) {
        return api.get('/api/visual/accident-risk', { params: { date: date || '' } });
    },
    // 出行规律24h
    travelPattern(date) {
        return api.get('/api/visual/travel-pattern', { params: { date: date || '' } });
    },
    // 导出全部大屏数据
    exportData(date) {
        return api.get('/api/visual/export', { params: { date: date || '' } });
    },
    // Spark集群状态
    sparkStatus() {
        return api.get('/api/visual/spark-status');
    }
};
