const { createApp, ref, reactive, onMounted, onUnmounted, nextTick, watch } = Vue;

// Vue挂载成功: 立即隐藏原生登录(Vue优先接管)
(function hideNativeOnVueMount() {
    function hide() {
        var c = document.getElementById('login-container');
        if (c) c.style.display = 'none';
        var v = document.getElementById('vueApp');
        if (v) v.style.display = '';
    }
    // 创建app后立即调用,确保原生登录不出现
    if (document.readyState !== 'loading') hide();
    else document.addEventListener('DOMContentLoaded', hide);
    // 兜底
    setTimeout(hide, 100);
})();

const app = createApp({
    setup() {
        // ========== 登录状态 ==========
        const loggedIn = ref(!!localStorage.getItem('token'));
        const username = ref('');
        const userRole = ref('');
        const loginForm = reactive({ username: '', password: '' });
        const loginLoading = ref(false);

        // ========== Tab ==========
        const activeTab = ref('dashboard');
        // 大屏是否被激活过(用于实时流注入时决定是否同步大屏)
        const dashboardLoaded = ref(false);
        // 实时流/自动采集期间,持续刷新状态卡 + 大屏的轮询定时器
        let collectStatusTimer = null;

        // ========== 数据采集 ==========
        const dataTypes = [
            { label: '交通', value: 'traffic' },
            { label: '气象', value: 'weather' },
            { label: '舆情', value: 'opinion' },
            { label: '消费', value: 'consumption' },
            { label: '人口', value: 'population' }
        ];
        const dataTypeMap = { traffic: '', weather: 'success', opinion: 'warning', consumption: 'danger', population: 'info' };
        const dataTypeLabels = { traffic: '交通', weather: '气象', opinion: '舆情', consumption: '消费', population: '人口' };
        const taskTypeLabels = { statistic: '统计分析', cluster: '聚类分析', association: '关联规则', predict: '预测分析', anomaly: '异常检测', classify: '随机森林分类' };
        // 采集状态卡的图标和颜色
        const collectIcons = ['🚦', '🌦️', '📣', '💰', '👥'];
        const collectColors = ['#409EFF', '#67C23A', '#E6A23C', '#F56C6C', '#909399'];
        const collectBgs = ['#ECF5FF', '#F0F9EB', '#FDF6EC', '#FEF0F0', '#F4F4F5'];
        const collectStatus = reactive({});
        const collectLoading = reactive({});
        const collectAllLoading = ref(false);
        // 三种模式共用数据
        const collectSubTab = ref('manual');
        // 手动
        const manualForm = reactive({ city: '上海', keyword: '新闻', count: 500, dataType: 'all' });
        // 自动
        const autoForm = reactive({ city: '上海', keyword: '新闻', interval: 1800000, count: 500 });
        const autoCollecting = ref(false);
        const autoCollectedCount = ref(0);
        const autoCollectedTotal = ref(0);
        let autoTimer = null;
        // 实时流
        const streamForm = reactive({ dataType: 'traffic', rate: 10, batchSize: 5 });
        const streaming = ref(false);
        const streamInjected = ref(0);
        let streamTimer = null;
        // 系统定时任务配置
        const schedulerConfig = reactive({
            autoFixedRateMs: 1800000,
            autoFixedRateDesc: '30分钟',
            autoCount: 500,
            dailyCron: '0 0 2 * * ?',
            dailyCronDesc: '每天 02:00',
            dailyCount: 500
        });
        const schedulerForm = reactive({
            autoFixedRateMs: 1800000,
            autoCount: 500,
            dailyCron: '0 0 2 * * ?',
            dailyCount: 500
        });
        // 历史回溯
        const historyForm = reactive({ dimension: 'day', dateValue: new Date().toISOString().substring(0, 10), dataType: '', taskType: '' });
        const historyResults = ref([]);
        const historyMeta = ref({});
        const historySummary = reactive({ success: 0, totalDuration: 0 });
        const historyLoading = ref(false);
        const historyDetailVisible = ref(false);
        const currentHistoryResult = ref(null);

        // 实时监控
        const monitorVisible = ref(false);
        const monitorData = reactive({ jvm: {}, runningTasks: [], taskStats: {}, collectStats: { total: 0, byType: {} } });
        const monitorLoading = ref(false);
        const collectBar = ref(null);
        let collectBarChart = null;
        let monitorTimer = null;
        // MapReduce + Streaming
        const mrForm = reactive({ dataType: 'traffic', dimension: 'month', dateStr: '' });
        const mrResult = ref(null);
        const mrLoading = ref(false);
        const streamIngestForm = reactive({ dataType: 'traffic', district: '市中心' });
        const streamRecent = ref([]);
        const streamRecentCount = ref(20);
        const streamIngestedCount = ref(0);
        const streamPushing = ref(false);
        const streamAuto = ref(false);
        let streamAutoTimer = null;

        // ========== 挖掘 ==========
        const taskTypes = reactive({ traffic: 'statistic', weather: 'statistic', opinion: 'statistic', consumption: 'statistic', population: 'statistic' });
        const miningLoading = reactive({});
        const miningTaskOptions = [
            { label: '统计分析', value: 'statistic', icon: '📊', desc: '按区汇总指标' },
            { label: '聚类(K-Means)', value: 'cluster', icon: '🔍', desc: '自动分档高/中/低' },
            { label: '关联规则', value: 'association', icon: '🔗', desc: '发现指标关联' },
            { label: '趋势预测', value: 'predict', icon: '📈', desc: '拟合预测走势' },
            { label: '异常检测', value: 'anomaly', icon: '⚠️', desc: 'IQR识别离群点' }
        ];
        const miningStep = ref(0);
        const advancedCollapse = ref([]);
        const activeMiningType = ref('traffic');
        const miningSelectedTask = ref('statistic');
        const miningSummary = ref({ total: 0, algorithm: '', groups: 0, duration: 0, source: '' });
        const miningChartData = ref([]);
        const aiConclusion = ref('');
        const aiConclusionLoading = ref(false);
        const miningAllLoading = ref(false);
        const miningResults = ref([]);

        // ========== 大屏 ==========
        const statCards = reactive([
            { label: '交通数据', value: 0, color: '#00d4ff' },
            { label: '气象数据', value: 0, color: '#67c23a' },
            { label: '舆情数据', value: 0, color: '#e6a23c' },
            { label: '消费数据', value: 0, color: '#f56c6c' },
            { label: '人口数据', value: 0, color: '#909399' },
            { label: '分析任务', value: 0, color: '#a855f7' }
        ]);
        const selectedDate = ref((() => {
            const d = new Date();
            return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;
        })());
        const refreshTimer = ref(null);
        const refreshEnabled = ref(false);
        const sparkStatus = ref({ status: '未知' });
        const sparkDialogVisible = ref(false);

        // ========== 维度下钻 ==========
        const drillVisible = ref(false);
        const drillTitle = ref('');
        const drillCol1 = ref('');
        const drillCol2 = ref('');
        const drillCol3 = ref('');
        const drillData = ref([]);

        // 图表实例(用于点击下钻)
        const chartInstances = {};

        function onChartDrill(type) {
            if (type === 'trafficFlow') {
                drillTitle.value = '24h交通流量'; drillCol1.value = '车流量'; drillCol2.value = '拥堵指数'; drillCol3.value = '平均速度';
                drillData.value = (trafficFlowRaw.value || []).map(d => ({ district: d.hour, value1: d.flow, value2: d.congestion, value3: d.speed }));
            } else if (type === 'trafficAnomaly') {
                drillTitle.value = '交通异常'; drillCol1.value = '事故数'; drillCol2.value = '拥堵指数'; drillCol3.value = '异常标记';
                drillData.value = (trafficAnomalyRaw.value || []).map(d => ({ district: d.district, value1: d.accidents, value2: d.congestion, value3: d.anomaly ? '是' : '否', anomaly: d.anomaly }));
            } else if (type === 'opinionAnomaly') {
                drillTitle.value = '舆情异常'; drillCol1.value = '热度指数'; drillCol2.value = '负面比例'; drillCol3.value = '正面比例';
                drillData.value = (opinionAnomalyRaw.value || []).map(d => ({ district: d.district, value1: d.hot_index, value2: d.negative_ratio, value3: d.positive_ratio, anomaly: d.anomaly }));
            }
            drillVisible.value = true;
        }

        // 图表原始数据(下钻用)
        const trafficFlowRaw = ref([]);
        const trafficAnomalyRaw = ref([]);
        const opinionAnomalyRaw = ref([]);

        // ========== 图标 ==========
        const ElementIcons = (typeof ElementPlus !== 'undefined' && ElementPlus.Icons) || {};
        const Monitor = ElementIcons.Monitor || null;
        const Download = ElementIcons.Download || null;

        // ========== 日志 ==========
        const logs = ref([]);

        // ========== ECharts实例 ==========
        const charts = {};

        // ========== 登录 ==========
        async function doLogin() {
            if (!loginForm.username || !loginForm.password) {
                ElementPlus.ElMessage.warning('请输入用户名和密码');
                return;
            }
            loginLoading.value = true;
            try {
                const res = await authAPI.login(loginForm);
                if (res.code === 200) {
                    localStorage.setItem('token', res.data.token);
                    username.value = res.data.username;
                    userRole.value = res.data.role;
                    loggedIn.value = true;
                    ElementPlus.ElMessage.success('登录成功');
                } else {
                    ElementPlus.ElMessage.error(res.message);
                }
            } catch (e) {
                ElementPlus.ElMessage.error('登录失败: ' + (e.response?.data?.message || e.message));
            }
            loginLoading.value = false;
        }

        function logout() {
            localStorage.removeItem('token');
            loggedIn.value = false;
            username.value = '';
            userRole.value = '';
        }

        // ========== 数据采集 ==========
        async function collectSingle(dataType) {
            collectLoading[dataType] = true;
            try {
                const res = await dataAPI.collect(dataType, '上海');
                if (res.code === 200) {
                    ElementPlus.ElMessage.success(dataTypeLabels[dataType] + '采集完成: ' + res.data.count + '条');
                } else {
                    ElementPlus.ElMessage.error(res.message);
                }
            } catch (e) {
                ElementPlus.ElMessage.error('采集失败');
            }
            collectLoading[dataType] = false;
            loadCollectStatus();
        }

        async function collectAll() {
            collectAllLoading.value = true;
            try {
                const res = await dataAPI.collectAll('上海');
                if (res.code === 200) {
                    ElementPlus.ElMessage.success('全部采集完成: 共' + res.data.total + '条');
                }
            } catch (e) {
                ElementPlus.ElMessage.error('采集失败');
            }
            collectAllLoading.value = false;
            loadCollectStatus();
        }

        async function loadCollectStatus() {
            try {
                const res = await dataAPI.collectStatus();
                if (res.code === 200 && res.data) {
                    Object.assign(collectStatus, res.data);
                }
            } catch (e) { /* ignore */ }
        }

        // ========== 启动/停止状态轮询(让状态卡+大屏实时同步) ==========
        function startCollectStatusPolling() {
            if (collectStatusTimer) return; // 已在跑
            collectStatusTimer = setInterval(async () => {
                await loadCollectStatus();
                // 大屏Tab只要曾激活过(用户已看过一次),持续同步最新数据
                if (dashboardLoaded.value) {
                    try { await loadAllCharts(); } catch(e) { /* ignore */ }
                }
            }, 3000);
        }
        function stopCollectStatusPolling() {
            if (collectStatusTimer) { clearInterval(collectStatusTimer); collectStatusTimer = null; }
        }

        // ========== 采集状态卡样式辅助 ==========
        function collectCardStyle(idx) {
            return {
                borderTop: `4px solid ${collectColors[idx]}`,
                background: collectBgs[idx],
                transition: 'all 0.3s',
                cursor: 'default'
            };
        }
        function statusTagType(status) {
            if (!status) return 'info';
            if (status === 'success') return 'success';
            if (status === 'failed') return 'danger';
            if (status === '未采集') return 'info';
            return 'primary';
        }

        // ========== 手动采集(支持自定义条数/关键词/城市) ==========
        async function doManualCollect() {
            if (!manualForm.city || !manualForm.keyword) {
                ElementPlus.ElMessage.warning('请填写城市和关键词');
                return;
            }
            collectAllLoading.value = true;
            try {
                if (manualForm.dataType === 'all') {
                    const res = await dataAPI.collectAllWithKeyword(manualForm.city, manualForm.count, manualForm.keyword);
                    if (res.code === 200) {
                        ElementPlus.ElMessage.success(`手动采集完成,共 ${res.data.total} 条 (含 API 数据)`);
                    } else {
                        ElementPlus.ElMessage.error(res.message);
                    }
                } else {
                    const res = await dataAPI.collectWithKeyword(manualForm.dataType, manualForm.city, manualForm.count, manualForm.keyword);
                    if (res.code === 200) {
                        ElementPlus.ElMessage.success(`${dataTypeLabels[manualForm.dataType]}采集完成: ${res.data.count}条`);
                    } else {
                        ElementPlus.ElMessage.error(res.message);
                    }
                }
            } catch (e) {
                ElementPlus.ElMessage.error('采集失败: ' + (e.response?.data?.message || e.message));
            }
            collectAllLoading.value = false;
            loadCollectStatus();
        }

        // ========== 自动采集(可配置间隔毫秒) ==========
        async function startAutoCollect() {
            if (!autoForm.city || !autoForm.keyword) {
                ElementPlus.ElMessage.warning('请填写城市和关键词');
                return;
            }
            if (autoForm.interval < 500) {
                ElementPlus.ElMessage.warning('间隔时间不能小于 500ms');
                return;
            }
            autoCollecting.value = true;
            autoCollectedCount.value = 0;
            autoCollectedTotal.value = 0;
            ElementPlus.ElMessage.success(`自动采集已启动,每 ${autoForm.interval}ms 采集 ${autoForm.count} 条`);

            // 立即采集一次
            doAutoTick();

            autoTimer = setInterval(() => {
                doAutoTick();
            }, autoForm.interval);

            // 启动状态轮询,同步状态卡+大屏
            startCollectStatusPolling();
        }

        async function doAutoTick() {
            if (!autoCollecting.value) return;
            try {
                const res = await dataAPI.collectAllWithKeyword(autoForm.city, autoForm.count, autoForm.keyword);
                if (res.code === 200) {
                    autoCollectedCount.value++;
                    autoCollectedTotal.value += res.data.total;
                }
            } catch (e) { /* ignore */ }
            loadCollectStatus();
        }

        function stopAutoCollect() {
            if (autoTimer) { clearInterval(autoTimer); autoTimer = null; }
            autoCollecting.value = false;
            stopCollectStatusPolling();
            ElementPlus.ElMessage.info(`自动采集已停止,共采集 ${autoCollectedCount.value} 次 / ${autoCollectedTotal.value} 条`);
        }

        // ========== 实时流接入 ==========
        async function startStream() {
            if (streamForm.rate < 1 || streamForm.batchSize < 1) {
                ElementPlus.ElMessage.warning('请设置正确的注入参数');
                return;
            }
            streaming.value = true;
            streamInjected.value = 0;
            ElementPlus.ElMessage.success(`实时流已启动,${streamForm.rate} 条/秒`);

            // 每个 batchSize 条用一次HTTP请求,频率 = 1000ms * batchSize / rate
            const intervalMs = Math.max(50, Math.floor(1000 * streamForm.batchSize / streamForm.rate));
            streamTimer = setInterval(() => {
                doStreamInject();
            }, intervalMs);

            // 启动状态轮询,同步状态卡+大屏
            startCollectStatusPolling();
        }

        async function doStreamInject() {
            if (!streaming.value) return;
            try {
                // 决定本次注入的数据类型
                let types;
                if (streamForm.dataType === 'mixed') {
                    types = ['traffic', 'opinion'];
                } else {
                    types = [streamForm.dataType];
                }
                for (const t of types) {
                    const res = await dataAPI.collectWithKeyword(t, '上海', streamForm.batchSize, '实时流');
                    if (res.code === 200) {
                        streamInjected.value += res.data.count;
                    }
                }
            } catch (e) { /* ignore */ }
            loadCollectStatus();
        }

        function stopStream() {
            if (streamTimer) { clearInterval(streamTimer); streamTimer = null; }
            streaming.value = false;
            stopCollectStatusPolling();
            ElementPlus.ElMessage.info(`实时流已停止,共注入 ${streamInjected.value} 条`);
        }

        // ========== 系统定时任务配置 ==========
        async function loadSchedulerConfig() {
            try {
                const res = await schedulerAPI.getConfig();
                if (res && res.code === 200 && res.data) {
                    Object.assign(schedulerConfig, res.data);
                    Object.assign(schedulerForm, {
                        autoFixedRateMs: res.data.autoFixedRateMs,
                        autoCount: res.data.autoCount,
                        dailyCron: res.data.dailyCron,
                        dailyCount: res.data.dailyCount
                    });
                }
            } catch (e) { console.error(e); }
        }

        async function saveSchedulerConfig() {
            try {
                const res = await schedulerAPI.updateConfig(
                    schedulerForm.autoFixedRateMs,
                    schedulerForm.autoCount,
                    schedulerForm.dailyCron,
                    schedulerForm.dailyCount
                );
                if (res && res.code === 200) {
                    Object.assign(schedulerConfig, res.data);
                    ElementPlus.ElMessage.success('定时任务配置已保存');
                } else {
                    ElementPlus.ElMessage.error(res.message || '保存失败');
                }
            } catch (e) {
                ElementPlus.ElMessage.error('保存失败: ' + (e.response?.data?.message || e.message));
            }
        }

        // ========== 历史回溯 ==========
        function onDimensionChange() {
            // 切换维度时,设置一个合理的默认值
            if (historyForm.dimension === 'day') {
                historyForm.dateValue = new Date().toISOString().substring(0, 10);
            } else if (historyForm.dimension === 'month') {
                historyForm.dateValue = new Date().toISOString().substring(0, 7);
            } else if (historyForm.dimension === 'year') {
                historyForm.dateValue = new Date().getFullYear().toString();
            }
        }

        // 日期/月份/年份的禁用回调:不允许选择今天之后的日期
        function disabledDate(time) {
            return time.getTime() > Date.now();
        }
        function disabledMonth(time) {
            const now = new Date();
            return time.getFullYear() > now.getFullYear() ||
                   (time.getFullYear() === now.getFullYear() && time.getMonth() > now.getMonth());
        }
        function disabledYear(time) {
            return time.getFullYear() > new Date().getFullYear();
        }

        async function queryHistory() {
            if (!historyForm.dateValue) {
                ElementPlus.ElMessage.warning('请先选择时间值');
                return;
            }
            historyLoading.value = true;
            try {
                const res = await miningAPI.history(historyForm.dimension, historyForm.dateValue, historyForm.dataType, historyForm.taskType);
                if (res && res.code === 200) {
                    historyResults.value = res.data.data || [];
                    historyMeta.value = res.data.meta || {};
                    // 计算汇总
                    historySummary.success = historyResults.value.filter(r => r.status === 'success').length;
                    historySummary.totalDuration = historyResults.value.reduce((acc, r) => acc + (r.duration || 0), 0);
                    ElementPlus.ElMessage.success(`查询到 ${historyResults.value.length} 条历史记录`);
                } else {
                    ElementPlus.ElMessage.error(res.message || '查询失败');
                }
            } catch (e) {
                ElementPlus.ElMessage.error('查询失败: ' + (e.response?.data?.message || e.message));
            } finally {
                historyLoading.value = false;
            }
        }

        function showHistoryDetail(row) {
            currentHistoryResult.value = row;
            historyDetailVisible.value = true;
        }

        function exportHistory() {
            if (!historyResults.value.length) return;
            // 构造CSV
            const headers = ['任务ID', '数据类型', '分析类型', '分析日期', '状态', '耗时(ms)', '创建时间'];
            const rows = historyResults.value.map(r => [
                r.taskId,
                dataTypeLabels[r.dataType] || r.dataType,
                taskTypeLabels[r.taskType] || r.taskType,
                r.dateStr,
                r.status === 'success' ? '成功' : '失败',
                r.duration,
                r.createTime
            ]);
            const csv = '\uFEFF' + [headers, ...rows].map(row => row.map(c => `"${String(c).replace(/"/g, '""')}"`).join(',')).join('\r\n');
            const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
            const url = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = `history_${historyForm.dimension}_${historyForm.dateValue}.csv`;
            a.click();
            URL.revokeObjectURL(url);
            ElementPlus.ElMessage.success('已导出 ' + historyResults.value.length + ' 条');
        }

        // ========== 实时监控 ==========
        function openMonitor() {
            monitorVisible.value = true;
            nextTick(() => {
                if (!collectBarChart && collectBar.value) {
                    collectBarChart = echarts.init(collectBar.value);
                }
                loadMonitor();
                startMonitorTimer();
            });
        }

        function startMonitorTimer() {
            stopMonitorTimer();
            monitorTimer = setInterval(() => {
                if (monitorVisible.value) {
                    loadMonitor();
                }
            }, 3000);
        }

        function stopMonitorTimer() {
            if (monitorTimer) {
                clearInterval(monitorTimer);
                monitorTimer = null;
            }
        }

        async function loadMonitor() {
            monitorLoading.value = true;
            try {
                const res = await monitorAPI.overview();
                if (res && res.code === 200 && res.data) {
                    Object.assign(monitorData, res.data);
                    // 渲染采集柱状图
                    await nextTick();
                    if (collectBarChart) {
                        const labels = Object.keys(monitorData.collectStats.byType || {});
                        const values = Object.values(monitorData.collectStats.byType || {});
                        collectBarChart.setOption({
                            tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
                            grid: { left: 60, right: 30, top: 20, bottom: 30 },
                            xAxis: { type: 'category', data: labels, axisLabel: { color: '#a0aec0' } },
                            yAxis: { type: 'value', axisLabel: { color: '#a0aec0' } },
                            series: [{
                                type: 'bar',
                                data: values,
                                itemStyle: {
                                    color: function(p) {
                                        const colors = ['#00d4ff', '#67c23a', '#e6a23c', '#f56c6c', '#9b59b6'];
                                        return colors[p.dataIndex % colors.length];
                                    }
                                },
                                label: { show: true, position: 'top', color: '#a0aec0' }
                            }]
                        });
                    }
                }
            } catch (e) {
                console.error('监控加载失败', e);
            } finally {
                monitorLoading.value = false;
            }
        }

        // ========== 模块四: MapReduce + Spark Streaming ==========
        async function runMapReduce() {
            mrLoading.value = true;
            try {
                const res = await streamingAPI.mapreduce(mrForm.dataType, mrForm.dimension, mrForm.dateStr);
                if (res && res.code === 200) {
                    mrResult.value = res.data;
                    ElementPlus.ElMessage.success(`MapReduce完成: ${res.data.totalGroups}个分组, ${res.data.totalRecords}条记录`);
                } else {
                    ElementPlus.ElMessage.error(res.message || '执行失败');
                }
            } catch (e) {
                ElementPlus.ElMessage.error('执行失败: ' + (e.response?.data?.message || e.message));
            } finally {
                mrLoading.value = false;
            }
        }

        async function pushStream() {
            streamPushing.value = true;
            try {
                const payload = buildStreamPayload(streamIngestForm.dataType, streamIngestForm.district);
                const res = await streamingAPI.ingest(payload);
                if (res && res.code === 200) {
                    streamIngestedCount.value += 1;
                    // 立即插入到头部
                    streamRecent.value.unshift(res.data);
                    if (streamRecent.value.length > streamRecentCount.value) {
                        streamRecent.value = streamRecent.value.slice(0, streamRecentCount.value);
                    }
                }
            } catch (e) {
                console.error(e);
            } finally {
                streamPushing.value = false;
            }
        }

        function buildStreamPayload(dataType, district) {
            const now = new Date();
            const hh = String(now.getHours()).padStart(2, '0');
            const mi = String(now.getMinutes()).padStart(2, '0');
            const ss = String(now.getSeconds()).padStart(2, '0');
            const payload = {
                dataType: dataType,
                district: district,
                timestamp: now.toISOString().substring(0, 19).replace('T', ' '),
                metrics: {}
            };
            // 随机生成指标
            if (dataType === 'traffic') {
                payload.metrics.flow = Math.floor(800 + Math.random() * 2000);
                payload.metrics.congestion = Math.floor(Math.random() * 100);
            } else if (dataType === 'opinion') {
                payload.metrics.heat = Math.floor(50 + Math.random() * 200);
                payload.metrics.sentiment = Math.round(Math.random() * 100) / 100;
            } else if (dataType === 'weather') {
                payload.metrics.temperature = Math.round((5 + Math.random() * 30) * 10) / 10;
                payload.metrics.humidity = Math.floor(30 + Math.random() * 60);
                payload.metrics.aqi = Math.floor(30 + Math.random() * 250);
            } else if (dataType === 'consumption') {
                payload.metrics.amount = Math.floor(1000 + Math.random() * 50000);
                payload.metrics.count = Math.floor(10 + Math.random() * 200);
            } else if (dataType === 'population') {
                payload.metrics.population = Math.floor(50000 + Math.random() * 200000);
                payload.metrics.density = Math.floor(3000 + Math.random() * 25000);
            }
            return payload;
        }

        function startStreamAuto() {
            if (streamAuto.value) return;
            streamAuto.value = true;
            streamAutoTimer = setInterval(() => {
                if (document.visibilityState === 'visible') pushStream();
            }, 1000);
            ElementPlus.ElMessage.success('持续注入已启动(每秒1条)');
        }

        function stopStreamAuto() {
            streamAuto.value = false;
            if (streamAutoTimer) {
                clearInterval(streamAutoTimer);
                streamAutoTimer = null;
            }
            ElementPlus.ElMessage.info('已停止持续注入');
        }

        async function loadStreamRecent() {
            try {
                const res = await streamingAPI.recent(streamRecentCount.value);
                if (res && res.code === 200) {
                    streamRecent.value = res.data.data || [];
                    streamIngestedCount.value = res.data.count || streamRecent.value.length;
                }
            } catch (e) { console.error(e); }
        }

        // ========== 挖掘 ==========
        async function runMiningWithConclusion(taskType) {
            miningSelectedTask.value = taskType;
            miningLoading[activeMiningType.value] = true;
            // 先清空旧结果, 让 v-if 重新挂载图表容器
            miningSummary.value = { total: 0, algorithm: '', groups: 0, duration: 0, source: '' };
            miningChartData.value = [];
            aiConclusion.value = '';
            // 销毁旧图(关键)
            if (charts['miningChart']) {
                charts['miningChart'].dispose();
                delete charts['miningChart'];
            }
            // 等待Vue v-if重渲染后再发请求
            await nextTick();
            try {
                const res = await miningAPI.conclusion(activeMiningType.value, taskType);
                if (res.code === 200 && res.data) {
                    const d = res.data;
                    const r = d.analysisResult || {};
                    // 1. 关键指标
                    miningSummary.value = {
                        total: r.totalRecords || r.total || 0,
                        algorithm: d.taskType || taskType,
                        groups: r.totalGroups || (r.clusters ? r.clusters.length : 0) || (r.rules ? r.rules.length : 0) || 0,
                        duration: r.duration || 0,
                        source: (d.dataType || '') + ' 来自 MongoDB city_data'
                    };
                    // 2. 提取图表数据
                    miningChartData.value = extractMiningChartData(r, taskType);
                    // 3. AI 业务建议
                    aiConclusion.value = d.conclusion || '未生成结论';
                    // 4. 先切到结果步骤,让DOM可见
                    miningStep.value = 2;
                    await nextTick();
                    // 5. DOM可见后再渲染图表
                    renderMiningChart(taskType, d.dataType);
                    ElementPlus.ElMessage.success((dataTypeLabels[activeMiningType.value] || '') + ' 分析完成');
                } else {
                    ElementPlus.ElMessage.error(res.message);
                }
            } catch (e) {
                ElementPlus.ElMessage.error('分析失败: ' + (e.message || e));
            }
            miningLoading[activeMiningType.value] = false;
            loadMiningResults();
        }

        // 从分析结果中提取图表数据
        function extractMiningChartData(r, taskType) {
            if (taskType === 'cluster' && r.clusters) {
                return r.clusters.map(c => ({ name: c.label || ('cluster_' + (c.clusterId ?? c.level ?? '')), value: c.size ?? c.count ?? 0 }));
            }
            if (taskType === 'association' && r.rules) {
                return r.rules.slice(0, 20).map(r2 => ({ name: r2.antecedent?.join(',') + '→' + (r2.consequent?.join(',') || ''), value: Math.round((r2.confidence || 0) * 100) }));
            }
            if (taskType === 'predict' && r.predictions) {
                return r.predictions.map(p => ({ name: p.period, value: Math.round((p.value || 0) * 100) / 100 }));
            }
            if (taskType === 'anomaly' && r.anomalies) {
                return r.anomalies.slice(0, 30).map(a => ({ name: a.district || a.timestamp?.slice(11, 16) || '?', value: a.value || a.score || 0 }));
            }
            if (r.metrics) {
                return Object.entries(r.metrics).slice(0, 15).map(([k, v]) => ({ name: k, value: typeof v === 'number' ? Math.round(v * 100) / 100 : 0 }));
            }
            return [];
        }

        // 渲染挖掘图表
        function renderMiningChart(taskType, dataType) {
            const data = miningChartData.value;
            // 强制销毁旧实例(关键: 防止残留旧数据)
            if (charts['miningChart']) {
                try { charts['miningChart'].dispose(); } catch(e) {}
                delete charts['miningChart'];
            }
            // 重新初始化
            const chart = initChart('miningChart', { tooltip: { trigger: 'axis' } });
            if (!chart) return;
            const typeLabel = (dataTypeLabels && dataTypeLabels[dataType]) || dataType || '数据';
            const taskLabel = ({statistic:'统计',cluster:'聚类',association:'关联',predict:'预测',anomaly:'异常',classify:'分类'})[taskType] || taskType;
            const title = data.length ? (typeLabel + ' - ' + taskLabel + '分析') : (typeLabel + ' 暂无足够数据');
            const barColors = ['#409eff', '#67c23a', '#e6a23c', '#f56c6c', '#a855f7', '#00d4ff', '#ff6b9d', '#20d4ad'];
            chart.setOption({
                title: { text: title, left: 'center', textStyle: { color: '#fff', fontSize: 14 } },
                tooltip: { trigger: 'axis' },
                grid: { top: 50, left: 60, right: 30, bottom: 60 },
                xAxis: { type: 'category', data: data.map(d => String(d.name)), axisLabel: { color: '#fff', rotate: 20, fontSize: 11, interval: 0 } },
                yAxis: { type: 'value', axisLabel: { color: '#fff' } },
                series: [{
                    type: 'bar',
                    data: data.map((d, i) => ({ value: d.value, itemStyle: { color: barColors[i % barColors.length] } })),
                    label: { show: true, position: 'top', color: '#fff' }
                }]
            });
        }

        function getTaskTypeName(t) {
            const m = { statistic: '统计分析结果', cluster: '聚类分析结果', association: '关联规则结果', predict: '趋势预测结果', anomaly: '异常检测结果' };
            return m[t] || t;
        }

        function onMiningTypeChange() {
            // 切换数据类型时:清空全部结果、隐藏图表、重置任务按钮
            miningSummary.value = { total: 0, algorithm: '', groups: 0, duration: 0, source: '' };
            miningChartData.value = [];
            aiConclusion.value = '';
            miningStep.value = 1; // 仍在算法选择步骤
            // 销毁旧图(关键:否则echarts 仍渲染旧数据)
            if (charts['miningChart']) {
                charts['miningChart'].dispose();
                delete charts['miningChart'];
            }
            // 让DOM先更新再清空
            nextTick(() => {
                var dom = document.getElementById('miningChart');
                if (dom) dom.innerHTML = '';
            });
        }

        function onStepClick(step) {
            miningStep.value = step;
        }

        async function executeMining(dataType) {
            miningLoading[dataType] = true;
            try {
                const res = await miningAPI.execute(dataType, taskTypes[dataType], '');
                if (res.code === 200) {
                    ElementPlus.ElMessage.success(dataTypeLabels[dataType] + '分析完成');
                } else {
                    ElementPlus.ElMessage.error(res.message);
                }
            } catch (e) {
                ElementPlus.ElMessage.error('分析失败');
            }
            miningLoading[dataType] = false;
            loadMiningResults();
        }

        async function executeAllMining() {
            miningAllLoading.value = true;
            try {
                const res = await miningAPI.executeAll('');
                if (res.code === 200) {
                    ElementPlus.ElMessage.success('全部5种算法分析完成! 成功' + res.data.success_count + '/5, 请点单个算法查看图表');
                }
            } catch (e) {
                ElementPlus.ElMessage.error('分析失败');
            }
            miningAllLoading.value = false;
            loadMiningResults();
        }

        async function loadMiningResults() {
            try {
                const res = await miningAPI.results('');
                if (res.code === 200 && res.data) {
                    miningResults.value = res.data;
                }
            } catch (e) { /* ignore */ }
        }

        // ========== 大屏图表 ==========
        function initChart(id, option) {
            const dom = document.getElementById(id);
            if (!dom) return null;
            if (charts[id]) charts[id].dispose();
            const chart = echarts.init(dom, 'dark');
            chart.setOption(option);
            charts[id] = chart;
            return chart;
        }

        async function loadCongestionPie() {
            try {
                const res = await visualAPI.congestionPie(selectedDate.value);
                if (res.code === 200 && res.data.data) {
                    initChart('chart-congestion', {
                        tooltip: { trigger: 'item' },
                        legend: { bottom: 0, textStyle: { color: '#8899aa' } },
                        series: [{
                            type: 'pie', radius: ['35%', '65%'],
                            center: ['50%', '45%'],
                            label: { color: '#ccc' },
                            data: res.data.data,
                            emphasis: { itemStyle: { shadowBlur: 20, shadowColor: 'rgba(0,0,0,0.5)' } }
                        }]
                    });
                }
            } catch (e) { console.error(e); }
        }

        async function loadPopulationBar() {
            try {
                const res = await visualAPI.populationBar(selectedDate.value);
                if (res.code === 200 && res.data.data) {
                    const d = res.data.data;
                    initChart('chart-population', {
                        tooltip: { trigger: 'axis' },
                        legend: { data: ['人口密度', '常驻人口'], textStyle: { color: '#8899aa' }, top: 5 },
                        xAxis: { type: 'category', data: d.map(i => i.district), axisLabel: { color: '#8899aa' } },
                        yAxis: { type: 'value', axisLabel: { color: '#8899aa' } },
                        series: [
                            { name: '人口密度', type: 'bar', data: d.map(i => i.density), itemStyle: { color: '#00d4ff' } },
                            { name: '常驻人口', type: 'bar', data: d.map(i => i.resident), itemStyle: { color: '#67c23a' } }
                        ]
                    });
                }
            } catch (e) { console.error(e); }
        }

        async function loadWeatherLine() {
            try {
                const res = await visualAPI.weatherLine(selectedDate.value);
                if (res.code === 200 && res.data.data) {
                    const d = res.data.data.slice(0, 50);
                    // time 是 long 毫秒时间戳,转成 HH:mm
                    const xLabels = d.map(i => {
                        const t = new Date(Number(i.time));
                        return String(t.getHours()).padStart(2,'0') + ':' + String(t.getMinutes()).padStart(2,'0');
                    });
                    initChart('chart-weather', {
                        tooltip: { trigger: 'axis' },
                        legend: { data: ['温度', '湿度', 'AQI'], textStyle: { color: '#8899aa' }, top: 5 },
                        xAxis: { type: 'category', data: xLabels, axisLabel: { color: '#8899aa' } },
                        yAxis: { type: 'value', axisLabel: { color: '#8899aa' } },
                        series: [
                            { name: '温度', type: 'line', data: d.map(i => i.temperature), smooth: true, itemStyle: { color: '#f56c6c' } },
                            { name: '湿度', type: 'line', data: d.map(i => i.humidity), smooth: true, itemStyle: { color: '#409eff' } },
                            { name: 'AQI', type: 'line', data: d.map(i => i.aqi), smooth: true, itemStyle: { color: '#e6a23c' } }
                        ]
                    });
                }
            } catch (e) { console.error(e); }
        }

        async function loadConsumptionScatter() {
            try {
                const res = await visualAPI.consumptionScatter(selectedDate.value);
                if (res.code === 200 && res.data.data) {
                    const d = res.data.data;
                    const scatterData = d.map(i => [i.transactions, i.total_amount]);
                    initChart('chart-consumption', {
                        tooltip: { trigger: 'item', formatter: p => '交易笔数: ' + p.value[0] + '<br/>交易总额: ' + p.value[1] },
                        xAxis: { name: '交易笔数', axisLabel: { color: '#8899aa' } },
                        yAxis: { name: '交易总额', axisLabel: { color: '#8899aa' } },
                        series: [{
                            type: 'scatter', data: scatterData,
                            symbolSize: 8, itemStyle: { color: '#a855f7' }
                        }]
                    });
                }
            } catch (e) { console.error(e); }
        }

        async function loadOpinionGauge() {
            try {
                const res = await visualAPI.opinionGauge(selectedDate.value);
                if (res.code === 200 && res.data) {
                    initChart('chart-opinion', {
                        series: [{
                            type: 'gauge', min: 0, max: 100,
                            axisLine: { lineStyle: { color: [[0.3, '#f56c6c'], [0.7, '#e6a23c'], [1, '#67c23a']], width: 15 } },
                            detail: { formatter: '{value}%', color: '#00d4ff', fontSize: 24 },
                            title: { color: '#8899aa', fontSize: 14 },
                            data: [{ value: res.data.positive_ratio || 65, name: '正面舆情比例' }]
                        }]
                    });
                }
            } catch (e) { console.error(e); }
        }

        async function loadAnalysisSummary() {
            try {
                const res = await visualAPI.analysisSummary();
                if (res.code === 200 && res.data.recent) {
                    const recent = res.data.recent;
                    const types = recent.map(r => r.taskType).reduce((acc, v) => { acc[v] = (acc[v]||0)+1; return acc; }, {});
                    // 英文 -> 中文 映射
                    const TYPE_CN = {
                        'statistic': '统计分析', 'statistics': '统计分析',
                        'cluster': '聚类分析', 'clustering': '聚类分析',
                        'association': '关联分析', 'correlation': '关联分析',
                        'prediction': '预测分析', 'forecast': '预测分析',
                        'anomaly': '异常检测', 'outlier': '异常检测',
                        'classification': '分类分级', 'classify': '分类分级',
                        'classification_analysis': '分类分级',
                        'cluster_analysis': '聚类分析',
                        'association_analysis': '关联分析',
                        'prediction_analysis': '预测分析',
                        'anomaly_analysis': '异常检测',
                        'statistic_analysis': '统计分析'
                    };
                    const COLOR_PALETTE = ['#5470c6', '#91cc75', '#fac858', '#ee6666', '#73c0de', '#3ba272'];
                    const pieData = Object.entries(types).map(([k, v], i) => ({
                        name: TYPE_CN[k] || k,
                        value: v,
                        itemStyle: { color: COLOR_PALETTE[i % COLOR_PALETTE.length] }
                    }));
                    initChart('chart-analysis', {
                        tooltip: { trigger: 'item', formatter: '{b}: {c}次 ({d}%)' },
                        legend: { bottom: 5, textStyle: { color: '#ccc' } },
                        series: [{
                            type: 'pie', radius: ['40%', '65%'],
                            data: pieData,
                            label: { color: '#fff', formatter: '{b}\n{d}%' }
                        }]
                    });
                }
            } catch (e) { console.error(e); }
        }

        async function loadOverview() {
            try {
                const res = await visualAPI.overview();
                if (res.code === 200 && res.data) {
                    statCards[0].value = res.data.traffic_count || 0;
                    statCards[1].value = res.data.weather_count || 0;
                    statCards[2].value = res.data.opinion_count || 0;
                    statCards[3].value = res.data.consumption_count || 0;
                    statCards[4].value = res.data.population_count || 0;
                    statCards[5].value = res.data.analysis_count || 0;
                }
            } catch (e) { /* ignore */ }
        }

        async function loadAllCharts() {
            dashboardLoaded.value = true; // 标记大屏已激活,实时流注入时同步大屏
            const chartNameMap = {};
            chartNameMap['chart-heatmap'] = '拥堵热力图';
            await Promise.allSettled([
                loadOverview(), loadCongestionPie(), loadPopulationBar(),
                loadWeatherLine(), loadConsumptionScatter(),
                loadOpinionGauge(), loadAnalysisSummary()
            ]);
            loadHeatmap();
            loadAccidentRisk();
            loadTravel();
            loadTrafficFlow24h();
            loadTrafficAnomaly();
            loadOpinionAnomaly();
        }

        async function loadLogs() {
            try {
                const res = await authAPI.logs(50);
                if (res.code === 200 && res.data) logs.value = res.data;
            } catch (e) { /* ignore */ }
        }

        // ========== HDFS 归档 ==========
        const hdfsStatus = ref({});
        const hdfsFiles = ref([]);
        const hdfsLoading = ref(false);

        async function loadHdfsStatus() {
            try {
                const res = await hdfsAPI.status();
                if (res.code === 200 && res.data) {
                    hdfsStatus.value = res.data;
                    hdfsFiles.value = res.data.files || [];
                }
            } catch (e) { /* ignore */ }
        }

        async function archiveResultsToHdfs() {
            hdfsLoading.value = true;
            try {
                const res = await hdfsAPI.archiveResults();
                if (res.code === 200) {
                    ElementPlus.ElMessage.success(res.message || '归档成功');
                    loadHdfsStatus();
                } else {
                    ElementPlus.ElMessage.error(res.message || '归档失败');
                }
            } catch (e) { ElementPlus.ElMessage.error('网络错误'); }
            finally { hdfsLoading.value = false; }
        }

        async function archiveDataToHdfs() {
            hdfsLoading.value = true;
            try {
                const res = await hdfsAPI.archiveData();
                if (res.code === 200) {
                    ElementPlus.ElMessage.success(res.message || '归档成功');
                    loadHdfsStatus();
                } else {
                    ElementPlus.ElMessage.error(res.message || '归档失败');
                }
            } catch (e) { ElementPlus.ElMessage.error('网络错误'); }
            finally { hdfsLoading.value = false; }
        }

        async function restoreFromHdfs(row) {
            try {
                await ElementPlus.ElMessageBox.confirm('确定从 HDFS 恢复 [' + row.name + '] 到 MongoDB?', '恢复确认', { type: 'warning' });
            } catch { return; }
            hdfsLoading.value = true;
            try {
                const collection = row.name.includes('analysis_results') ? 'analysis_results' : 'city_data';
                const res = await hdfsAPI.restore(row.name, collection);
                if (res.code === 200) {
                    ElementPlus.ElMessage.success(res.message || '恢复成功');
                } else {
                    ElementPlus.ElMessage.error(res.message || '恢复失败');
                }
            } catch (e) { ElementPlus.ElMessage.error('网络错误'); }
            finally { hdfsLoading.value = false; }
        }

        // ========== 用户管理(仅管理员) ==========
        const users = ref([]);
        const addUserVisible = ref(false);
        const newUser = reactive({ username: '', password: '', role: 'user' });
        const pwdVisible = ref(false);
        const pwdForm = reactive({ userId: null, username: '', newPassword: '' });

        async function loadUsers() {
            if (userRole.value !== 'admin') return;
            try {
                const res = await authAPI.listUsers();
                if (res.code === 200 && res.data) {
                    users.value = res.data;
                } else {
                    ElementPlus.ElMessage.error('加载用户列表失败: ' + res.message);
                }
            } catch (e) { ElementPlus.ElMessage.error('网络错误'); }
        }

        function openAddUser() {
            newUser.username = '';
            newUser.password = '';
            newUser.role = 'user';
            addUserVisible.value = true;
        }

        async function doAddUser() {
            if (!newUser.username || newUser.username.length < 3) {
                return ElementPlus.ElMessage.warning('用户名至少3位');
            }
            if (!newUser.password || newUser.password.length < 4) {
                return ElementPlus.ElMessage.warning('密码至少4位');
            }
            try {
                const res = await authAPI.addUser(newUser.username, newUser.password, newUser.role);
                if (res.code === 200) {
                    ElementPlus.ElMessage.success('新增成功');
                    addUserVisible.value = false;
                    loadUsers();
                } else {
                    ElementPlus.ElMessage.error(res.message);
                }
            } catch (e) { ElementPlus.ElMessage.error('网络错误'); }
        }

        function openChangePassword(row) {
            pwdForm.userId = row.id;
            pwdForm.username = row.username;
            pwdForm.newPassword = '';
            pwdVisible.value = true;
        }

        async function doChangePassword() {
            if (!pwdForm.newPassword || pwdForm.newPassword.length < 4) {
                return ElementPlus.ElMessage.warning('密码至少4位');
            }
            try {
                const res = await authAPI.changePassword(pwdForm.userId, pwdForm.newPassword);
                if (res.code === 200) {
                    ElementPlus.ElMessage.success('密码已修改');
                    pwdVisible.value = false;
                } else {
                    ElementPlus.ElMessage.error(res.message);
                }
            } catch (e) { ElementPlus.ElMessage.error('网络错误'); }
        }

        async function changeRole(row) {
            const newRole = row.role === 'admin' ? 'user' : 'admin';
            const action = newRole === 'admin' ? '升为管理员' : '降为普通用户';
            try {
                await ElementPlus.ElMessageBox.confirm(`确定将 [${row.username}] ${action}？`, '提示', { type: 'warning' });
            } catch { return; }
            try {
                const res = await authAPI.updateRole(row.id, newRole);
                if (res.code === 200) {
                    ElementPlus.ElMessage.success('角色已修改');
                    loadUsers();
                } else {
                    ElementPlus.ElMessage.error(res.message);
                }
            } catch (e) { ElementPlus.ElMessage.error('网络错误'); }
        }

        async function deleteUser(row) {
            try {
                await ElementPlus.ElMessageBox.confirm(`确定删除用户 [${row.username}]？此操作不可恢复！`, '危险', { type: 'error' });
            } catch { return; }
            try {
                const res = await authAPI.deleteUser(row.id);
                if (res.code === 200) {
                    ElementPlus.ElMessage.success('已删除');
                    loadUsers();
                } else {
                    ElementPlus.ElMessage.error(res.message);
                }
            } catch (e) { ElementPlus.ElMessage.error('网络错误'); }
        }

        // ========== 日期筛选 ==========
        function onDateChange(val) {
            selectedDate.value = val;
            loadAllCharts();
        }

        // ========== 自动刷新 ==========
        function toggleRefresh() {
            refreshEnabled.value = !refreshEnabled.value;
            if (refreshEnabled.value) {
                ElementPlus.ElMessage.success('自动刷新已开启，每30秒刷新一次');
                refreshTimer.value = setInterval(() => loadAllCharts(), 30000);
            } else {
                ElementPlus.ElMessage.info('自动刷新已关闭');
                if (refreshTimer.value) { clearInterval(refreshTimer.value); refreshTimer.value = null; }
            }
        }

        // ========== 热力图 ==========
        async function loadHeatmap() {
            try {
                const res = await visualAPI.trafficHeatmap(selectedDate.value);
                if (res && res.code === 200 && res.data && res.data.data) {
                    renderHeatmap(res.data.data);
                }
            } catch (e) { console.error('热力图加载失败', e); }
        }

        function renderHeatmap(data) {
            const chartDom = document.getElementById('chart-heatmap');
            if (!chartDom) return;
            if (charts.heatmap) charts.heatmap.dispose();
            let chart = echarts.init(chartDom);
            if (!data || data.length === 0) return;
            // 按 district 聚合平均拥堵指数
            const byDistrict = {};
            data.forEach(d => {
                if (!d.district) return;
                if (!byDistrict[d.district]) byDistrict[d.district] = { sum: 0, cnt: 0, flow: 0 };
                byDistrict[d.district].sum += Number(d.congestion || 0);
                byDistrict[d.district].cnt += 1;
                byDistrict[d.district].flow += Number(d.flow || 0);
            });
            // 按拥堵指数升序排（拥堵越严重的柱子越高）
            const entries = Object.keys(byDistrict)
                .map(name => {
                    const v = byDistrict[name];
                    const avg = v.sum / v.cnt;
                    const flow = Math.round(v.flow / v.cnt);
                    return { name, avg: Math.round(avg * 1000) / 1000, flow };
                })
                .sort((a, b) => a.avg - b.avg);
            const districts = entries.map(e => e.name);
            const values = entries.map(e => e.avg);
            const flows = entries.map(e => e.flow);

            // 三档颜色: 低<0.4=绿, 0.4~0.6=黄, >0.6=红(明显区分, 一眼看懂)
            function colorFor(v) {
                if (v >= 0.6) return '#f56c6c';   // 高拥堵-红
                if (v >= 0.4) return '#f6c022';   // 中拥堵-黄
                return '#67c23a';                  // 低拥堵-绿
            }
            function labelFor(v) {
                if (v >= 0.6) return '高拥堵';
                if (v >= 0.4) return '中拥堵';
                return '低拥堵';
            }

            chart.setOption({
                tooltip: {
                    trigger: 'axis',
                    axisPointer: { type: 'shadow' },
                    formatter: p => {
                        const i = p[0].dataIndex;
                        return `${districts[i]}<br/>拥堵等级: <b>${labelFor(values[i])}</b><br/>拥堵指数: <b>${values[i]}</b><br/>车流量: ${flows[i]}`;
                    }
                },
                legend: {
                    data: ['低拥堵(绿)', '中拥堵(黄)', '高拥堵(红)'],
                    textStyle: { color: '#ccc' },
                    top: 5
                },
                grid: { left: 60, right: 30, top: 50, bottom: 60 },
                xAxis: { type: 'category', data: districts, axisLabel: { color: '#ccc', fontSize: 10, rotate: 35 }, name: '区域', nameLocation: 'middle', nameGap: 40, nameTextStyle: { color: '#ccc' } },
                yAxis: { type: 'value', name: '拥堵指数(0~1)', nameTextStyle: { color: '#ccc' }, axisLabel: { color: '#ccc' } },
                series: [{
                    type: 'bar',
                    data: values.map((v, i) => ({
                        value: v,
                        name: districts[i],
                        itemStyle: { color: colorFor(v) }
                    })),
                    label: { show: true, position: 'top', fontSize: 10, color: '#fff', formatter: p => p.value.toFixed(2) },
                    itemStyle: { borderRadius: [4, 4, 0, 0] },
                    emphasis: { itemStyle: { shadowBlur: 10, shadowColor: 'rgba(0,0,0,0.5)' } }
                }]
            });
            charts.heatmap = chart;
        }

        // ========== 事故风险 ==========
        async function loadAccidentRisk() {
            try {
                const res = await visualAPI.accidentRisk(selectedDate.value);
                if (res && res.code === 200 && res.data && res.data.data) {
                    const chartDom = document.getElementById('chart-accident');
                    if (!chartDom) return;
                    if (charts.accident) charts.accident.dispose();
                    let chart = echarts.init(chartDom);
                    const data = res.data.data;
                    const districts = data.map(d => d.district);
                    const accidents = data.map(d => d.accidents);
                    // 按风险等级分组(颜色 + 排序): 高风险在左/上,中、低依次排列
                    const riskOrder = { '高风险': 0, '中风险': 1, '低风险': 2 };
                    const sortedIdx = data.map((d, i) => i)
                        .sort((a, b) => (riskOrder[data[a].risk] ?? 3) - (riskOrder[data[b].risk] ?? 3));
                    const sortedDistricts = sortedIdx.map(i => districts[i]);
                    const sortedAccidents = sortedIdx.map(i => accidents[i]);
                    const sortedRisks = sortedIdx.map(i => data[i].risk);
                    const colorMap = { '高风险': '#f56c6c', '中风险': '#e6a23c', '低风险': '#67c23a' };
                    chart.setOption({
                        tooltip: {
                            trigger: 'axis',
                            axisPointer: { type: 'shadow' },
                            formatter: p => {
                                const i = p[0].dataIndex;
                                return `${sortedDistricts[i]}<br/>事故数: <b>${sortedAccidents[i]}</b><br/>风险等级: <b style="color:${colorMap[sortedRisks[i]]}">${sortedRisks[i]}</b>`;
                            }
                        },
                        legend: {
                            data: ['低风险', '中风险', '高风险'],
                            textStyle: { color: '#ccc' },
                            top: 5
                        },
                        grid: { left: 70, right: 30, top: 45, bottom: 30 },
                        xAxis: { type: 'value', name: '事故数', nameTextStyle: { color: '#ccc' }, axisLabel: { color: '#ccc' } },
                        yAxis: { type: 'category', data: sortedDistricts, axisLabel: { color: '#ccc', fontSize: 11 } },
                        series: [{
                            name: '事故数',
                            type: 'bar',
                            data: sortedAccidents.map((v, i) => ({
                                value: v,
                                itemStyle: { color: colorMap[sortedRisks[i]] }
                            })),
                            label: { show: true, position: 'right', color: '#fff', formatter: p => `${p.value}起` },
                            itemStyle: { borderRadius: [0, 4, 4, 0] }
                        }]
                    });
                    charts.accident = chart;
                }
            } catch (e) { console.error('事故风险加载失败', e); }
        }

        // ========== 出行规律 ==========
        async function loadTravel() {
            try {
                const res = await visualAPI.travelPattern(selectedDate.value);
                if (res && res.code === 200 && res.data && res.data.data) {
                    const chartDom = document.getElementById('chart-travel');
                if (!chartDom) return;
                if (charts.travel) charts.travel.dispose();
                let chart = echarts.init(chartDom);
                    chart.setOption({
                        tooltip: { trigger:'axis' },
                        grid: { left:50, right:30, top:20, bottom:40 },
                        xAxis: { type:'category', data:res.data.data.map(d=>d.hour), axisLabel:{color:'#ccc',fontSize:9,rotate:45} },
                        yAxis: { type:'value', name:'平均车流量', nameTextStyle:{color:'#ccc'} },
                        series: [{ type:'line', data:res.data.data.map(d=>d.avg_flow), smooth:true,
                            areaStyle:{color:{type:'linear',x:0,y:0,x2:0,y2:1,colorStops:[{offset:0,color:'rgba(0,245,255,0.3)'},{offset:1,color:'rgba(0,245,255,0)'}]}},
                            lineStyle:{color:'#00f5ff'}, itemStyle:{color:'#00f5ff'} }]
                    });
                    const chartNameMap = {};
                    chartNameMap['chart-travel'] = '出行规律';
                    charts.travel = chart;
                }
            } catch(e) { console.error('出行规律加载失败', e); }
        }

        // ========== 24h交通流量趋势(简化: 去掉平均速度曲线, 只保留车流量柱+拥堵指数线) ==========
        async function loadTrafficFlow24h() {
            try {
                const res = await visualAPI.trafficFlow24h(selectedDate.value);
                if (res && res.code === 200 && res.data && res.data.data) {
                    trafficFlowRaw.value = res.data.data;
                    const chartDom = document.getElementById('chart-traffic-flow');
                    if (!chartDom) return;
                    if (charts.flow) charts.flow.dispose();
                    let chart = echarts.init(chartDom);
                    const hours = res.data.data.map(d => d.hour + ':00');
                    const flows = res.data.data.map(d => d.flow);
                    const congs = res.data.data.map(d => d.congestion);
                    chart.setOption({
                        tooltip: { trigger:'axis' },
                        legend: { data:['车流量','拥堵指数'], textStyle:{color:'#ccc'}, top:5 },
                        grid: { left:55, right:55, top:40, bottom:40 },
                        xAxis: { type:'category', data: hours, axisLabel:{color:'#ccc',fontSize:9,rotate:45} },
                        yAxis: [
                            { type:'value', name:'车流量(辆)', nameTextStyle:{color:'#ccc'}, axisLabel:{color:'#ccc'} },
                            { type:'value', name:'拥堵指数', min:0, max:1, nameTextStyle:{color:'#ccc'}, axisLabel:{color:'#ccc'} }
                        ],
                        series: [
                            { name:'车流量', type:'bar', data: flows, itemStyle:{color:'#5470c6',borderRadius:[4,4,0,0]},
                              label:{show:true,position:'top',color:'#fff',fontSize:9,formatter:p=>p.value>=1000?Math.round(p.value/100)/10+'k':p.value} },
                            { name:'拥堵指数', type:'line', yAxisIndex:1, data: congs, smooth:true, symbol:'circle', symbolSize:6,
                              lineStyle:{color:'#ee6666',width:3}, itemStyle:{color:'#ee6666'},
                              markLine:{silent:true, data:[{yAxis:0.7,name:'拥堵警戒线 0.7',lineStyle:{color:'#ff4757',type:'dashed'}}]} }
                        ]
                    });
                    chart.on('click', () => onChartDrill('trafficFlow'));
                    charts.flow = chart;
                }
            } catch(e) { console.error('24h流量加载失败', e); }
        }

        // ========== 交通异常检测 ==========
        async function loadTrafficAnomaly() {
            try {
                const res = await visualAPI.trafficAnomaly(selectedDate.value);
                if (res && res.code === 200 && res.data && res.data.data) {
                    trafficAnomalyRaw.value = res.data.data;
                    const chartDom = document.getElementById('chart-traffic-anomaly');
                if (!chartDom) return;
                if (charts.trafficAnomaly) charts.trafficAnomaly.dispose();
                let chart = echarts.init(chartDom);
                    chart.setOption({
                        tooltip: { trigger:'axis' },
                        grid: { left:55, right:25, top:10, bottom:50 },
                        xAxis: { type:'category', data:res.data.data.map(d=>d.district), axisLabel:{color:'#ccc',fontSize:9,rotate:30} },
                        yAxis: { type:'value', name:'事故数' },
                        series: [{
                            type:'bar', data:res.data.data.map(d=> ({
                                value: d.accidents,
                                itemStyle: { color: d.anomaly ? '#ff4757' : '#2ed573' }
                            })), itemStyle:{borderRadius:[4,4,0,0]},
                            markLine:{silent:true,data:[{yAxis:res.data.avg_accidents,name:'均值',lineStyle:{color:'#ffa502'}}]}
                        }]
                    });
                    chart.on('click', () => onChartDrill('trafficAnomaly'));
                    charts.trafficAnomaly = chart;
                }
            } catch(e) { console.error('交通异常加载失败', e); }
        }

        // ========== 舆情异常识别(简化直观: 左轴热度柱+右轴负面比例线) ==========
        async function loadOpinionAnomaly() {
            try {
                const res = await visualAPI.opinionAnomaly(selectedDate.value);
                if (res && res.code === 200 && res.data && res.data.data) {
                    opinionAnomalyRaw.value = res.data.data;
                    const chartDom = document.getElementById('chart-opinion-anomaly');
                    if (!chartDom) return;
                    if (charts.opinionAnomaly) charts.opinionAnomaly.dispose();
                    let chart = echarts.init(chartDom);
                    const data = res.data.data;
                    const districts = data.map(d => d.district);
                    const hotIndex = data.map(d => d.hot_index || 0);
                    const negRatio = data.map(d => Math.round((d.negative_ratio || 0) * 1000) / 10);  // 转 %
                    const anomaly = data.map(d => d.anomaly);
                    // 异常区上方的提示: 用红色柱突出
                    chart.setOption({
                        tooltip: {
                            trigger: 'axis',
                            formatter: p => {
                                const i = p[0].dataIndex;
                                const status = anomaly[i] ? '<span style="color:#ff4757">⚠ 异常</span>' : '<span style="color:#67c23a">✓ 正常</span>';
                                return `${districts[i]}<br/>热度: <b>${hotIndex[i]}</b><br/>负面比例: <b>${negRatio[i]}%</b><br/>状态: ${status}`;
                            }
                        },
                        legend: { data:['热度指数','负面比例(%)'], textStyle:{color:'#ccc'}, top:5 },
                        grid: { left:55, right:55, top:40, bottom:50 },
                        xAxis: { type:'category', data:districts, axisLabel:{color:'#ccc',fontSize:10,rotate:30} },
                        yAxis: [
                            { type:'value', name:'热度指数', nameTextStyle:{color:'#ccc'}, axisLabel:{color:'#ccc'} },
                            { type:'value', name:'负面比例(%)', min:0, max:100, nameTextStyle:{color:'#ccc'}, axisLabel:{color:'#ccc'} }
                        ],
                        series: [
                            { name:'热度指数', type:'bar', data:hotIndex.map((v, i) => ({
                                value: v,
                                itemStyle: { color: anomaly[i] ? '#ff4757' : '#5470c6', borderRadius:[4,4,0,0] }
                            })),
                              label:{show:true, position:'top', color:'#fff', fontSize:9} },
                            { name:'负面比例(%)', type:'line', yAxisIndex:1, data:negRatio, smooth:true, symbol:'circle', symbolSize:8,
                              lineStyle:{color:'#ee6666', width:3}, itemStyle:{color:'#ee6666'} }
                        ]
                    });
                    chart.on('click', () => onChartDrill('opinionAnomaly'));
                    charts.opinionAnomaly = chart;
                }
            } catch(e) { console.error('舆情异常加载失败', e); }
        }

        // ========== 导出数据 ==========
        async function exportDashboard() {
            try {
                ElementPlus.ElMessage.info('正在导出数据...');
                const res = await visualAPI.exportData(selectedDate.value);
                if (res && res.code === 200) {
                    const blob = new Blob([JSON.stringify(res.data, null, 2)], {type:'application/json'});
                    const url = URL.createObjectURL(blob);
                    const a = document.createElement('a');
                    a.href = url; a.download = `dashboard-export-${selectedDate.value}.json`;
                    a.click(); URL.revokeObjectURL(url);
                    ElementPlus.ElMessage.success('大屏数据导出成功！');
                }
            } catch(e) { ElementPlus.ElMessage.error('导出失败: ' + e.message); }
        }

        // ========== Spark状态 ==========
        async function showSparkStatus() {
            try {
                const res = await visualAPI.sparkStatus();
                if (res && res.code === 200) {
                    sparkStatus.value = res.data;
                    sparkDialogVisible.value = true;
                }
            } catch(e) { ElementPlus.ElMessage.error('获取Spark状态失败'); }
        }

        // ========== Tab切换 ==========
        function onTabChange(tab) {
            if (tab.props.name === 'dashboard') {
                nextTick(() => loadAllCharts());
            } else if (tab.props.name === 'collect') {
                loadCollectStatus();
                loadSchedulerConfig();
            } else if (tab.props.name === 'history') {
                if (historyResults.value.length === 0) {
                    queryHistory();
                }
            } else if (tab.props.name === 'mining') {
                loadMiningResults();
                if (streamRecent.value.length === 0) {
                    loadStreamRecent();
                }
            } else if (tab.props.name === 'report') {
                loadLogs();
                loadHdfsStatus();
            } else if (tab.props.name === 'admin') {
                loadUsers();
            }
        }

        // ========== 窗口resize ==========
        window.addEventListener('resize', () => {
            Object.values(charts).forEach(c => c.resize());
        });

        // ========== 初始化 ==========
        onMounted(async () => {
            // 修复: 无条件先初始化日期为今天(原代码写在 if (loggedIn) 内,当 checkLogin 失败时日期永远为空)
            const _d = new Date();
            const _today = `${_d.getFullYear()}-${String(_d.getMonth()+1).padStart(2,'0')}-${String(_d.getDate()).padStart(2,'0')}`;
            if (!selectedDate.value) selectedDate.value = _today;

            if (loggedIn.value) {
                try {
                    const res = await authAPI.userinfo();
                    if (res.code === 200) {
                        username.value = res.data.username;
                        userRole.value = res.data.role;
                    }
                } catch (e) {
                    logout();
                }
                if (!selectedDate.value) selectedDate.value = _today;
                nextTick(() => loadAllCharts());
            }
        });

        onUnmounted(() => {
            if (refreshTimer.value) { clearInterval(refreshTimer.value); }
            stopMonitorTimer();
            stopStreamAuto();
            if (autoTimer) { clearInterval(autoTimer); autoTimer = null; }
            if (streamTimer) { clearInterval(streamTimer); streamTimer = null; }
            stopCollectStatusPolling();
            Object.values(charts).forEach(c => { try { c.dispose(); } catch(e) {} });
        });

        return {
            loggedIn, username, userRole, loginForm, loginLoading, doLogin, logout,
            activeTab, onTabChange,
            dataTypes, dataTypeMap, dataTypeLabels, collectStatus, collectLoading, collectAllLoading,
            collectIcons, collectColors, collectBgs, collectCardStyle, statusTagType,
            collectSingle, collectAll, loadCollectStatus,
            collectSubTab, manualForm, doManualCollect,
            autoForm, autoCollecting, autoCollectedCount, autoCollectedTotal,
            startAutoCollect, stopAutoCollect,
            streamForm, streaming, streamInjected, startStream, stopStream,
            schedulerConfig, schedulerForm, loadSchedulerConfig, saveSchedulerConfig,
            historyForm, historyResults, historyMeta, historySummary, historyLoading,
            historyDetailVisible, currentHistoryResult, taskTypeLabels,
            onDimensionChange, queryHistory, showHistoryDetail, exportHistory,
            disabledDate, disabledMonth, disabledYear,
            monitorVisible, monitorData, monitorLoading, collectBar,
            openMonitor, loadMonitor,
            mrForm, mrResult, mrLoading, runMapReduce,
            streamIngestForm, streamRecent, streamRecentCount, streamIngestedCount,
            streamPushing, streamAuto, pushStream, startStreamAuto, stopStreamAuto, loadStreamRecent,
            taskTypes, miningLoading, miningAllLoading, miningResults,
            executeMining, executeAllMining,
            miningTaskOptions, activeMiningType, miningSelectedTask, miningStep, advancedCollapse, onStepClick,
            miningSummary, miningChartData, aiConclusion, aiConclusionLoading,
            runMiningWithConclusion, onMiningTypeChange,
            statCards, logs,
            users, addUserVisible, newUser, openAddUser, doAddUser,
            pwdVisible, pwdForm, openChangePassword, doChangePassword,
            changeRole, deleteUser, loadUsers,
            hdfsStatus, hdfsFiles, hdfsLoading, loadHdfsStatus,
            archiveResultsToHdfs, archiveDataToHdfs, restoreFromHdfs,
            selectedDate, refreshTimer, refreshEnabled, sparkStatus, sparkDialogVisible,
            onDateChange, toggleRefresh, loadHeatmap, loadAccidentRisk, loadTravel,
            exportDashboard, showSparkStatus,
            loadTrafficFlow24h, loadTrafficAnomaly, loadOpinionAnomaly,
            drillVisible, drillTitle, drillCol1, drillCol2, drillCol3, drillData, onChartDrill,
            Monitor, Download
        };
    }
});

app.use(ElementPlus);
app.mount('#vueApp');
