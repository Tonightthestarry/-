<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>城市级多源海量数据智能挖掘与可视化决策平台</title>
    <link rel="stylesheet" href="lib/element-plus/index.css">
    <link rel="stylesheet" href="css/style.css">
    <script src="lib/vue.global.prod.js"></script>
    <script src="lib/element-plus.full.min.js"></script>
    <script src="lib/element-plus-zh-cn.min.js"></script>
    <script src="lib/echarts.min.js"></script>
    <script src="lib/axios.min.js"></script>
</head>
<body>
<div id="app">
    <!-- 登录页 (独立纯HTML,Vue挂载失败时也能用) -->
    <div id="login-container" style="display:none;">
        <div class="login-card">
            <h1>城市级多源海量数据智能挖掘与可视化决策平台</h1>
            <form id="nativeLoginForm" style="width:100%;">
                <div class="login-field">
                    <input id="nativeUsername" type="text" placeholder="用户名" autocomplete="username" class="login-input" required>
                </div>
                <div class="login-field">
                    <input id="nativePassword" type="password" placeholder="密码" autocomplete="current-password" class="login-input" required>
                </div>
                <button type="submit" id="nativeLoginBtn" class="login-btn">登 录</button>
                <p style="text-align:center;color:#999;margin-top:16px;font-size:13px;">管理员: admin/123456 | 用户: user/123456</p>
            </form>
        </div>
    </div>

    <!-- Vue主容器 -->
    <div id="vueApp" style="display:none;">
        <div v-if="!loggedIn" id="vue-login-fallback" style="display:none;"></div>
        <div v-else id="main-container">
        <!-- 顶栏 -->
        <header class="top-bar">
            <span class="title">城市级多源海量数据智能挖掘与可视化决策平台</span>
            <span class="user-info">
                {{ userRole === 'admin' ? '管理员' : '普通用户' }}: {{ username }}
                <el-button type="danger" size="small" @click="logout" text>退出</el-button>
            </span>
        </header>
        <!-- 普通用户只读提示 -->
        <div v-if="userRole !== 'admin'" style="background:#fdf6ec;color:#e6a23c;padding:8px 24px;border-bottom:1px solid #faecd8;text-align:center;font-size:13px;">
            🔒 您当前以 <b>普通用户</b> 身份登录,数据采集/挖掘分析/实时流/定时配置等操作需管理员权限
        </div>

        <!-- Tab导航 -->
        <el-tabs v-model="activeTab" type="border-card" @tab-click="onTabChange">
            <el-tab-pane label="数据大屏" name="dashboard">
                <div class="dashboard">
                    <!-- 数字卡片 -->
                    <el-row :gutter="16" class="stat-cards">
                        <el-col :span="4" v-for="card in statCards" :key="card.label">
                            <el-card shadow="hover">
                                <div class="stat-value" :style="{color:card.color}">{{ card.value }}</div>
                                <div class="stat-label">{{ card.label }}</div>
                            </el-card>
                        </el-col>
                    </el-row>

                    <!-- 工具栏 -->
                        <div class="chart-toolbar" style="display:flex;justify-content:space-between;align-items:center;margin-bottom:16px;flex-wrap:wrap;gap:10px;">
                            <div style="display:flex;align-items:center;gap:10px;">
                                <el-button type="primary" size="small" @click="openMonitor">📊 实时监控</el-button>
                                <span style="color:#a0aec0;font-size:13px;">分析日期</span>
                                <el-date-picker v-model="selectedDate" type="date" placeholder="选择日期"
                                    format="YYYY-MM-DD" value-format="YYYY-MM-DD" size="small"
                                    :disabled-date="disabledDate"
                                    @change="onDateChange" style="width:170px;" />
                                <el-button type="primary" size="small" @click="toggleRefresh" :type="refreshEnabled ? 'success' : 'default'" style="margin-left:4px;">
                                    {{ refreshEnabled ? '自动刷新中(30s)' : '开启自动刷新' }}
                                </el-button>
                            </div>
                            <div style="display:flex;gap:8px;">
                                <el-button type="warning" size="small" @click="showSparkStatus">
                                    <el-icon style="margin-right:4px;"><Monitor /></el-icon>Spark集群状态
                                </el-button>
                                <el-button type="success" size="small" @click="exportDashboard">
                                    <el-icon style="margin-right:4px;"><Download /></el-icon>导出大屏数据
                                </el-button>
                            </div>
                        </div>

                    <!-- 图表行 -->
                    <el-row :gutter="16" style="margin-top:16px">
                        <el-col :span="12">
                            <el-card header="交通拥堵等级分布">
                                <div id="chart-congestion" style="height:320px"></div>
                            </el-card>
                        </el-col>
                        <el-col :span="12">
                            <el-card header="各区域人口分布">
                                <div id="chart-population" style="height:320px"></div>
                            </el-card>
                        </el-col>
                    </el-row>
                    <el-row :gutter="16" style="margin-top:16px">
                        <el-col :span="12">
                            <el-card header="气象指标趋势">
                                <div id="chart-weather" style="height:320px"></div>
                            </el-card>
                        </el-col>
                        <el-col :span="12">
                            <el-card header="消费交易分析">
                                <div id="chart-consumption" style="height:320px"></div>
                            </el-card>
                        </el-col>
                    </el-row>
                    <el-row :gutter="16" style="margin-top:16px">
                        <el-col :span="12">
                            <el-card header="舆情情感仪表盘">
                                <div id="chart-opinion" style="height:280px"></div>
                            </el-card>
                        </el-col>
                        <el-col :span="12">
                            <el-card header="分析任务概览">
                                <div id="chart-analysis" style="height:280px"></div>
                            </el-card>
                        </el-col>
                    </el-row>
                    <!-- 热力图 + 事故风险 -->
                    <el-row :gutter="16" style="margin-bottom:16px;">
                        <el-col :span="12">
                            <el-card shadow="hover" class="chart-card">
                                <div class="chart-title">城市拥堵热力图</div>
                                <div id="chart-heatmap" style="height:360px;"></div>
                            </el-card>
                        </el-col>
                        <el-col :span="12">
                            <el-card shadow="hover" class="chart-card">
                                <div class="chart-title">事故风险分类</div>
                                <div id="chart-accident" style="height:360px;"></div>
                            </el-card>
                        </el-col>
                    </el-row>
                    <!-- 出行规律 -->
                    <el-row :gutter="16" style="margin-bottom:16px;">
                        <el-col :span="24">
                            <el-card shadow="hover" class="chart-card">
                                <div class="chart-title">24h出行规律</div>
                                <div id="chart-travel" style="height:300px;"></div>
                            </el-card>
                        </el-col>
                    </el-row>
                    <!-- 24h交通流量趋势 + 交通异常 -->
                    <el-row :gutter="16" style="margin-bottom:16px;">
                        <el-col :span="12">
                            <el-card shadow="hover" class="chart-card">
                                <div class="chart-title">24h交通流量趋势</div>
                                <div id="chart-traffic-flow" style="height:340px;" @click="onChartDrill('trafficFlow')"></div>
                            </el-card>
                        </el-col>
                        <el-col :span="12">
                            <el-card shadow="hover" class="chart-card">
                                <div class="chart-title">交通异常检测(区域)</div>
                                <div id="chart-traffic-anomaly" style="height:340px;" @click="onChartDrill('trafficAnomaly')"></div>
                            </el-card>
                        </el-col>
                    </el-row>
                    <!-- 舆情异常 -->
                    <el-row :gutter="16" style="margin-bottom:16px;">
                        <el-col :span="24">
                            <el-card shadow="hover" class="chart-card">
                                <div class="chart-title">舆情热点异常识别</div>
                                <div id="chart-opinion-anomaly" style="height:320px;" @click="onChartDrill('opinionAnomaly')"></div>
                            </el-card>
                        </el-col>
                    </el-row>
                    <!-- 维度下钻弹窗 -->
                    <el-dialog v-model="drillVisible" :title="'维度下钻: ' + drillTitle" width="700px">
                        <el-table :data="drillData" stripe max-height="400" border>
                            <el-table-column prop="district" label="区域" width="100"></el-table-column>
                            <el-table-column prop="value1" :label="drillCol1" width="150"></el-table-column>
                            <el-table-column prop="value2" :label="drillCol2" width="150"></el-table-column>
                            <el-table-column prop="value3" :label="drillCol3" width="150"></el-table-column>
                            <el-table-column label="状态">
                                <template #default="s">
                                    <el-tag v-if="s.row.anomaly === true || s.row.anomaly === 'true'" type="danger">异常</el-tag>
                                    <el-tag v-else type="success">正常</el-tag>
                                </template>
                            </el-table-column>
                        </el-table>
                    </el-dialog>
                    <!-- Spark状态弹窗 -->
                    <el-dialog v-model="sparkDialogVisible" title="Spark集群状态" width="480px">
                        <el-descriptions :column="2" border size="small">
                            <el-descriptions-item label="Spark版本">{{ sparkStatus.version }}</el-descriptions-item>
                            <el-descriptions-item label="运行模式">{{ sparkStatus.mode }}</el-descriptions-item>
                            <el-descriptions-item label="Master">{{ sparkStatus.master }}</el-descriptions-item>
                            <el-descriptions-item label="Streaming">{{ sparkStatus.streaming }}</el-descriptions-item>
                            <el-descriptions-item label="集群状态">
                                <el-tag type="success">{{ sparkStatus.status }}</el-tag>
                            </el-descriptions-item>
                            <el-descriptions-item label="启用状态">
                                <el-tag :type="sparkStatus.enabled ? 'success' : 'danger'">{{ sparkStatus.enabled ? '已启用' : '未启用' }}</el-tag>
                            </el-descriptions-item>
                        </el-descriptions>
                    </el-dialog>

                    <!-- 实时监控抽屉 -->
                    <el-drawer v-model="monitorVisible" title="📊 实时任务与系统监控" direction="rtl" size="80%">
                        <div style="padding:0 16px;">
                            <el-alert title="数据每3秒自动刷新,可点下方按钮立即刷新" type="info" :closable="false" style="margin-bottom:12px;"></el-alert>

                            <!-- 系统指标卡片 -->
                            <h3 style="margin-top:8px;">🖥️ 系统指标</h3>
                            <el-row :gutter="12">
                                <el-col :span="6">
                                    <el-card shadow="hover" :body-style="{padding:'14px'}">
                                        <div style="color:#999;font-size:12px;">JVM堆内存</div>
                                        <div style="font-size:20px;font-weight:bold;color:#00d4ff;">
                                            {{ monitorData.jvm?.heap?.used || 0 }} / {{ monitorData.jvm?.heap?.max || 0 }} MB
                                        </div>
                                        <el-progress :percentage="Math.round(monitorData.jvm?.heap?.usedPercent || 0)" :stroke-width="6" :show-text="false" :color="(monitorData.jvm?.heap?.usedPercent || 0) > 80 ? '#f56c6c' : '#67c23a'"></el-progress>
                                    </el-card>
                                </el-col>
                                <el-col :span="6">
                                    <el-card shadow="hover" :body-style="{padding:'14px'}">
                                        <div style="color:#999;font-size:12px;">进程 CPU</div>
                                        <div style="font-size:20px;font-weight:bold;color:#e6a23c;">{{ (monitorData.jvm?.cpu?.processCpuLoad || 0).toFixed(1) }}%</div>
                                        <el-progress :percentage="Math.min(100, Math.round(monitorData.jvm?.cpu?.processCpuLoad || 0))" :stroke-width="6" :show-text="false"></el-progress>
                                    </el-card>
                                </el-col>
                                <el-col :span="6">
                                    <el-card shadow="hover" :body-style="{padding:'14px'}">
                                        <div style="color:#999;font-size:12px;">系统 CPU</div>
                                        <div style="font-size:20px;font-weight:bold;color:#f56c6c;">{{ (monitorData.jvm?.cpu?.systemCpuLoad || 0).toFixed(1) }}%</div>
                                        <el-progress :percentage="Math.min(100, Math.round(monitorData.jvm?.cpu?.systemCpuLoad || 0))" :stroke-width="6" :show-text="false"></el-progress>
                                    </el-card>
                                </el-col>
                                <el-col :span="6">
                                    <el-card shadow="hover" :body-style="{padding:'14px'}">
                                        <div style="color:#999;font-size:12px;">运行时长</div>
                                        <div style="font-size:20px;font-weight:bold;color:#67c23a;">{{ monitorData.jvm?.runtime?.uptimeDesc || '-' }}</div>
                                        <div style="color:#999;font-size:11px;">线程: {{ monitorData.jvm?.threads || 0 }}</div>
                                    </el-card>
                                </el-col>
                            </el-row>

                            <!-- 任务统计 -->
                            <h3 style="margin-top:18px;">📈 任务统计</h3>
                            <el-row :gutter="12">
                                <el-col :span="6">
                                    <el-card shadow="hover" :body-style="{padding:'14px'}">
                                        <div style="color:#999;font-size:12px;">总任务数</div>
                                        <div style="font-size:22px;font-weight:bold;color:#00d4ff;">{{ monitorData.taskStats?.total || 0 }}</div>
                                    </el-card>
                                </el-col>
                                <el-col :span="6">
                                    <el-card shadow="hover" :body-style="{padding:'14px'}">
                                        <div style="color:#999;font-size:12px;">成功</div>
                                        <div style="font-size:22px;font-weight:bold;color:#67c23a;">{{ monitorData.taskStats?.success || 0 }}</div>
                                        <div style="color:#67c23a;font-size:11px;">成功率 {{ (monitorData.taskStats?.successRate || 0).toFixed(1) }}%</div>
                                    </el-card>
                                </el-col>
                                <el-col :span="6">
                                    <el-card shadow="hover" :body-style="{padding:'14px'}">
                                        <div style="color:#999;font-size:12px;">失败</div>
                                        <div style="font-size:22px;font-weight:bold;color:#f56c6c;">{{ monitorData.taskStats?.failed || 0 }}</div>
                                    </el-card>
                                </el-col>
                                <el-col :span="6">
                                    <el-card shadow="hover" :body-style="{padding:'14px'}">
                                        <div style="color:#999;font-size:12px;">正在运行</div>
                                        <div style="font-size:22px;font-weight:bold;color:#e6a23c;">{{ monitorData.taskStats?.running || 0 }}</div>
                                        <div style="color:#999;font-size:11px;">平均耗时 {{ monitorData.taskStats?.avgDuration || 0 }}ms</div>
                                    </el-card>
                                </el-col>
                            </el-row>

                            <!-- 数据同步状态 -->
                            <h3 style="margin-top:18px;">🔄 数据同步状态</h3>
                            <el-row :gutter="12">
                                <el-col :span="24">
                                    <el-card shadow="hover" :body-style="{padding:'14px'}">
                                        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:8px;">
                                            <span style="font-size:13px;color:#a0aec0;">已采集总量</span>
                                            <span style="font-size:18px;font-weight:bold;color:#00d4ff;">{{ monitorData.collectStats?.total || 0 }} 条</span>
                                        </div>
                                        <div ref="collectBar" style="height:140px;"></div>
                                    </el-card>
                                </el-col>
                            </el-row>

                            <!-- 正在运行任务 -->
                            <h3 style="margin-top:18px;">⚡ 正在运行的任务 ({{ monitorData.runningTasks?.length || 0 }})</h3>
                            <el-table :data="monitorData.runningTasks || []" stripe max-height="200" border>
                                <el-table-column prop="taskId" label="任务ID" width="140"></el-table-column>
                                <el-table-column prop="dataType" label="数据类型" width="100">
                                    <template #default="scope">
                                        <el-tag size="small" :type="dataTypeMap[scope.row.dataType]">{{ dataTypeLabels[scope.row.dataType] || scope.row.dataType }}</el-tag>
                                    </template>
                                </el-table-column>
                                <el-table-column prop="taskType" label="分析类型" width="100">
                                    <template #default="scope">
                                        <el-tag size="small">{{ taskTypeLabels[scope.row.taskType] || scope.row.taskType }}</el-tag>
                                    </template>
                                </el-table-column>
                                <el-table-column prop="dateStr" label="分析日期" width="110"></el-table-column>
                                <el-table-column prop="createTime" label="启动时间" width="160"></el-table-column>
                                <el-table-column label="已运行" width="100">
                                    <template #default="scope">{{ Math.round((scope.row.elapsed || 0) / 1000) }}秒</template>
                                </el-table-column>
                            </el-table>
                            <el-empty v-if="!monitorData.runningTasks || monitorData.runningTasks.length === 0" description="当前没有正在运行的任务" :image-size="80"></el-empty>

                            <div style="margin-top:16px;text-align:right;">
                                <el-button @click="loadMonitor">🔄 立即刷新</el-button>
                                <el-button type="primary" :loading="monitorLoading" @click="loadMonitor">关闭自动刷新</el-button>
                            </div>
                        </div>
                    </el-drawer>
                </div>
            </el-tab-pane>

            <el-tab-pane label="数据采集" name="collect">
                <el-card>
                    <h2 style="margin-top:0">⚙️ 数据采集管理</h2>
                    <el-alert title="系统支持手动采集 / 定时离线采集 / 实时流接入 三种模式" type="info" :closable="false" style="margin-bottom:16px;"></el-alert>

                    <el-tabs v-model="collectSubTab" type="card">
                        <!-- ============ 手动采集 ============ -->
                        <el-tab-pane label="🎯 手动采集" name="manual">
                            <el-form label-width="110px" style="max-width:900px;">
                                <el-form-item label="采集城市">
                                    <el-input v-model="manualForm.city" placeholder="例如:上海" style="max-width:300px;"></el-input>
                                </el-form-item>
                                <el-form-item label="模拟生成条数">
                                    <el-input-number v-model="manualForm.count" :min="1" :max="50000" :step="100" style="width:200px;"></el-input-number>
                                    <span style="margin-left:12px;color:#999">支持 1 ~ 50000 条</span>
                                </el-form-item>
                                <el-form-item label="数据类别">
                                    <el-select v-model="manualForm.dataType" style="width:200px;">
                                        <el-option v-for="dt in dataTypes" :key="dt.value" :label="dt.label" :value="dt.value"></el-option>
                                        <el-option label="全部五类" value="all"></el-option>
                                    </el-select>
                                </el-form-item>
                                <el-form-item>
                                    <el-button type="primary" @click="doManualCollect" :loading="collectAllLoading" :disabled="userRole!=='admin'">
                                        🚀 开始手动采集
                                    </el-button>
                                </el-form-item>
                            </el-form>
                        </el-tab-pane>

                        <!-- ============ 自动采集 ============ -->
                        <el-tab-pane label="🔄 自动(定时)采集" name="auto">
                            <el-alert title="通过设置间隔时间(毫秒)和单次条数,系统会按此节奏持续自动采集" type="success" :closable="false" style="margin-bottom:16px;"></el-alert>
                            <el-form label-width="160px" style="max-width:900px;">
                                <el-form-item label="采集城市">
                                    <el-input v-model="autoForm.city" placeholder="例如:上海" style="max-width:300px;"></el-input>
                                </el-form-item>
                                <el-form-item label="自动爬取间隔(毫秒)">
                                    <el-input-number v-model="autoForm.interval" :min="500" :max="86400000" :step="100" style="width:220px;"></el-input-number>
                                    <span style="margin-left:12px;color:#999">500ms ~ 24h(1000ms=1秒)</span>
                                </el-form-item>
                                <el-form-item label="单次生成条数">
                                    <el-input-number v-model="autoForm.count" :min="1" :max="5000" :step="50" style="width:200px;"></el-input-number>
                                    <span style="margin-left:12px;color:#999">每次定时触发生成 1~5000 条</span>
                                </el-form-item>
                                <el-form-item>
                                    <el-button type="success" @click="startAutoCollect" :disabled="autoCollecting || userRole!=='admin'">▶ 启动自动采集</el-button>
                                    <el-button type="danger" @click="stopAutoCollect" :disabled="!autoCollecting || userRole!=='admin'">⏸ 停止自动采集</el-button>
                                    <el-tag v-if="autoCollecting" type="success" style="margin-left:12px;">运行中 - 已采集 {{ autoCollectedCount }} 次 / 共 {{ autoCollectedTotal }} 条</el-tag>
                                </el-form-item>
                            </el-form>

                            <!-- ========== 定时任务配置（仅自动Tab内）========== -->
                            <el-divider content-position="left"><span style="font-size:15px;font-weight:bold;">⏰ 定时任务配置(修改"到点自动爬"时间)</span></el-divider>
                            <el-alert title="修改后即时保存,固定间隔需重启服务后生效,cron 修改后下一次触发时生效" type="warning" :closable="false" style="margin-bottom:12px;"></el-alert>
                            <el-card shadow="hover">
                                <el-row :gutter="16">
                                    <el-col :span="12">
                                        <h4>🔁 定时循环采集</h4>
                                        <el-form label-width="120px">
                                            <el-form-item label="当前间隔">
                                                <el-tag>{{ schedulerConfig.autoFixedRateDesc }}</el-tag>
                                                <span style="margin-left:8px;color:#999">({{ schedulerConfig.autoFixedRateMs }} 毫秒)</span>
                                            </el-form-item>
                                            <el-form-item label="修改为(毫秒)">
                                                <el-input-number v-model="schedulerForm.autoFixedRateMs" :min="500" :max="86400000" :step="1000" style="width:200px;"></el-input-number>
                                                <span style="margin-left:8px;color:#999">500ms~24h</span>
                                            </el-form-item>
                                            <el-form-item label="单次生成条数">
                                                <el-input-number v-model="schedulerForm.autoCount" :min="1" :max="5000" style="width:200px;"></el-input-number>
                                            </el-form-item>
                                        </el-form>
                                    </el-col>
                                    <el-col :span="12">
                                        <h4>📅 每日定点采集</h4>
                                        <el-form label-width="120px">
                                            <el-form-item label="当前时间">
                                                <el-tag>{{ schedulerConfig.dailyCronDesc }}</el-tag>
                                                <span style="margin-left:8px;color:#999">({{ schedulerConfig.dailyCron }})</span>
                                            </el-form-item>
                                            <el-form-item label="修改 cron">
                                                <el-input v-model="schedulerForm.dailyCron" placeholder="0 0 2 * * ?"></el-input>
                                                <div style="color:#999;font-size:12px;margin-top:4px;">
                                                    6位: 秒 分 时 日 月 周<br>
                                                    例: 0 0 2 * * ? = 每天2点
                                                </div>
                                            </el-form-item>
                                            <el-form-item label="单次生成条数">
                                                <el-input-number v-model="schedulerForm.dailyCount" :min="1" :max="5000" style="width:200px;"></el-input-number>
                                            </el-form-item>
                                        </el-form>
                                    </el-col>
                                </el-row>
                                <el-row style="margin-top:8px;">
                                    <el-button type="primary" @click="loadSchedulerConfig" style="margin-right:8px;">🔄 刷新配置</el-button>
                                    <el-button type="success" @click="saveSchedulerConfig" :disabled="userRole!=='admin'">💾 保存配置</el-button>
                                </el-row>
                            </el-card>
                        </el-tab-pane>

                        <!-- ============ 实时流接入 ============ -->
                        <el-tab-pane label="📡 实时流接入" name="stream">
                            <el-alert title="开启后,系统将持续以秒级节奏模拟注入实时城市数据流(模拟Spark Streaming接收)" type="warning" :closable="false" style="margin-bottom:16px;"></el-alert>
                            <el-form label-width="160px" style="max-width:900px;">
                                <el-form-item label="数据流类型">
                                    <el-select v-model="streamForm.dataType" style="width:200px;">
                                        <el-option label="🚦 交通流" value="traffic"></el-option>
                                        <el-option label="🌦️ 气象流" value="weather"></el-option>
                                        <el-option label="📣 舆情流" value="opinion"></el-option>
                                        <el-option label="💰 消费流" value="consumption"></el-option>
                                        <el-option label="👥 人口流" value="population"></el-option>
                                        <el-option label="🎲 混合流(随机)" value="mixed"></el-option>
                                    </el-select>
                                </el-form-item>
                                <el-form-item label="注入速度(条/秒)">
                                    <el-input-number v-model="streamForm.rate" :min="1" :max="1000" :step="1" style="width:200px;"></el-input-number>
                                    <span style="margin-left:12px;color:#999">每秒注入1~1000条数据</span>
                                </el-form-item>
                                <el-form-item label="单批条数">
                                    <el-input-number v-model="streamForm.batchSize" :min="1" :max="100" style="width:200px;"></el-input-number>
                                    <span style="margin-left:12px;color:#999">每批1~100条</span>
                                </el-form-item>
                                <el-form-item>
                                    <el-button type="warning" @click="startStream" :disabled="streaming || userRole!=='admin'">📡 启动实时流</el-button>
                                    <el-button type="info" @click="stopStream" :disabled="!streaming || userRole!=='admin'">⏹ 停止实时流</el-button>
                                    <el-tag v-if="streaming" type="warning" style="margin-left:12px;">运行中 - 已注入 {{ streamInjected }} 条</el-tag>
                                </el-form-item>
                            </el-form>
                        </el-tab-pane>
                    </el-tabs>

                    <!-- ========== 采集状态总览（全局可见）========== -->
                    <el-divider><span style="font-size:18px;font-weight:bold;">📊 采集状态总览</span></el-divider>
                    <el-row :gutter="16">
                        <el-col :span="4" v-for="(dt, idx) in dataTypes" :key="'cs_'+dt.value">
                            <el-card shadow="hover" :body-style="{padding:'14px'}" :style="collectCardStyle(idx)">
                                <div style="text-align:center;">
                                    <div :style="{fontSize:'32px',lineHeight:'40px',marginBottom:'6px'}">{{ collectIcons[idx] }}</div>
                                    <div :style="{fontSize:'14px',fontWeight:'bold',color:collectColors[idx],marginBottom:'8px'}">{{ dt.label }}数据</div>
                                    <div :style="{fontSize:'24px',fontWeight:'bold',color:'#303133',lineHeight:'32px'}">
                                        {{ collectStatus[dt.label] ? collectStatus[dt.label].count : 0 }}
                                    </div>
                                    <div style="font-size:12px;color:#909399;margin-top:2px;">已采集条数</div>
                                    <el-divider style="margin:10px 0;"></el-divider>
                                    <div style="font-size:12px;margin-bottom:4px;">
                                        状态:
                                        <el-tag size="small" :type="statusTagType(collectStatus[dt.label] ? collectStatus[dt.label].status : '未采集')">
                                            {{ collectStatus[dt.label] ? collectStatus[dt.label].status : '未采集' }}
                                        </el-tag>
                                    </div>
                                    <div style="font-size:11px;color:#909399;margin-top:4px;">
                                        🕐 {{ collectStatus[dt.label] && collectStatus[dt.label].time ? collectStatus[dt.label].time : '尚未采集' }}
                                    </div>
                                </div>
                            </el-card>
                        </el-col>
                    </el-row>

                </el-card>
            </el-tab-pane>

            <el-tab-pane label="挖掘分析" name="mining">
                <el-card>
                    <!-- ========== 步骤向导 ========== -->
                    <el-steps :active="miningStep" align-center finish-status="success" style="margin-bottom:24px;">
                        <el-step title="选择数据" description="选择要分析的数据类别" @click="onStepClick(0)" />
                        <el-step title="选择算法" description="选择分析算法并执行" @click="onStepClick(1)" />
                        <el-step title="查看结果" description="图表 + 关键指标 + AI建议" @click="onStepClick(2)" />
                    </el-steps>

                    <!-- Step 1: 选择数据类型 -->
                    <div v-show="miningStep === 0">
                        <el-alert title="选择要分析的数据类别，点击下一步选择算法" type="info" :closable="false" style="margin-bottom:12px;" />
                        <el-tabs v-model="activeMiningType" type="card" @tab-click="onMiningTypeChange">
                            <el-tab-pane v-for="dt in dataTypes" :key="'mt_'+dt.value" :label="dt.label" :name="dt.value"></el-tab-pane>
                        </el-tabs>
                        <div style="text-align:center;margin-top:16px;">
                            <el-tag size="large" :type="dataTypeMap[activeMiningType] || 'info'" style="font-size:15px;padding:8px 20px;">
                                {{ dataTypeLabels[activeMiningType] || activeMiningType }} 数据已选中
                            </el-tag>
                        </div>
                        <div style="text-align:center;margin-top:20px;">
                            <el-button type="primary" size="large" @click="miningStep = 1">下一步：选择算法 →</el-button>
                        </div>
                    </div>

                    <!-- Step 2: 选择算法并执行 -->
                    <div v-show="miningStep === 1">
                        <el-alert :title="'当前数据类型：' + (dataTypeLabels[activeMiningType] || activeMiningType) + '，点击任一算法开始分析'" type="info" :closable="false" style="margin-bottom:12px;" />
                        <el-row :gutter="16">
                            <el-col :span="4" v-for="tt in miningTaskOptions" :key="'tt_'+tt.value">
                                <el-card shadow="hover" :body-style="{padding:'20px',textAlign:'center',cursor:'pointer'}"
                                    :style="{borderColor: miningSelectedTask===tt.value ? '#409eff' : '', borderWidth: miningSelectedTask===tt.value ? '2px' : '1px'}"
                                    @click="runMiningWithConclusion(tt.value)">
                                    <div style="font-size:32px;margin-bottom:8px;">{{ tt.icon }}</div>
                                    <div style="font-size:14px;font-weight:bold;margin-bottom:4px;">{{ tt.label }}</div>
                                    <div style="font-size:11px;color:#999;">{{ tt.desc }}</div>
                                    <el-button v-if="miningSelectedTask===tt.value && miningLoading[activeMiningType]"
                                        type="primary" size="small" loading style="margin-top:10px;width:100%;">
                                        分析中...
                                    </el-button>
                                </el-card>
                            </el-col>
                        </el-row>
                        <div style="text-align:center;margin-top:16px;">
                            <el-button @click="miningStep = 0">← 返回选择</el-button>
                            <el-button type="danger" @click="executeAllMining" :loading="miningAllLoading"
                                v-if="userRole==='admin'" style="margin-left:12px;">
                                🚀 一键执行全部5种算法
                            </el-button>
                        </div>
                    </div>

                    <!-- Step 3: 查看结果 -->
                    <div v-show="miningStep === 2">
                        <el-alert :title="dataTypeLabels[activeMiningType] + ' · ' + (taskTypeLabels[miningSelectedTask] || miningSelectedTask) + ' 分析完成'" type="success" :closable="false" style="margin-bottom:16px;" />

                        <!-- 关键指标 行 -->
                        <el-row :gutter="12" style="margin-bottom:16px;">
                            <el-col :span="6">
                                <el-card shadow="hover" :body-style="{padding:'14px',textAlign:'center'}">
                                    <div style="color:#999;font-size:12px;">数据量</div>
                                    <div style="font-size:28px;font-weight:bold;color:#409eff;">{{ miningSummary.total }}</div>
                                </el-card>
                            </el-col>
                            <el-col :span="6">
                                <el-card shadow="hover" :body-style="{padding:'14px',textAlign:'center'}">
                                    <div style="color:#999;font-size:12px;">算法</div>
                                    <div style="font-size:18px;font-weight:bold;color:#67c23a;">{{ miningSummary.algorithm }}</div>
                                </el-card>
                            </el-col>
                            <el-col :span="6">
                                <el-card shadow="hover" :body-style="{padding:'14px',textAlign:'center'}">
                                    <div style="color:#999;font-size:12px;">分组/规则数</div>
                                    <div style="font-size:28px;font-weight:bold;color:#e6a23c;">{{ miningSummary.groups }}</div>
                                </el-card>
                            </el-col>
                            <el-col :span="6">
                                <el-card shadow="hover" :body-style="{padding:'14px',textAlign:'center'}">
                                    <div style="color:#999;font-size:12px;">分析耗时</div>
                                    <div style="font-size:20px;font-weight:bold;">{{ miningSummary.duration }}ms</div>
                                </el-card>
                            </el-col>
                        </el-row>

                        <!-- 图表 + AI建议 两栏 -->
                        <el-row :gutter="16">
                            <!-- 左: 图表（占更大面积） -->
                            <el-col :span="14">
                                <el-card shadow="hover" :body-style="{padding:'8px',height:'420px'}">
                                    <template #header>
                                        <span style="font-weight:bold;">{{ dataTypeLabels[activeMiningType] }} - {{ taskTypeLabels[miningSelectedTask] || miningSelectedTask }} 图表</span>
                                    </template>
                                    <div v-if="miningChartData.length" id="miningChart" style="height:370px;"></div>
                                    <div v-else style="color:#999;text-align:center;padding:160px 0;">
                                        暂无图表数据<br/>请返回上一步选择算法执行
                                    </div>
                                </el-card>
                            </el-col>
                            <!-- 右: AI建议 -->
                            <el-col :span="10">
                                <el-card shadow="hover" :body-style="{padding:'16px',height:'420px',overflowY:'auto'}">
                                    <template #header>
                                        <span style="font-weight:bold;">AI业务建议 (DeepSeek)</span>
                                    </template>
                                    <div v-if="aiConclusionLoading" style="text-align:center;padding:60px 0;">
                                        <el-icon class="is-loading" size="32"><Loading /></el-icon>
                                        <div style="margin-top:12px;color:#999;">DeepSeek 大模型分析中...</div>
                                    </div>
                                    <div v-else-if="aiConclusion" style="white-space:pre-line;font-size:13px;line-height:1.9;color:#67c23a;">
                                        {{ aiConclusion }}
                                    </div>
                                    <div v-else style="color:#999;text-align:center;padding:60px 0;">
                                        执行分析后自动生成
                                    </div>
                                </el-card>
                            </el-col>
                        </el-row>

                        <div style="text-align:center;margin-top:16px;">
                            <el-button @click="miningStep = 1">← 换算法重新分析</el-button>
                            <el-button type="primary" @click="miningStep = 0">← 换数据类型</el-button>
                        </div>
                    </div>

                    <!-- 分析结果表格 -->
                    <el-divider>📋 历史分析记录</el-divider>
                    <el-table :data="miningResults" stripe max-height="300">
                        <el-table-column prop="taskId" label="任务ID" width="120"></el-table-column>
                        <el-table-column prop="dataType" label="数据类型" width="100">
                            <template #default="scope">
                                <el-tag :type="dataTypeMap[scope.row.dataType]">
                                    {{ dataTypeLabels[scope.row.dataType] }}
                                </el-tag>
                            </template>
                        </el-table-column>
                        <el-table-column prop="taskType" label="分析类型" width="100"></el-table-column>
                        <el-table-column prop="status" label="状态" width="80">
                            <template #default="scope">
                                <el-tag :type="scope.row.status==='success'?'success':'danger'">
                                    {{ scope.row.status === 'success' ? '成功' : '失败' }}
                                </el-tag>
                            </template>
                        </el-table-column>
                        <el-table-column prop="createTime" label="时间" width="180"></el-table-column>
                        <el-table-column prop="duration" label="耗时(ms)" width="100"></el-table-column>
                    </el-table>

                    <!-- 高级工具 (折叠) -->
                    <el-divider>🛠 高级工具</el-divider>
                    <el-collapse v-model="advancedCollapse" style="border:none;">
                        <!-- MapReduce -->
                        <el-collapse-item title="🗺️ MapReduce 离线聚合 (日/月/年/小时/区域维度)" name="mr">
                            <el-form :inline="true">
                                <el-form-item label="数据类型">
                                    <el-select v-model="mrForm.dataType" style="width:140px;">
                                        <el-option v-for="dt in dataTypes" :key="dt.value" :label="dt.label" :value="dt.value"></el-option>
                                    </el-select>
                                </el-form-item>
                                <el-form-item label="维度">
                                    <el-radio-group v-model="mrForm.dimension">
                                        <el-radio-button label="day">日</el-radio-button>
                                        <el-radio-button label="month">月</el-radio-button>
                                        <el-radio-button label="year">年</el-radio-button>
                                        <el-radio-button label="hour">小时</el-radio-button>
                                        <el-radio-button label="district">区域</el-radio-button>
                                    </el-radio-group>
                                </el-form-item>
                                <el-form-item label="日期">
                                    <el-date-picker v-model="mrForm.dateStr" type="date" placeholder="不填则用全部"
                                        format="YYYY-MM-DD" value-format="YYYY-MM-DD" size="default" :disabled-date="disabledDate" style="width:170px;" />
                                </el-form-item>
                                <el-form-item>
                                    <el-button type="primary" @click="runMapReduce" :loading="mrLoading" :disabled="userRole!=='admin'">🚀 执行MapReduce</el-button>
                                </el-form-item>
                            </el-form>
                            <div v-if="mrResult">
                                <el-row :gutter="12" style="margin-bottom:12px;">
                                    <el-col :span="6"><el-card :body-style="{padding:'12px'}"><div style="color:#999;font-size:12px;">算法</div><div style="font-size:16px;font-weight:bold;">{{ mrResult.algorithm }}</div></el-card></el-col>
                                    <el-col :span="6"><el-card :body-style="{padding:'12px'}"><div style="color:#999;font-size:12px;">维度</div><div style="font-size:16px;font-weight:bold;">{{ mrResult.dimension }}</div></el-card></el-col>
                                    <el-col :span="6"><el-card :body-style="{padding:'12px'}"><div style="color:#999;font-size:12px;">分组数</div><div style="font-size:16px;font-weight:bold;">{{ mrResult.totalGroups }}</div></el-card></el-col>
                                    <el-col :span="6"><el-card :body-style="{padding:'12px'}"><div style="color:#999;font-size:12px;">记录数</div><div style="font-size:16px;font-weight:bold;">{{ mrResult.totalRecords }}</div></el-card></el-col>
                                </el-row>
                                <el-table :data="mrResult.data || []" stripe max-height="300" border>
                                    <el-table-column prop="key" label="分组键" width="220"></el-table-column>
                                    <el-table-column :label="'sum_' + (mrResult.metric || 'val')">
                                        <template #default="scope">{{ scope.row['sum_' + (mrResult.metric || 'val')] }}</template>
                                    </el-table-column>
                                    <el-table-column :label="'avg_' + (mrResult.metric || 'val')">
                                        <template #default="scope">{{ scope.row['avg_' + (mrResult.metric || 'val')] }}</template>
                                    </el-table-column>
                                    <el-table-column prop="count" label="记录数" width="100"></el-table-column>
                                </el-table>
                            </div>
                        </el-collapse-item>

                        <!-- Spark Streaming -->
                        <el-collapse-item title="📡 Spark Streaming 实时流输出 (秒级入库)" name="streaming">
                            <el-row :gutter="12" style="margin-bottom:12px;">
                                <el-col :span="12">
                                    <el-form :inline="true">
                                        <el-form-item label="数据类型">
                                            <el-select v-model="streamIngestForm.dataType" style="width:130px;">
                                                <el-option v-for="dt in dataTypes" :key="'si_'+dt.value" :label="dt.label" :value="dt.value"></el-option>
                                            </el-select>
                                        </el-form-item>
                                        <el-form-item label="区域">
                                            <el-input v-model="streamIngestForm.district" style="width:100px;"></el-input>
                                        </el-form-item>
                                        <el-form-item>
                                            <el-button type="success" @click="pushStream" :loading="streamPushing" :disabled="userRole!=='admin'">📤 推送1条</el-button>
                                            <el-button type="primary" @click="startStreamAuto" :disabled="streamAuto || userRole!=='admin'">🔁 开启持续注入</el-button>
                                            <el-button type="danger" @click="stopStreamAuto" :disabled="!streamAuto || userRole!=='admin'">⏸ 停止</el-button>
                                        </el-form-item>
                                    </el-form>
                                </el-col>
                                <el-col :span="12" style="text-align:right;">
                                    <span style="color:#999;font-size:13px;">已注入 {{ streamIngestedCount }} 条 · 最近 {{ streamRecentCount }} 条</span>
                                </el-col>
                            </el-row>
                            <el-table :data="streamRecent" stripe max-height="280" border>
                                <el-table-column prop="dataType" label="类型" width="80">
                                    <template #default="scope"><el-tag size="small" :type="dataTypeMap[scope.row.dataType]">{{ dataTypeLabels[scope.row.dataType] }}</el-tag></template>
                                </el-table-column>
                                <el-table-column prop="district" label="区域" width="90"></el-table-column>
                                <el-table-column label="计算结果" width="220">
                                    <template #default="scope"><span style="font-size:12px;color:#9cdcfe;">{{ JSON.stringify(scope.row.computed) }}</span></template>
                                </el-table-column>
                                <el-table-column label="告警" width="100">
                                    <template #default="scope"><el-tag size="small" :type="scope.row.alert && (scope.row.alert.includes('高') || scope.row.alert.includes('重') || scope.row.alert.includes('热点')) ? 'danger' : 'success'">{{ scope.row.alert }}</el-tag></template>
                                </el-table-column>
                                <el-table-column prop="ingestTime" label="接入时间" width="180"></el-table-column>
                                <el-table-column prop="cost" label="耗时" width="70"><template #default="scope">{{ scope.row.cost }}ms</template></el-table-column>
                            </el-table>
                        </el-collapse-item>

                        <!-- HDFS -->
                        <el-collapse-item title="📦 HDFS 分布式归档" name="hdfs">
                            <div style="display:flex;gap:8px;flex-wrap:wrap;margin-bottom:12px;">
                                <el-tag :type="hdfsStatus.online ? 'success' : 'danger'" size="small">
                                    {{ hdfsStatus.distributedOnline ? 'HDFS 在线' : (hdfsStatus.online ? 'HDFS降级(本地)' : 'HDFS 离线') }}
                                </el-tag>
                                <span style="color:#a0aec0;font-size:13px;">{{ hdfsStatus.hadoopHome }} · 归档文件: {{ hdfsStatus.fileCount || 0 }} 个</span>
                                <el-button size="small" @click="loadHdfsStatus">🔄 刷新状态</el-button>
                                <el-button size="small" type="primary" @click="archiveResultsToHdfs" :loading="hdfsLoading" :disabled="userRole!=='admin'">📤 归档挖掘结果</el-button>
                                <el-button size="small" type="success" @click="archiveDataToHdfs" :loading="hdfsLoading" :disabled="userRole!=='admin'">📤 归档原始数据</el-button>
                            </div>
                            <el-table :data="hdfsFiles" stripe max-height="250" empty-text="暂无归档文件,点击上方按钮归档">
                                <el-table-column prop="name" label="文件名" min-width="300"></el-table-column>
                                <el-table-column prop="size" label="大小(字节)" width="120"></el-table-column>
                                <el-table-column prop="date" label="日期" width="160"></el-table-column>
                                <el-table-column label="操作" width="100">
                                    <template #default="scope">
                                        <el-button size="small" type="warning" @click="restoreFromHdfs(scope.row)" :disabled="userRole!=='admin'">🔄 恢复</el-button>
                                    </template>
                                </el-table-column>
                            </el-table>
                        </el-collapse-item>
                    </el-collapse>
                </el-card>
            </el-tab-pane>

            <el-tab-pane label="历史回溯" name="history">
                <el-card>
                    <h2 style="margin-top:0">📜 历史数据查询与挖掘结果回溯</h2>
                    <el-alert title="支持按日 / 月 / 年维度切换查询历史挖掘结果,支持导出" type="info" :closable="false" style="margin-bottom:16px;"></el-alert>

                    <el-form :inline="true" label-width="80px">
                        <el-form-item label="时间维度">
                            <el-radio-group v-model="historyForm.dimension" @change="onDimensionChange">
                                <el-radio-button label="day">按日</el-radio-button>
                                <el-radio-button label="month">按月</el-radio-button>
                                <el-radio-button label="year">按年</el-radio-button>
                            </el-radio-group>
                        </el-form-item>
                        <el-form-item label="时间值">
                            <el-date-picker
                                v-if="historyForm.dimension==='day'"
                                v-model="historyForm.dateValue"
                                type="date"
                                placeholder="选择日期"
                                format="YYYY-MM-DD"
                                value-format="YYYY-MM-DD"
                                :disabled-date="disabledDate"
                                style="width:170px;">
                            </el-date-picker>
                            <el-date-picker
                                v-else-if="historyForm.dimension==='month'"
                                v-model="historyForm.dateValue"
                                type="month"
                                placeholder="选择月份"
                                format="YYYY-MM"
                                value-format="YYYY-MM"
                                :disabled-date="disabledMonth"
                                style="width:170px;">
                            </el-date-picker>
                            <el-date-picker
                                v-else
                                v-model="historyForm.dateValue"
                                type="year"
                                placeholder="选择年份"
                                format="YYYY"
                                value-format="YYYY"
                                :disabled-date="disabledYear"
                                style="width:170px;">
                            </el-date-picker>
                        </el-form-item>
                        <el-form-item label="数据类型">
                            <el-select v-model="historyForm.dataType" clearable placeholder="全部" style="width:140px;">
                                <el-option v-for="dt in dataTypes" :key="dt.value" :label="dt.label" :value="dt.value"></el-option>
                            </el-select>
                        </el-form-item>
                        <el-form-item label="分析类型">
                            <el-select v-model="historyForm.taskType" clearable placeholder="全部" style="width:140px;">
                                <el-option label="统计分析" value="statistic"></el-option>
                                <el-option label="聚类分析" value="cluster"></el-option>
                                <el-option label="关联规则" value="association"></el-option>
                                <el-option label="预测分析" value="predict"></el-option>
                                <el-option label="异常检测" value="anomaly"></el-option>
                            </el-select>
                        </el-form-item>
                        <el-form-item>
                            <el-button type="primary" @click="queryHistory" :loading="historyLoading">🔍 查询</el-button>
                            <el-button type="success" @click="exportHistory" :disabled="!historyResults.length">📤 导出CSV</el-button>
                        </el-form-item>
                    </el-form>

                    <el-divider>📊 查询结果 ({{ historyResults.length }} 条)</el-divider>

                    <!-- 统计小卡片 -->
                    <el-row :gutter="12" style="margin-bottom:12px;">
                        <el-col :span="6">
                            <el-card shadow="hover" :body-style="{padding:'14px'}">
                                <div style="color:#999;font-size:12px;">查询维度</div>
                                <div style="font-size:18px;font-weight:bold;color:#00d4ff;">{{ historyMeta.dimension || '-' }}</div>
                            </el-card>
                        </el-col>
                        <el-col :span="6">
                            <el-card shadow="hover" :body-style="{padding:'14px'}">
                                <div style="color:#999;font-size:12px;">查询值</div>
                                <div style="font-size:18px;font-weight:bold;color:#67c23a;">{{ historyMeta.value || '-' }}</div>
                            </el-card>
                        </el-col>
                        <el-col :span="6">
                            <el-card shadow="hover" :body-style="{padding:'14px'}">
                                <div style="color:#999;font-size:12px;">成功任务</div>
                                <div style="font-size:18px;font-weight:bold;color:#e6a23c;">{{ historySummary.success }}</div>
                            </el-card>
                        </el-col>
                        <el-col :span="6">
                            <el-card shadow="hover" :body-style="{padding:'14px'}">
                                <div style="color:#999;font-size:12px;">总耗时</div>
                                <div style="font-size:18px;font-weight:bold;color:#f56c6c;">{{ historySummary.totalDuration }}ms</div>
                            </el-card>
                        </el-col>
                    </el-row>

                    <el-table :data="historyResults" stripe max-height="500" border>
                        <el-table-column prop="taskId" label="任务ID" width="120"></el-table-column>
                        <el-table-column prop="dataType" label="数据类型" width="100">
                            <template #default="scope">
                                <el-tag :type="dataTypeMap[scope.row.dataType]">
                                    {{ dataTypeLabels[scope.row.dataType] || scope.row.dataType }}
                                </el-tag>
                            </template>
                        </el-table-column>
                        <el-table-column prop="taskType" label="分析类型" width="100">
                            <template #default="scope">
                                <el-tag size="small">{{ taskTypeLabels[scope.row.taskType] || scope.row.taskType }}</el-tag>
                            </template>
                        </el-table-column>
                        <el-table-column prop="dateStr" label="分析日期" width="110"></el-table-column>
                        <el-table-column prop="status" label="状态" width="80">
                            <template #default="scope">
                                <el-tag :type="scope.row.status==='success'?'success':'danger'">
                                    {{ scope.row.status === 'success' ? '成功' : '失败' }}
                                </el-tag>
                            </template>
                        </el-table-column>
                        <el-table-column prop="duration" label="耗时" width="80">
                            <template #default="scope">{{ scope.row.duration }}ms</template>
                        </el-table-column>
                        <el-table-column prop="createTime" label="创建时间" width="160"></el-table-column>
                        <el-table-column label="操作" width="120">
                            <template #default="scope">
                                <el-button size="small" @click="showHistoryDetail(scope.row)">查看详情</el-button>
                            </template>
                        </el-table-column>
                    </el-table>

                    <!-- 详情弹窗 -->
                    <el-dialog v-model="historyDetailVisible" title="历史挖掘结果详情" width="700px">
                        <el-descriptions :column="2" border size="small" v-if="currentHistoryResult">
                            <el-descriptions-item label="任务ID">{{ currentHistoryResult.taskId }}</el-descriptions-item>
                            <el-descriptions-item label="数据类型">{{ dataTypeLabels[currentHistoryResult.dataType] }}</el-descriptions-item>
                            <el-descriptions-item label="分析类型">{{ taskTypeLabels[currentHistoryResult.taskType] || currentHistoryResult.taskType }}</el-descriptions-item>
                            <el-descriptions-item label="状态">
                                <el-tag :type="currentHistoryResult.status==='success'?'success':'danger'">
                                    {{ currentHistoryResult.status === 'success' ? '成功' : '失败' }}
                                </el-tag>
                            </el-descriptions-item>
                            <el-descriptions-item label="分析日期">{{ currentHistoryResult.dateStr }}</el-descriptions-item>
                            <el-descriptions-item label="耗时">{{ currentHistoryResult.duration }}ms</el-descriptions-item>
                            <el-descriptions-item label="创建时间" :span="2">{{ currentHistoryResult.createTime }}</el-descriptions-item>
                        </el-descriptions>
                        <el-divider>结果数据 (JSON)</el-divider>
                        <pre style="max-height:300px;overflow:auto;background:#1e1e1e;color:#9cdcfe;padding:12px;border-radius:4px;font-size:12px;">{{ JSON.stringify(currentHistoryResult ? currentHistoryResult.result : {}, null, 2) }}</pre>
                    </el-dialog>
                </el-card>
            </el-tab-pane>

            <!-- 用户管理(仅管理员) -->
            <el-tab-pane v-if="userRole === 'admin'" label="用户管理" name="admin">
                <el-row :gutter="16" style="margin-bottom:16px">
                    <el-col :span="4">
                        <el-card shadow="hover">
                            <div class="stat-value" style="color:#4facfe">{{ users.length }}</div>
                            <div class="stat-label">总用户数</div>
                        </el-card>
                    </el-col>
                    <el-col :span="4">
                        <el-card shadow="hover">
                            <div class="stat-value" style="color:#00f2fe">{{ users.filter(u => u.role === 'admin').length }}</div>
                            <div class="stat-label">管理员数</div>
                        </el-card>
                    </el-col>
                    <el-col :span="4">
                        <el-card shadow="hover">
                            <div class="stat-value" style="color:#43e97b">{{ users.filter(u => u.role === 'user').length }}</div>
                            <div class="stat-label">普通用户数</div>
                        </el-card>
                    </el-col>
                </el-row>

                <el-card header="账号管理">
                    <div style="margin-bottom:12px;display:flex;gap:8px;flex-wrap:wrap;">
                        <el-button type="primary" @click="openAddUser">➕ 新增用户</el-button>
                        <el-button @click="loadUsers">🔄 刷新列表</el-button>
                    </div>
                    <el-table :data="users" stripe border>
                        <el-table-column prop="id" label="ID" width="60"></el-table-column>
                        <el-table-column prop="username" label="用户名" width="140"></el-table-column>
                        <el-table-column prop="role" label="角色" width="100">
                            <template #default="scope">
                                <el-tag v-if="scope.row.role === 'admin'" type="danger" size="small">管理员</el-tag>
                                <el-tag v-else type="info" size="small">普通用户</el-tag>
                            </template>
                        </el-table-column>
                        <el-table-column prop="createTime" label="创建时间" width="180"></el-table-column>
                        <el-table-column prop="lastLoginTime" label="最后登录" width="180"></el-table-column>
                        <el-table-column label="操作" width="320">
                            <template #default="scope">
                                <el-button v-if="scope.row.username !== 'admin'" type="primary" size="small" @click="openChangePassword(scope.row)">🔑 改密</el-button>
                                <el-button v-if="scope.row.username !== 'admin'" type="warning" size="small" @click="changeRole(scope.row)">
                                    {{ scope.row.role === 'admin' ? '降为用户' : '升为管理员' }}
                                </el-button>
                                <el-button v-if="scope.row.username !== 'admin'" type="danger" size="small" @click="deleteUser(scope.row)">🗑️ 删除</el-button>
                                <el-tag v-else type="success" size="small">系统账号(不可操作)</el-tag>
                            </template>
                        </el-table-column>
                    </el-table>
                </el-card>

                <el-card header="操作日志" style="margin-top:16px">
                    <el-table :data="logs" stripe max-height="300">
                        <el-table-column prop="username" label="用户" width="100"></el-table-column>
                        <el-table-column prop="operation" label="操作" width="120"></el-table-column>
                        <el-table-column prop="target" label="目标" width="200"></el-table-column>
                        <el-table-column prop="status" label="状态" width="80"></el-table-column>
                        <el-table-column prop="createTime" label="时间" width="180"></el-table-column>
                    </el-table>
                </el-card>
            </el-tab-pane>
        </el-tabs>
    </div>

    <!-- 新增用户弹窗 -->
    <el-dialog v-model="addUserVisible" title="➕ 新增用户" width="420px">
        <el-form :model="newUser" label-width="80px">
            <el-form-item label="用户名">
                <el-input v-model="newUser.username" placeholder="请输入用户名"></el-input>
            </el-form-item>
            <el-form-item label="密码">
                <el-input v-model="newUser.password" type="password" show-password placeholder="请输入密码"></el-input>
            </el-form-item>
            <el-form-item label="角色">
                <el-select v-model="newUser.role" style="width:100%">
                    <el-option label="普通用户" value="user"></el-option>
                    <el-option label="管理员" value="admin"></el-option>
                </el-select>
            </el-form-item>
        </el-form>
        <template #footer>
            <el-button @click="addUserVisible = false">取消</el-button>
            <el-button type="primary" @click="doAddUser">确定</el-button>
        </template>
    </el-dialog>

    <!-- 修改密码弹窗 -->
    <el-dialog v-model="pwdVisible" title="🔑 修改密码" width="380px">
        <el-form :model="pwdForm" label-width="100px">
            <el-form-item label="目标用户">
                <el-input v-model="pwdForm.username" disabled></el-input>
            </el-form-item>
            <el-form-item label="新密码">
                <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="至少4位"></el-input>
            </el-form-item>
        </el-form>
        <template #footer>
            <el-button @click="pwdVisible = false">取消</el-button>
            <el-button type="primary" @click="doChangePassword">确定修改</el-button>
        </template>
    </el-dialog>
    </div><!-- vueApp end -->
