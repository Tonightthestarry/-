const { createApp, ref, reactive, onMounted, onUnmounted, nextTick, watch } = Vue;

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
        const collectStatus = reactive({});
        const collectLoading = reactive({});
        const collectAllLoading = ref(false);

        // ========== 挖掘 ==========
        const taskTypes = reactive({ traffic: 'statistic', weather: 'statistic', opinion: 'statistic', consumption: 'statistic', population: 'statistic' });
        const miningLoading = reactive({});
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
        const selectedDate = ref('');
        const refreshTimer = ref(null);
        const refreshEnabled = ref(false);
        const sparkStatus = ref({ status: '未知' });
        const sparkDialogVisible = ref(false);

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

        // ========== 挖掘 ==========
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
                    ElementPlus.ElMessage.success('全部分析完成: 成功' + res.data.success_count + '/5');
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
                    initChart('chart-weather', {
                        tooltip: { trigger: 'axis' },
                        legend: { data: ['温度', '湿度', 'AQI'], textStyle: { color: '#8899aa' }, top: 5 },
                        xAxis: { type: 'category', data: d.map(i => i.time?.substring(11,16)), axisLabel: { color: '#8899aa' } },
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
                    initChart('chart-analysis', {
                        tooltip: { trigger: 'item' },
                        series: [{
                            type: 'pie', radius: '60%',
                            data: Object.entries(types).map(([k,v]) => ({ name: k, value: v })),
                            label: { color: '#ccc' }
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
        }

        async function loadLogs() {
            try {
                const res = await authAPI.logs(50);
                if (res.code === 200 && res.data) logs.value = res.data;
            } catch (e) { /* ignore */ }
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
            let chart = echarts.init(chartDom);
            const districts = ['中心区','东城区','西城区','南湖区','北山区','高新区'];
            const coords = data.map(d => [districts.indexOf(d.district) % 3, Math.floor(districts.indexOf(d.district) / 3), d.congestion || 1]);
            chart.setOption({
                tooltip: { formatter: p => p.data ? `${data[p.data[0] + p.data[1]*3] ? data[p.data[0] + p.data[1]*3].district : ''}: ${p.data[2]}级拥堵` : '' },
                grid: { left:60, right:40, top:20, bottom:60 },
                xAxis: { type:'category', data:['东','中','西'], axisLabel:{fontSize:11} },
                yAxis: { type:'category', data:['南','中','北'], axisLabel:{fontSize:11} },
                visualMap: { min:1, max:10, calculable:true, orient:'horizontal', left:'center', bottom:5,
                    inRange:{color:['#50a3ba','#eac736','#d94e5d']} },
                series: [{ type:'heatmap', data:coords, label:{show:true,fontSize:10},
                    emphasis:{itemStyle:{shadowBlur:10,shadowColor:'rgba(0,0,0,0.5)'}} }]
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
                    let chart = echarts.init(chartDom);
                    const districts = res.data.data.map(d => d.district);
                    const accidents = res.data.data.map(d => d.accidents);
                    const risks = res.data.data.map(d => d.risk);
                    chart.setOption({
                        tooltip: { trigger:'axis' },
                        legend: { data:['事故数','风险等级'], textStyle:{color:'#ccc'} },
                        xAxis: { type:'category', data:districts, axisLabel:{color:'#ccc',fontSize:10} },
                        yAxis: [{ type:'value', name:'事故数', nameTextStyle:{color:'#ccc'} },
                                { type:'value', name:'风险', nameTextStyle:{color:'#ccc'} }],
                        series: [
                            { name:'事故数', type:'bar', data:accidents,
                                itemStyle:{color:p=>['低风险','中风险','高风险'].indexOf(risks[p.dataIndex])>=0?
                                    ['#67c23a','#e6a23c','#f56c6c'][['低风险','中风险','高风险'].indexOf(risks[p.dataIndex])]:
                                    '#67c23a'} },
                            { name:'风险等级', type:'line', yAxisIndex:1,
                                data:risks.map(r=>r==='高风险'?3:r==='中风险'?2:1),
                                lineStyle:{color:'#f56c6c'}, itemStyle:{color:'#f56c6c'} }
                        ]
                    });
                    charts.accident = chart;
                }
            } catch(e) { console.error('事故风险加载失败', e); }
        }

        // ========== 出行规律 ==========
        async function loadTravel() {
            try {
                const res = await visualAPI.travelPattern(selectedDate.value);
                if (res && res.code === 200 && res.data && res.data.data) {
                    const chartDom = document.getElementById('chart-travel');
                    if (!chartDom) return;
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
            } else if (tab.props.name === 'mining') {
                loadMiningResults();
            } else if (tab.props.name === 'report') {
                loadLogs();
            }
        }

        // ========== 窗口resize ==========
        window.addEventListener('resize', () => {
            Object.values(charts).forEach(c => c.resize());
        });

        // ========== 初始化 ==========
        onMounted(async () => {
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
                const d = new Date(); selectedDate.value = `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;
                nextTick(() => loadAllCharts());
            }
        });

        onUnmounted(() => {
            if (refreshTimer.value) { clearInterval(refreshTimer.value); }
        });

        return {
            loggedIn, username, userRole, loginForm, loginLoading, doLogin, logout,
            activeTab, onTabChange,
            dataTypes, dataTypeMap, dataTypeLabels, collectStatus, collectLoading, collectAllLoading,
            collectSingle, collectAll,
            taskTypes, miningLoading, miningAllLoading, miningResults,
            executeMining, executeAllMining,
            statCards, logs,
            selectedDate, refreshTimer, refreshEnabled, sparkStatus, sparkDialogVisible,
            onDateChange, toggleRefresh, loadHeatmap, loadAccidentRisk, loadTravel,
            exportDashboard, showSparkStatus,
            Monitor, Download
        };
    }
});

app.use(ElementPlus);
app.mount('#app');
