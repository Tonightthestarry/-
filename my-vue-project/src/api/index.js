// API请求封装
import axios from 'axios'

const api = axios.create({ baseURL: 'http://localhost:8088/', timeout: 30000 });

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
export const authAPI = {
    login: (data) => api.post('/api/auth/login', data),
    userinfo: () => api.get('/api/auth/userinfo'),
    logs: (limit) => api.get('/api/admin/logs', { params: { limit } }),
    listUsers: () => api.get('/api/admin/users'),
    addUser: (username, password, role) => api.post('/api/admin/user', { username, password, role }),
    changePassword: (userId, newPassword) => api.put('/api/admin/user/password', { userId, newPassword }),
    updateRole: (userId, role) => api.put('/api/admin/user/role', { userId, role }),
    deleteUser: (id) => api.delete('/api/admin/user/' + id)
};

// 数据API
export const dataAPI = {
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
export const miningAPI = {
    execute: (dataType, taskType, dateStr) =>
        api.post('/api/mining/execute', null, { params: { dataType, taskType, dateStr } }),
    executeAll: (dateStr) =>
        api.post('/api/mining/execute-all', null, { params: { dateStr } }),
    results: (dataType) => api.get('/api/mining/results', { params: { dataType } }),
    result: (taskId) => api.get('/api/mining/result/' + taskId),
    history: (dimension, value, dataType, taskType) =>
        api.get('/api/mining/history', { params: { dimension, value, dataType, taskType } }),
    conclusion: (dataType, taskType) =>
        api.get('/api/mining/conclusion', { params: { dataType, taskType } })
};

// 可视化API
export const visualAPI = {
    overview: () => api.get('/api/visual/overview'),
    trafficHeatmap: (date) => api.get('/api/visual/traffic-heatmap', { params: { date } }),
    congestionPie: (date) => api.get('/api/visual/congestion-pie', { params: { date } }),
    populationBar: (date) => api.get('/api/visual/population-bar', { params: { date } }),
    weatherLine: (date) => api.get('/api/visual/weather-line', { params: { date } }),
    consumptionScatter: (date) => api.get('/api/visual/consumption-scatter', { params: { date } }),
    opinionGauge: (date) => api.get('/api/visual/opinion-gauge', { params: { date } }),
    analysisSummary: () => api.get('/api/visual/analysis-summary'),
    accidentRisk(date) { return api.get('/api/visual/accident-risk', { params: { date: date || '' } }); },
    travelPattern(date) { return api.get('/api/visual/travel-pattern', { params: { date: date || '' } }); },
    exportData(date) { return api.get('/api/visual/export', { params: { date: date || '' } }); },
    sparkStatus() { return api.get('/api/visual/spark-status'); },
    trafficFlow24h(date) { return api.get('/api/visual/traffic-flow-24h', { params: { date: date || '' } }); },
    trafficAnomaly(date) { return api.get('/api/visual/traffic-anomaly', { params: { date: date || '' } }); },
    opinionAnomaly(date) { return api.get('/api/visual/opinion-anomaly', { params: { date: date || '' } }); },
    next24hForecast(date) { return api.get('/api/visual/next-24h-forecast', { params: { date: date || '' } }); },
    populationCommercial: (date) => api.get('/api/visual/population-commercial', { params: { date } }),
    consumptionHeatmap: (date) => api.get('/api/visual/consumption-heatmap', { params: { date } }),
};

// 定时任务配置API
export const schedulerAPI = {
    getConfig: () => api.get('/api/scheduler/config'),
    updateConfig: (autoFixedRateMs, autoCount, dailyCron, dailyCount) =>
        api.post('/api/scheduler/config', null, { params: { autoFixedRateMs, autoCount, dailyCron, dailyCount } })
};

// 实时监控API
export const monitorAPI = {
    overview: () => api.get('/api/monitor/overview'),
    jvm: () => api.get('/api/monitor/jvm'),
    runningTasks: () => api.get('/api/monitor/running-tasks'),
    taskStats: () => api.get('/api/monitor/task-stats'),
    recentTasks: (limit) => api.get('/api/monitor/recent-tasks', { params: { limit } }),
    collectStats: () => api.get('/api/monitor/collect-stats')
};

// MapReduce + Spark Streaming
export const streamingAPI = {
    mapreduce: (dataType, dimension, dateStr) => api.get('/api/streaming/mapreduce', { params: { dataType, dimension, dateStr } }),
    ingest: (payload) => api.post('/api/streaming/ingest', payload),
    recent: (limit) => api.get('/api/streaming/recent', { params: { limit } }),
    stats: (date) => api.get('/api/streaming/stats', { params: date ? { date } : {} }),
    dashboard: (date) => api.get('/api/streaming/dashboard', { params: date ? { date } : {} })
};

// HDFS归档API
export const hdfsAPI = {
    status: () => api.get('/api/hdfs/status'),
    archiveResults: () => api.post('/api/hdfs/archive/results'),
    archiveData: (dataType) => api.post('/api/hdfs/archive/data', null, { params: { dataType } }),
    restore: (hdfsPath, collection) => api.post('/api/hdfs/restore', { hdfsPath, collection })
};

// AI便民建议API
export const suggestionAPI = {
    get: (date) => api.get('/api/ai/suggestion', { params: { date: date || '' } })
};

// AI紧急事件(大屏滚动条)
export const urgentAlertsAPI = {
    get: (date) => api.get('/api/ai/urgent-alerts', { params: { date: date || '' } })
};

// AI生活化预测(给普通人看)
export const lifeAdviceAPI = {
    get: (date) => api.get('/api/ai/life-advice', { params: { date: date || '' } })
};

export default api;