</div><!-- app end -->

<!-- 原生登录脚本(独立于Vue) -->
<script>
(function(){
    function showNativeLogin() {
        var c = document.getElementById('login-container');
        var v = document.getElementById('vueApp');
        if (c) c.style.display = 'flex';
        if (v) v.style.display = 'none';
    }
    function hideNativeLogin() {
        var c = document.getElementById('login-container');
        if (c) c.style.display = 'none';
    }
    function doNativeLogin(u, p, btn) {
        btn.disabled = true; btn.textContent = '登录中...';
        return fetch('/api/auth/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username: u, password: p })
        }).then(function(r){ return r.json(); }).then(function(j){
            if (j.code === 200 && j.data && j.data.token) {
                localStorage.setItem('token', j.data.token);
                localStorage.setItem('username', j.data.username || u);
                localStorage.setItem('role', j.data.role || 'user');
                location.reload();
            } else {
                alert('登录失败: ' + (j.message || '未知错误'));
                btn.disabled = false; btn.textContent = '登 录';
            }
        }).catch(function(e){
            alert('登录请求失败: ' + e);
            btn.disabled = false; btn.textContent = '登 录';
        });
    }
    // 等Vue挂载后由Vue控制; 若1秒后Vue未渲染出登录框, 则回退到原生登录
    window.addEventListener('load', function(){
        setTimeout(function(){
            // 如果Vue已登录(token有效)就不显示
            if (localStorage.getItem('token')) return;
            // 检查Vue的#app是否已被Vue接管并显示出内容
            var vapp = document.getElementById('vueApp');
            // Vue未显示任何东西(显示空),则用原生登录
            showNativeLogin();
        }, 500);
    });
    // 提交原生登录
    document.addEventListener('DOMContentLoaded', function(){
        var form = document.getElementById('nativeLoginForm');
        if (form) {
            form.addEventListener('submit', function(e){
                e.preventDefault();
                var u = document.getElementById('nativeUsername').value.trim();
                var p = document.getElementById('nativePassword').value;
                if (!u || !p) return;
                var btn = document.getElementById('nativeLoginBtn');
                doNativeLogin(u, p, btn);
            });
        }
    });
})();
</script>

<script src="js/api.js"></script>
<script src="js/app.js"></script>
</body>
</html>
