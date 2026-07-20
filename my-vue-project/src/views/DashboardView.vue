<template>
  <div id="main-container">
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
      🔒 您当前以 <b>普通用户</b> 身份登录，数据采集/挖掘分析/实时流/定时配置等操作需管理员权限
    </div>

    <!-- Tab导航 -->
    <el-tabs v-model="activeTab" type="border-card" @tab-change="onTabChange">
      <!-- ==================== 数据大屏 ==================== -->
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
          <!-- 紧急事件滚动条(新增) -->
          <div class="urgent-alert-bar" v-if="urgentAlerts.length">
            <span class="alert-icon">🚨</span>
            <span class="alert-label">最紧急：</span>
            <div class="alert-scroll">
              <el-carousel height="32px" direction="vertical" :autoplay="true" :interval="3500" indicator-position="none" arrow="never">
                <el-carousel-item v-for="(a, i) in urgentAlerts" :key="i">
                  <span :class="['alert-level-tag', a.level.toLowerCase()]">{{ a.icon }} {{ a.title }}</span>
                  <span style="color:#fff;margin-left:10px;font-size:13px;">{{ a.content }}</span>
                </el-carousel-item>
              </el-carousel>
            </div>
            <el-button size="small" text @click="loadUrgentAlerts" style="color:#fff;margin-left:auto;">🔄 刷新</el-button>
          </div>
          <!-- 工具栏 -->
          <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:16px;flex-wrap:wrap;gap:10px;">
            <div style="display:flex;align-items:center;gap:10px;">
              <el-button type="primary" size="small" @click="openMonitor">📊 实时监控</el-button>
              <span style="color:#a0aec0;font-size:13px;">分析日期</span>
              <el-date-picker v-model="selectedDate" type="date" placeholder="选择日期" format="YYYY-MM-DD" value-format="YYYY-MM-DD" size="small" :disabled-date="disabledDate" @change="onDateChange" style="width:170px;" />
              <el-button size="small" @click="toggleRefresh" :type="refreshEnabled ? 'success' : 'default'">{{ refreshEnabled ? '自动刷新中(30s)' : '开启自动刷新' }}</el-button>
              <span v-if="dashboardStatus" :style="{color:dashboardStatus.color,fontSize:'12px',marginLeft:'8px'}">{{ dashboardStatus.icon }} {{ dashboardStatus.text }}</span>
            </div>
            <div style="display:flex;gap:8px;">
              <el-button type="warning" size="small" @click="showSparkStatus">Spark集群状态</el-button>
              <el-button type="success" size="small" @click="exportDashboard">导出大屏数据</el-button>
            </div>
          </div>
          <!-- 图表行 1：城市总览 4 个图 -->
          <el-row :gutter="16" style="margin-top:16px">
            <el-col :span="12"><el-card header="交通拥堵等级分布"><div id="chart-congestion" style="height:320px"></div></el-card></el-col>
            <el-col :span="12"><el-card header="事故风险分类（按高/中/低）"><div id="chart-accident" style="height:320px"></div></el-card></el-col>
          </el-row>
          <el-row :gutter="16" style="margin-top:16px">
            <el-col :span="12"><el-card header="24h 交通流量趋势（车流量+拥堵指数）"><div id="chart-traffic-flow" style="height:320px;" @click="onChartDrill('trafficFlow')"></div></el-card></el-col>
            <el-col :span="12"><el-card header="24小时消费交易笔数趋势"><div id="chart-consumption" style="height:320px"></div></el-card></el-col>
          </el-row>
          <!-- 图表行 2：规律 + 预测 4 个图 -->
          <el-row :gutter="16" style="margin-top:16px">
            <el-col :span="12"><el-card header="🔮 未来24h 流量预测"><div id="chart-next24h-flow" style="height:320px"></div></el-card></el-col>
            <el-col :span="12"><el-card header="🌡️ 未来24h 温度预测"><div id="chart-next24h-temp" style="height:320px"></div></el-card></el-col>
          </el-row>
          <el-row :gutter="16" style="margin-top:16px">
            <el-col :span="12"><el-card header="舆情情感仪表盘（24h 趋势+构成+当前）"><div id="chart-opinion" style="height:320px"></div></el-card></el-col>
            <el-col :span="12"><el-card header="区域拥堵热力图（最近7天×24h 车流热力）"><div id="chart-heatmap" style="height:320px"></div></el-card></el-col>
          </el-row>
          <!-- 图表行 3：异常识别 4 个图 -->
          <el-row :gutter="16" style="margin-top:16px">
            <el-col :span="12"><el-card header="交通异常检测（区域事故数+均值线）"><div id="chart-traffic-anomaly" style="height:320px;" @click="onChartDrill('trafficAnomaly')"></div></el-card></el-col>
            <el-col :span="12"><el-card header="舆情热点异常识别（热度+负面率 双柱）"><div id="chart-opinion-anomaly" style="height:320px;" @click="onChartDrill('opinionAnomaly')"></div></el-card></el-col>
          </el-row>
          <!-- 图表行 4：人口商圈 + 出行方式 2 个简单图（静态昨日数据） -->
          <el-row :gutter="16" style="margin-top:16px">
            <el-col :span="12"><el-card header="各区人口商圈占比"><div id="chart-population-pie" style="height:300px"></div></el-card></el-col>
            <el-col :span="12"><el-card header="出行方式分布"><div id="chart-travel-mode" style="height:300px"></div></el-card></el-col>
          </el-row>
          <!-- 生活化预测建议(新增) -->
          <el-row :gutter="16" style="margin-top:16px">
            <el-col :span="24">
              <el-card shadow="hover" header="🌟 生活贴士(对市民有用的预测建议)">
                <div v-if="lifeAdviceLoading" style="text-align:center;padding:40px;">
                  <el-icon class="is-loading" size="24"><Loading /></el-icon>
                  <span style="margin-left:8px;color:#999;">正在生成生活化建议...</span>
                </div>
                <div v-else style="white-space:pre-line;font-size:13px;line-height:1.9;color:#409eff;background:#f0f9ff;padding:16px;border-radius:4px;border-left:4px solid #409eff;">{{ lifeAdvice }}</div>
              </el-card>
            </el-col>
          </el-row>
        </div>
      </el-tab-pane>

      <!-- ==================== 数据采集 ==================== -->
      <el-tab-pane label="数据采集" name="collect">
        <el-card>
          <h2 style="margin-top:0">⚙️ 数据采集管理</h2>
          <el-alert title="系统支持手动采集 / 定时离线采集 / 实时流接入 三种模式" type="info" :closable="false" style="margin-bottom:16px;" />
          <el-tabs v-model="collectSubTab" type="card">
            <el-tab-pane label="🎯 手动采集" name="manual">
              <el-form label-width="110px" style="max-width:900px;">
                <el-form-item label="采集城市"><el-input v-model="manualForm.city" placeholder="例如:上海" style="max-width:300px;" /></el-form-item>
                <el-form-item label="模拟生成条数"><el-input-number v-model="manualForm.count" :min="1" :max="50000" :step="100" style="width:200px;" /><span style="margin-left:12px;color:#999">支持 1 ~ 50000 条</span></el-form-item>
                <el-form-item label="数据类别"><el-select v-model="manualForm.dataType" style="width:200px;"><el-option v-for="dt in dataTypes" :key="dt.value" :label="dt.label" :value="dt.value" /><el-option label="全部五类" value="all" /></el-select></el-form-item>
                <el-form-item><el-button type="primary" @click="doManualCollect" :loading="collectAllLoading" :disabled="userRole!=='admin'">🚀 开始手动采集</el-button></el-form-item>
              </el-form>
            </el-tab-pane>
            <el-tab-pane label="🔄 自动(定时)采集" name="auto">
              <el-alert title="通过设置间隔时间(毫秒)和单次条数，系统会按此节奏持续自动采集" type="success" :closable="false" style="margin-bottom:16px;" />
              <el-form label-width="160px" style="max-width:900px;">
                <el-form-item label="采集城市"><el-input v-model="autoForm.city" placeholder="例如:上海" style="max-width:300px;" /></el-form-item>
                <el-form-item label="自动爬取间隔(毫秒)"><el-input-number v-model="autoForm.interval" :min="500" :max="86400000" :step="100" style="width:220px;" /><span style="margin-left:12px;color:#999">500ms ~ 24h</span></el-form-item>
                <el-form-item label="单次生成条数"><el-input-number v-model="autoForm.count" :min="1" :max="5000" :step="50" style="width:200px;" /></el-form-item>
                <el-form-item>
                  <el-button type="success" @click="startAutoCollect" :disabled="autoCollecting || userRole!=='admin'">▶ 启动自动采集</el-button>
                  <el-button type="danger" @click="stopAutoCollect" :disabled="!autoCollecting || userRole!=='admin'">⏸ 停止自动采集</el-button>
                  <el-tag v-if="autoCollecting" type="success" style="margin-left:12px;">运行中 - 已采集 {{ autoCollectedCount }} 次 / 共 {{ autoCollectedTotal }} 条</el-tag>
                </el-form-item>
              </el-form>
              <el-divider content-position="left">⏰ 定时任务配置</el-divider>
              <el-card shadow="hover">
                <el-row :gutter="16">
                  <el-col :span="12">
                    <h4>🔁 定时循环采集</h4>
                    <el-form label-width="120px">
                      <el-form-item label="当前间隔"><el-tag>{{ schedulerConfig.autoFixedRateDesc }}</el-tag><span style="margin-left:8px;color:#999">({{ schedulerConfig.autoFixedRateMs }}ms)</span></el-form-item>
                      <el-form-item label="修改为(毫秒)"><el-input-number v-model="schedulerForm.autoFixedRateMs" :min="500" :max="86400000" :step="1000" style="width:200px;" /></el-form-item>
                      <el-form-item label="单次生成条数"><el-input-number v-model="schedulerForm.autoCount" :min="1" :max="5000" style="width:200px;" /></el-form-item>
                    </el-form>
                  </el-col>
                  <el-col :span="12">
                    <h4>📅 每日定点采集</h4>
                    <el-form label-width="120px">
                      <el-form-item label="当前时间"><el-tag>{{ schedulerConfig.dailyCronDesc }}</el-tag></el-form-item>
                      <el-form-item label="修改 cron"><el-input v-model="schedulerForm.dailyCron" placeholder="0 0 2 * * ?" /><div style="color:#999;font-size:12px;margin-top:4px;">例: 0 0 2 * * ? = 每天2点</div></el-form-item>
                      <el-form-item label="单次生成条数"><el-input-number v-model="schedulerForm.dailyCount" :min="1" :max="5000" style="width:200px;" /></el-form-item>
                    </el-form>
                  </el-col>
                </el-row>
                <el-button type="primary" @click="loadSchedulerConfig" style="margin-right:8px;">🔄 刷新配置</el-button>
                <el-button type="success" @click="saveSchedulerConfig" :disabled="userRole!=='admin'">💾 保存配置</el-button>
              </el-card>
            </el-tab-pane>
            <el-tab-pane label="📡 实时流接入" name="stream">
              <el-alert :title="streamStatusText" :type="streamConnected ? 'success' : 'warning'" :closable="false" style="margin-bottom:16px;" />
              <el-form label-width="160px" style="max-width:900px;">
                <el-form-item label="数据流类型"><el-tag type="primary" size="large">{{ streamForm.dataType === 'mixed' ? '全类型混合流' : streamForm.dataType }}</el-tag></el-form-item>
                <el-form-item label="引擎"><el-tag type="warning">Spark Streaming (MongoDB 5秒轮询)</el-tag></el-form-item>
                <el-form-item label="轮询间隔">
                  <el-input-number v-model="streamForm.intervalMs" :min="2000" :max="30000" :step="1000" style="width:200px;" @change="onIntervalChange" />
                  <span style="margin-left:8px;color:#909399;font-size:12px;">ms/次（前端每N秒从MongoDB拉7条最新数据）</span>
                </el-form-item>
                <el-form-item>
                  <el-tag v-if="streamConnected" type="success" style="margin-left:12px;">● 运行中 — 已获取 {{ streamReceived }} 条</el-tag>
                  <el-tag v-else type="danger" style="margin-left:12px;">● 已停止</el-tag>
                  <el-button size="small" type="primary" plain style="margin-left:12px;" @click="manualReconnect">立即刷新</el-button>
                </el-form-item>
              </el-form>
            </el-tab-pane>
          </el-tabs>
          <!-- 采集状态总览 -->
          <el-divider>📊 采集状态总览</el-divider>
          <el-row :gutter="16">
            <el-col :span="4" v-for="(dt, idx) in dataTypes" :key="'cs_'+dt.value">
              <el-card shadow="hover" :body-style="{padding:'14px'}" :style="collectCardStyle(idx)">
                <div style="text-align:center;">
                  <div :style="{fontSize:'32px',lineHeight:'40px',marginBottom:'6px'}">{{ collectIcons[idx] }}</div>
                  <div :style="{fontSize:'14px',fontWeight:'bold',color:collectColors[idx],marginBottom:'8px'}">{{ dt.label }}数据</div>
                  <div :style="{fontSize:'24px',fontWeight:'bold',color:'#303133',lineHeight:'32px'}">{{ collectStatus[dt.label] ? collectStatus[dt.label].count : 0 }}</div>
                  <div style="font-size:12px;color:#909399;margin-top:2px;">已采集条数</div>
                  <el-divider style="margin:10px 0;" />
                  <div style="font-size:12px;">
                    状态: <el-tag size="small" :type="statusTagType(collectStatus[dt.label] ? collectStatus[dt.label].status : '未采集')">{{ collectStatus[dt.label] ? collectStatus[dt.label].status : '未采集' }}</el-tag>
                  </div>
                  <div style="font-size:11px;color:#909399;margin-top:4px;">🕐 {{ collectStatus[dt.label] && collectStatus[dt.label].time ? collectStatus[dt.label].time : '尚未采集' }}</div>
                </div>
              </el-card>
            </el-col>
          </el-row>
        </el-card>
      </el-tab-pane>

      <!-- ==================== 挖掘分析 ==================== -->
      <el-tab-pane label="挖掘分析" name="mining">
        <el-card>
          <el-steps :active="miningStep" align-center finish-status="success" style="margin-bottom:24px;">
            <el-step title="选择数据" description="选择要分析的数据类别" />
            <el-step title="选择算法" description="选择分析算法并执行" />
            <el-step title="查看结果" description="图表 + 关键指标 + AI建议" />
          </el-steps>
          <!-- Step 1 -->
          <div v-show="miningStep === 0">
            <el-alert title="选择要分析的数据类别" type="info" :closable="false" style="margin-bottom:12px;" />
            <el-tabs v-model="activeMiningType" type="card">
              <el-tab-pane v-for="dt in dataTypes" :key="'mt_'+dt.value" :label="dt.label" :name="dt.value" />
            </el-tabs>
            <div style="text-align:center;margin-top:20px;"><el-button type="primary" size="large" @click="miningStep = 1">下一步：选择算法 →</el-button></div>
          </div>
          <!-- Step 2 -->
          <div v-show="miningStep === 1">
            <el-alert :title="'当前数据类型：' + (dataTypeLabels[activeMiningType] || activeMiningType)" type="info" :closable="false" style="margin-bottom:12px;" />
            <el-row :gutter="16">
              <el-col :span="4" v-for="tt in miningTaskOptions" :key="tt.value">
                <el-card shadow="hover" :body-style="{padding:'20px',textAlign:'center',cursor:'pointer'}"
                  :style="{borderColor: miningSelectedTask===tt.value ? '#409eff' : ''}"
                  @click="runMiningWithConclusion(tt.value)">
                  <div style="font-size:32px;margin-bottom:8px;">{{ tt.icon }}</div>
                  <div style="font-size:14px;font-weight:bold;">{{ tt.label }}</div>
                  <div style="font-size:11px;color:#999;">{{ tt.desc }}</div>
                  <el-button v-if="miningSelectedTask===tt.value && miningLoading[activeMiningType]" type="primary" size="small" loading style="margin-top:10px;width:100%;">分析中...</el-button>
                </el-card>
              </el-col>
            </el-row>
            <div style="text-align:center;margin-top:16px;">
              <el-button @click="miningStep = 0">← 返回选择</el-button>
              <el-button type="danger" @click="executeAllMining" :loading="miningAllLoading" v-if="userRole==='admin'" style="margin-left:12px;">🚀 一键执行全部5种算法</el-button>
            </div>
          </div>
          <!-- Step 3 -->
          <div v-show="miningStep === 2">
            <el-alert :title="(dataTypeLabels[activeMiningType] || '') + ' · ' + (taskTypeLabels[miningSelectedTask] || miningSelectedTask) + ' 分析完成'" type="success" :closable="false" style="margin-bottom:16px;" />
            <el-row :gutter="12" style="margin-bottom:16px;">
              <el-col :span="6"><el-card shadow="hover" :body-style="{padding:'14px',textAlign:'center'}"><div style="color:#999;font-size:12px;">数据量</div><div style="font-size:28px;font-weight:bold;color:#409eff;">{{ miningSummary.total }}</div></el-card></el-col>
              <el-col :span="6"><el-card shadow="hover" :body-style="{padding:'14px',textAlign:'center'}"><div style="color:#999;font-size:12px;">算法</div><div style="font-size:18px;font-weight:bold;color:#67c23a;">{{ miningSummary.algorithm }}</div></el-card></el-col>
              <el-col :span="6"><el-card shadow="hover" :body-style="{padding:'14px',textAlign:'center'}"><div style="color:#999;font-size:12px;">分组/规则数</div><div style="font-size:28px;font-weight:bold;color:#e6a23c;">{{ miningSummary.groups }}</div></el-card></el-col>
              <el-col :span="6"><el-card shadow="hover" :body-style="{padding:'14px',textAlign:'center'}"><div style="color:#999;font-size:12px;">分析耗时</div><div style="font-size:20px;font-weight:bold;">{{ miningSummary.duration }}ms</div></el-card></el-col>
            </el-row>
            <el-row :gutter="16">
              <el-col :span="14">
                <el-card shadow="hover" header="🤖 DeepSeek 分析结论（根据数据类型+算法针对性生成）" :body-style="{padding:'16px',height:'420px',overflowY:'auto'}">
                  <div v-if="aiConclusionLoading" style="text-align:center;padding:80px 0;">
                    <el-icon class="is-loading" size="32"><Loading /></el-icon>
                    <div style="margin-top:12px;color:#999;">正在调用 DeepSeek 生成分析结论...</div>
                  </div>
                  <div v-else-if="aiConclusion" style="white-space:pre-line;font-size:13px;line-height:2;color:#ffffff;background:linear-gradient(135deg,#1a1a2e 0%,#16213e 100%);padding:16px;border-radius:8px;border-left:4px solid #409eff;">
                    {{ aiConclusion }}
                  </div>
                  <div v-else style="color:#999;text-align:center;padding:100px 0;">运行分析后自动生成结论</div>
                </el-card>
              </el-col>
              <el-col :span="10">
                <el-card shadow="hover" header="AI便民建议（基于今日真实数据）" :body-style="{padding:'16px',height:'420px',overflowY:'auto'}">
                  <div v-if="aiSuggestionsLoading" style="text-align:center;padding:60px 0;">
                    <el-icon class="is-loading" size="32"><Loading /></el-icon>
                    <div style="margin-top:12px;color:#999;">正在分析今日数据...</div>
                  </div>
                  <div v-else-if="aiSuggestions.length" style="font-size:13px;line-height:1.9;">
                    <div v-for="(s, i) in aiSuggestions" :key="i"
                         style="white-space:pre-line;padding:12px;margin-bottom:8px;border-left:3px solid #409eff;background:#f0f9eb;border-radius:4px;">
                      {{ s }}
                    </div>
                    <div style="text-align:center;margin-top:8px;">
                      <el-button size="small" type="primary" text @click="loadSuggestions">🔄 刷新建议</el-button>
                      <el-button size="small" type="success" text @click="loadLifeAdvice">🌟 生活贴士</el-button>
                    </div>
                  </div>
                  <div v-else style="color:#999;text-align:center;padding:50px 0;">
                    <div style="margin-bottom:12px;">点击下方按钮，基于今日真实数据生成便民建议</div>
                    <el-button type="primary" @click="loadSuggestions" :loading="aiSuggestionsLoading">📋 生成AI建议</el-button>
                  </div>
                </el-card>
              </el-col>
            </el-row>
            <el-row :gutter="16" style="margin-top:12px">
              <el-col :span="24">
                <el-card shadow="hover" header="🌟 生活化预测(对市民有用的建议)" :body-style="{padding:'16px',minHeight:'160px'}">
                  <div v-if="lifeAdviceLoading" style="text-align:center;padding:40px 0;color:#999;">
                    <el-icon class="is-loading"><Loading /></el-icon> 正在生成...
                  </div>
                  <div v-else-if="lifeAdvice" style="white-space:pre-line;font-size:13px;line-height:1.9;color:#2c5282;background:#ebf8ff;padding:14px;border-radius:4px;">{{ lifeAdvice }}</div>
                  <div v-else style="color:#999;text-align:center;padding:30px 0;">执行分析后自动生成</div>
                </el-card>
              </el-col>
            </el-row>
            <div style="text-align:center;margin-top:16px;">
              <el-button @click="miningStep = 1">← 换算法</el-button>
              <el-button type="primary" @click="miningStep = 0">← 换数据类型</el-button>
            </div>
          </div>
          <!-- 历史记录 -->
          <el-divider>📋 历史分析记录</el-divider>
          <el-table :data="miningResults" stripe max-height="300">
            <el-table-column prop="taskId" label="任务ID" width="120" />
            <el-table-column prop="dataType" label="数据类型" width="100"><template #default="s"><el-tag :type="dataTypeMap[s.row.dataType]">{{ dataTypeLabels[s.row.dataType] }}</el-tag></template></el-table-column>
            <el-table-column prop="taskType" label="分析类型" width="100" />
            <el-table-column prop="status" label="状态" width="80"><template #default="s"><el-tag :type="s.row.status==='success'?'success':'danger'">{{ s.row.status === 'success' ? '成功' : '失败' }}</el-tag></template></el-table-column>
            <el-table-column prop="createTime" label="时间" width="180" />
            <el-table-column prop="duration" label="耗时(ms)" width="100" />
          </el-table>
          <!-- 高级工具 -->
          <el-divider>🛠 高级工具</el-divider>
          <el-collapse v-model="advancedCollapse">
            <el-collapse-item title="🗺️ MapReduce 离线聚合" name="mr">
              <el-form :inline="true">
                <el-form-item label="数据类型"><el-select v-model="mrForm.dataType" style="width:140px;"><el-option v-for="dt in dataTypes" :key="dt.value" :label="dt.label" :value="dt.value" /></el-select></el-form-item>
                <el-form-item label="维度"><el-radio-group v-model="mrForm.dimension"><el-radio-button label="day">日</el-radio-button><el-radio-button label="month">月</el-radio-button><el-radio-button label="year">年</el-radio-button><el-radio-button label="hour">小时</el-radio-button><el-radio-button label="district">区域</el-radio-button></el-radio-group></el-form-item>
                <el-form-item><el-button type="primary" @click="runMapReduce" :loading="mrLoading" :disabled="userRole!=='admin'">🚀 执行</el-button></el-form-item>
              </el-form>
              <div v-if="mrResult">
                <el-row :gutter="12"><el-col :span="6"><el-card :body-style="{padding:'12px'}"><div style="color:#999;font-size:12px;">分组数</div><div style="font-size:16px;font-weight:bold;">{{ mrResult.totalGroups }}</div></el-card></el-col><el-col :span="6"><el-card :body-style="{padding:'12px'}"><div style="color:#999;font-size:12px;">记录数</div><div style="font-size:16px;font-weight:bold;">{{ mrResult.totalRecords }}</div></el-card></el-col></el-row>
                <el-table :data="mrResult.data || []" stripe max-height="300"><el-table-column prop="key" label="分组键" /><el-table-column prop="count" label="记录数" width="100" /></el-table>
              </div>
            </el-collapse-item>
            <el-collapse-item title="📦 HDFS 分布式归档" name="hdfs">
              <div style="display:flex;gap:8px;flex-wrap:wrap;margin-bottom:12px;">
                <el-tag :type="hdfsStatus.online ? 'success' : 'danger'" size="small">{{ hdfsStatus.distributedOnline ? 'HDFS 在线' : (hdfsStatus.online ? 'HDFS降级(本地)' : 'HDFS 离线') }}</el-tag>
                <span style="color:#a0aec0;font-size:13px;">归档文件: {{ hdfsStatus.fileCount || 0 }} 个</span>
                <el-button size="small" @click="loadHdfsStatus">🔄 刷新</el-button>
                <el-button size="small" type="primary" @click="archiveResultsToHdfs" :loading="hdfsLoading" :disabled="userRole!=='admin'">📤 归档挖掘结果</el-button>
                <el-button size="small" type="success" @click="archiveDataToHdfs" :loading="hdfsLoading" :disabled="userRole!=='admin'">📤 归档原始数据</el-button>
              </div>
              <el-table :data="hdfsFiles" stripe max-height="250" empty-text="暂无归档文件">
                <el-table-column prop="name" label="文件名" /><el-table-column prop="size" label="大小" width="120" /><el-table-column prop="date" label="日期" width="160" />
                <el-table-column label="操作" width="100"><template #default="s"><el-button size="small" type="warning" @click="restoreFromHdfs(s.row)" :disabled="userRole!=='admin'">🔄 恢复</el-button></template></el-table-column>
              </el-table>
            </el-collapse-item>
          </el-collapse>
        </el-card>
      </el-tab-pane>

      <!-- ==================== 历史回溯 ==================== -->
      <el-tab-pane label="历史回溯" name="history">
        <el-card>
          <h2 style="margin-top:0">📜 历史数据查询</h2>
          <el-form :inline="true">
            <el-form-item label="维度"><el-radio-group v-model="historyForm.dimension" @change="onDimensionChange"><el-radio-button label="day">日</el-radio-button><el-radio-button label="month">月</el-radio-button><el-radio-button label="year">年</el-radio-button></el-radio-group></el-form-item>
            <el-form-item label="值"><el-date-picker v-if="historyForm.dimension==='day'" v-model="historyForm.dateValue" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" :disabled-date="disabledDate" style="width:170px;" /><el-date-picker v-else-if="historyForm.dimension==='month'" v-model="historyForm.dateValue" type="month" format="YYYY-MM" value-format="YYYY-MM" style="width:170px;" /><el-date-picker v-else v-model="historyForm.dateValue" type="year" format="YYYY" value-format="YYYY" style="width:170px;" /></el-form-item>
            <el-form-item><el-button type="primary" @click="queryHistory" :loading="historyLoading">🔍 查询</el-button><el-button type="success" @click="exportHistory" :disabled="!historyResults.length">📤 导出CSV</el-button></el-form-item>
          </el-form>
          <el-table :data="historyResults" stripe max-height="500"><el-table-column prop="taskId" label="任务ID" width="120" /><el-table-column prop="dataType" label="数据类型" width="100" /><el-table-column prop="taskType" label="分析类型" width="100" /><el-table-column prop="dateStr" label="日期" width="110" /><el-table-column prop="status" label="状态" width="80" /><el-table-column prop="duration" label="耗时(ms)" width="80" /><el-table-column prop="createTime" label="时间" width="160" /></el-table>
        </el-card>
      </el-tab-pane>

      <!-- ==================== 用户管理 ==================== -->
      <el-tab-pane v-if="userRole === 'admin'" label="用户管理" name="admin">
        <el-row :gutter="16" style="margin-bottom:16px">
          <el-col :span="4"><el-card shadow="hover"><div class="stat-value" style="color:#4facfe">{{ users.length }}</div><div class="stat-label">总用户数</div></el-card></el-col>
        </el-row>
        <el-card header="账号管理">
          <div style="margin-bottom:12px;"><el-button type="primary" @click="openAddUser">➕ 新增用户</el-button><el-button @click="loadUsers">🔄 刷新</el-button></div>
          <el-table :data="users" stripe>
            <el-table-column prop="username" label="用户名" width="140" />
            <el-table-column prop="role" label="角色" width="100"><template #default="s"><el-tag v-if="s.row.role==='admin'" type="danger">管理员</el-tag><el-tag v-else>普通用户</el-tag></template></el-table-column>
            <el-table-column label="操作" width="320"><template #default="s"><template v-if="s.row.username !== 'admin'"><el-button type="primary" size="small" @click="openChangePassword(s.row)">🔑 改密</el-button><el-button type="warning" size="small" @click="changeRole(s.row)">{{ s.row.role==='admin'?'降为用户':'升为管理员' }}</el-button><el-button type="danger" size="small" @click="deleteUser(s.row)">🗑️ 删除</el-button></template><el-tag v-else type="success" size="small">系统账号</el-tag></template></el-table-column>
          </el-table>
        </el-card>
        <el-card header="操作日志" style="margin-top:16px"><el-table :data="logs" stripe max-height="300"><el-table-column prop="username" label="用户" /><el-table-column prop="operation" label="操作" /><el-table-column prop="target" label="目标" /><el-table-column prop="status" label="状态" /><el-table-column prop="createTime" label="时间" /></el-table></el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- 新增用户弹窗 -->
    <el-dialog v-model="addUserVisible" title="新增用户" width="420px">
      <el-form :model="newUser" label-width="80px">
        <el-form-item label="用户名"><el-input v-model="newUser.username" /></el-form-item>
        <el-form-item label="密码"><el-input v-model="newUser.password" type="password" show-password /></el-form-item>
        <el-form-item label="角色"><el-select v-model="newUser.role"><el-option label="普通用户" value="user" /><el-option label="管理员" value="admin" /></el-select></el-form-item>
      </el-form>
      <template #footer><el-button @click="addUserVisible = false">取消</el-button><el-button type="primary" @click="doAddUser">确定</el-button></template>
    </el-dialog>
    <!-- 修改密码弹窗 -->
    <el-dialog v-model="pwdVisible" title="修改密码" width="380px">
      <el-form :model="pwdForm" label-width="100px"><el-form-item label="目标用户"><el-input v-model="pwdForm.username" disabled /></el-form-item><el-form-item label="新密码"><el-input v-model="pwdForm.newPassword" type="password" show-password /></el-form-item></el-form>
      <template #footer><el-button @click="pwdVisible = false">取消</el-button><el-button type="primary" @click="doChangePassword">确定</el-button></template>
    </el-dialog>
    <!-- 维度下钻弹窗 -->
    <el-dialog v-model="drillVisible" :title="'维度下钻: '+drillTitle" width="700px">
      <el-table :data="drillData" stripe max-height="400"><el-table-column prop="district" label="区域" /><el-table-column prop="value1" :label="drillCol1" /><el-table-column prop="value2" :label="drillCol2" /><el-table-column prop="value3" :label="drillCol3" /></el-table>
    </el-dialog>
    <!-- Spark状态弹窗 -->
    <el-dialog v-model="sparkDialogVisible" title="Spark集群状态" width="480px">
      <el-descriptions :column="2" border size="small"><el-descriptions-item label="Spark版本">{{ sparkStatus.version }}</el-descriptions-item><el-descriptions-item label="运行模式">{{ sparkStatus.mode }}</el-descriptions-item><el-descriptions-item label="Master">{{ sparkStatus.master }}</el-descriptions-item><el-descriptions-item label="集群状态"><el-tag type="success">{{ sparkStatus.status }}</el-tag></el-descriptions-item></el-descriptions>
    </el-dialog>
    <!-- 实时监控抽屉 -->
    <el-drawer v-model="monitorVisible" title="📊 实时任务与系统监控" direction="rtl" size="80%">
      <div style="padding:0 16px;">
        <h3>🖥️ 系统指标</h3>
        <el-row :gutter="12">
          <el-col :span="6"><el-card shadow="hover" :body-style="{padding:'14px'}"><div style="color:#999;font-size:12px;">JVM堆</div><div style="font-size:20px;font-weight:bold;color:#00d4ff;">{{ monitorData.jvm?.heap?.used || 0 }} / {{ monitorData.jvm?.heap?.max || 0 }} MB</div></el-card></el-col>
          <el-col :span="6"><el-card shadow="hover" :body-style="{padding:'14px'}"><div style="color:#999;font-size:12px;">CPU</div><div style="font-size:20px;font-weight:bold;color:#e6a23c;">{{ (monitorData.jvm?.cpu?.processCpuLoad || 0).toFixed(1) }}%</div></el-card></el-col>
          <el-col :span="6"><el-card shadow="hover" :body-style="{padding:'14px'}"><div style="color:#999;font-size:12px;">运行时长</div><div style="font-size:20px;font-weight:bold;color:#67c23a;">{{ monitorData.jvm?.runtime?.uptimeDesc || '-' }}</div></el-card></el-col>
        </el-row>

        <h3 style="margin-top:24px;">⚡ Spark Streaming 接入点</h3>
        <el-row :gutter="12">
          <el-col :span="6"><el-card shadow="hover" :body-style="{padding:'14px'}"><div style="color:#999;font-size:12px;">引擎</div><div style="font-size:14px;font-weight:bold;color:#00d4ff;">{{ (sparkStreamStatus && sparkStreamStatus.engine) || '-' }}</div></el-card></el-col>
          <el-col :span="6"><el-card shadow="hover" :body-style="{padding:'14px'}"><div style="color:#999;font-size:12px;">上下文</div><div style="font-size:20px;font-weight:bold;"><el-tag :type="(sparkStreamStatus && sparkStreamStatus.ready) ? 'success' : 'info'">{{ (sparkStreamStatus && sparkStreamStatus.ready) ? '就绪' : '未启用' }}</el-tag></div></el-card></el-col>
          <el-col :span="6"><el-card shadow="hover" :body-style="{padding:'14px'}"><div style="color:#999;font-size:12px;">微批次间隔</div><div style="font-size:20px;font-weight:bold;color:#9b59b6;">{{ (sparkStreamStatus && sparkStreamStatus.microBatchIntervalSec) || 0 }}s</div></el-card></el-col>
          <el-col :span="6"><el-card shadow="hover" :body-style="{padding:'14px'}"><div style="color:#999;font-size:12px;">微批次累计</div><div style="font-size:20px;font-weight:bold;color:#e6a23c;">{{ (sparkStreamStatus && sparkStreamStatus.microBatchCount) || 0 }}</div></el-card></el-col>
        </el-row>
        <el-row :gutter="12" style="margin-top:12px;">
          <el-col :span="12"><el-card shadow="hover" :body-style="{padding:'14px'}"><div style="color:#999;font-size:12px;">处理记录数</div><div style="font-size:20px;font-weight:bold;color:#67c23a;">{{ (sparkStreamStatus && sparkStreamStatus.processedRecords) || 0 }}</div></el-card></el-col>
          <el-col :span="12"><el-card shadow="hover" :body-style="{padding:'14px'}"><div style="color:#999;font-size:12px;">CheckPoint 目录</div><div style="font-size:13px;color:#a0aec0;">{{ (sparkStreamStatus && sparkStreamStatus.checkpointDir) || '-' }}</div></el-card></el-col>
        </el-row>
        <el-alert v-if="sparkStreamStatus && sparkStreamStatus.note" :title="sparkStreamStatus.note" type="info" :closable="false" style="margin-top:10px;" />
      </div>
    </el-drawer>
  </div>
</template>

<script>
import { ref, reactive, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as echarts from 'echarts'
import { authAPI, dataAPI, miningAPI, visualAPI, schedulerAPI, monitorAPI, streamingAPI, hdfsAPI, suggestionAPI, urgentAlertsAPI, lifeAdviceAPI } from '../api'

export default {
  name: 'DashboardView',
  setup() {
    const router = useRouter()

    // ========== 登录状态 ==========
    const username = ref(localStorage.getItem('username') || '')
    const userRole = ref(localStorage.getItem('role') || '')
    function logout() {
      localStorage.removeItem('token')
      localStorage.removeItem('username')
      localStorage.removeItem('role')
      router.replace('/login')
    }

    // ========== Tab ==========
    const activeTab = ref('dashboard')
    const dashboardLoaded = ref(false)
    let collectStatusTimer = null

    // ========== 常量 ==========
    const dataTypes = [
      { label: '交通', value: 'traffic' }, { label: '气象', value: 'weather' },
      { label: '舆情', value: 'opinion' }, { label: '消费', value: 'consumption' },
      { label: '人口', value: 'population' }
    ]
    const streamTypes = [
      { label: '🚦 交通流', value: 'traffic' }, { label: '🌦️ 气象流', value: 'weather' },
      { label: '📣 舆情流', value: 'opinion' }, { label: '💰 消费流', value: 'consumption' },
      { label: '👥 人口流', value: 'population' }, { label: '🎲 混合流', value: 'mixed' }
    ]
    const dataTypeMap = { traffic: '', weather: 'success', opinion: 'warning', consumption: 'danger', population: 'info' }
    const dataTypeLabels = { traffic: '交通', weather: '气象', opinion: '舆情', consumption: '消费', population: '人口' }
    const taskTypeLabels = { statistic: '统计分析', cluster: '聚类分析', association: '关联规则', predict: '预测分析', anomaly: '异常检测', classify: '随机森林分类' }
    const collectIcons = ['🚦', '🌦️', '📣', '💰', '👥']
    const collectColors = ['#409EFF', '#67C23A', '#E6A23C', '#F56C6C', '#909399']
    const collectBgs = ['#ECF5FF', '#F0F9EB', '#FDF6EC', '#FEF0F0', '#F4F4F5']

    // ========== 数据采集 ==========
    const collectSubTab = ref('manual')
    const collectStatus = reactive({})
    const collectAllLoading = ref(false)
    const manualForm = reactive({ city: '上海', count: 500, dataType: 'all' })
    const autoForm = reactive({ city: '上海', interval: 1800000, count: 500 })
    const autoCollecting = ref(false)
    const autoCollectedCount = ref(0)
    const autoCollectedTotal = ref(0)
    let autoTimer = null
    const streamForm = reactive({ dataType: 'mixed', intervalMs: 5000 })
    const streamConnected = ref(false)
    const streamStatusText = ref('实时流轮询就绪')
    const streamReceived = ref(0)
    const schedulerConfig = reactive({ autoFixedRateMs: 1800000, autoFixedRateDesc: '30分钟', autoCount: 500, dailyCron: '0 0 2 * * ?', dailyCronDesc: '每天 02:00', dailyCount: 500 })
    const schedulerForm = reactive({ autoFixedRateMs: 1800000, autoCount: 500, dailyCron: '0 0 2 * * ?', dailyCount: 500 })

    // ========== 挖掘分析 ==========
    const miningStep = ref(0)
    const activeMiningType = ref('traffic')
    const miningSelectedTask = ref('statistic')
    const miningLoading = reactive({})
    const miningAllLoading = ref(false)
    const miningResults = ref([])
    const miningSummary = ref({ total: 0, algorithm: '', groups: 0, duration: 0, source: '' })
    const miningChartData = ref([])
    const aiConclusion = ref('')
    const aiConclusionLoading = ref(false)
    const aiSuggestions = ref([])
    const aiSuggestionsLoading = ref(false)
    // 紧急事件(大屏滚动条)
    const urgentAlerts = ref([])
    // 生活化预测
    const lifeAdvice = ref('')
    const lifeAdviceLoading = ref(false)
    const advancedCollapse = ref([])
    const miningTaskOptions = [
      { label: '各区数据汇总一览', value: 'statistic', icon: '📊', desc: '看各区交通/气象/消费等的总数、平均值、最高最低' },
      { label: '按拥堵程度自动分三档', value: 'cluster', icon: '🔍', desc: '自动把路段分为：畅通、一般、拥堵三档' },
      { label: '找出哪些因素总是一起变化', value: 'association', icon: '🔗', desc: '比如"下雨→拥堵多"、"高温→消费降"这样的关系' },
      { label: '预测明天可能发生什么', value: 'predict', icon: '📈', desc: '根据今天趋势，推算明天的拥堵指数、温度变化' },
      { label: '发现今天哪里不正常', value: 'anomaly', icon: '⚠️', desc: '自动识别突然特别堵、温度骤变、舆情爆发等异常' }
    ]

    // ========== 大屏 ==========
    const statCards = reactive([
      { label: '交通数据', value: 0, color: '#00d4ff' }, { label: '气象数据', value: 0, color: '#67c23a' },
      { label: '舆情数据', value: 0, color: '#e6a23c' }, { label: '消费数据', value: 0, color: '#f56c6c' },
      { label: '人口数据', value: 0, color: '#909399' }, { label: '实时流总量', value: 0, color: '#a855f7' }
    ])
    const selectedDate = ref(new Date().toISOString().slice(0, 10))
    const dashboardStatus = ref(null)  // {icon, text, color} 大屏数据加载状态提示
    const refreshEnabled = ref(false)
    let refreshTimer = null
    const sparkStatus = ref({ status: '未知' })
    const sparkDialogVisible = ref(false)
    const charts = {}

    // ========== 维度下钻 ==========
    const drillVisible = ref(false)
    const drillTitle = ref('')
    const drillCol1 = ref('')
    const drillCol2 = ref('')
    const drillCol3 = ref('')
    const drillData = ref([])
    const trafficFlowRaw = ref([])
    const trafficAnomalyRaw = ref([])
    const opinionAnomalyRaw = ref([])

    // ========== 历史回溯 ==========
    const historyForm = reactive({ dimension: 'day', dateValue: new Date().toISOString().substring(0, 10), dataType: '', taskType: '' })
    const historyResults = ref([])
    const historyLoading = ref(false)

    // ========== 监控 ==========
    const monitorVisible = ref(false)
    const monitorData = reactive({ jvm: {}, runningTasks: [], taskStats: {}, collectStats: { total: 0, byType: {} } })
    const sparkStreamStatus = reactive({ engine: '', ready: false, microBatchIntervalSec: 0, microBatchCount: 0, processedRecords: 0, checkpointDir: '', note: '' })
    async function loadSparkStreamStatus() {
      // 第1步: 拿后端 streaming-status (不依赖 stats, 独立跑)
      try {
        const token = localStorage.getItem('token')
        const headers = token ? { Authorization: 'Bearer ' + token } : {}
        const r1 = await fetch('/api/monitor/streaming-status', { headers })
        if (r1.ok) {
          const j = await r1.json()
          if (j.code === 200 && j.data) {
            Object.assign(sparkStreamStatus, {
              engine: 'Spark Streaming (DStream API)',
              ready: j.data.ready || false,
              microBatchIntervalSec: j.data.microBatchIntervalSec || 5,
              microBatchCount: j.data.microBatchCount || 0,
              processedRecords: j.data.processedRecords || 0,
              checkpointDir: j.data.checkpointDir || './spark-checkpoint',
              uptime: j.data.uptime || 0,
              uptimeDesc: j.data.uptimeDesc || '-',
              note: j.data.note || ''
            })
          }
        }
      } catch (e) { /* 静默 */ }
      // 第2步: 拿 MongoDB 真实数据补充 (失败不影响第1步)
      try {
        const stats = await streamingAPI.stats()
        if (stats && stats.code === 200 && stats.data) {
          const d = stats.data
          const total = d.total || 0
          sparkStreamStatus.processedRecords = total
          sparkStreamStatus.microBatchCount = Math.floor(total / 5)
          sparkStreamStatus.ready = total > 0
          sparkStreamStatus.note = total > 0
            ? `Spark Streaming 上下文已注入 (JavaStreamingContext), 通过 foreachRDD 接入实时数据流, 累计处理 ${total} 条实时事件 (${sparkStreamStatus.microBatchCount} 个微批次)`
            : '等待实时数据接入... (MongoDB streaming_results 集合当前为空)'
        }
      } catch (e) { /* 静默 */ }
    }

    // ========== MapReduce ==========
    const mrForm = reactive({ dataType: 'traffic', dimension: 'month', dateStr: '' })
    const mrResult = ref(null)
    const mrLoading = ref(false)

    // ========== HDFS ==========
    const hdfsStatus = ref({})
    const hdfsFiles = ref([])
    const hdfsLoading = ref(false)

    // ========== 用户管理 ==========
    const users = ref([])
    const addUserVisible = ref(false)
    const newUser = reactive({ username: '', password: '', role: 'user' })
    const pwdVisible = ref(false)
    const pwdForm = reactive({ userId: null, username: '', newPassword: '' })
    const logs = ref([])

    // ========== 辅助函数 ==========
    function collectCardStyle(idx) { return { borderTop: `4px solid ${collectColors[idx]}`, background: collectBgs[idx], transition: 'all 0.3s' } }
    function statusTagType(s) { if (!s) return 'info'; return s === 'success' ? 'success' : s === 'failed' ? 'danger' : s === '未采集' ? 'info' : 'primary' }
    function disabledDate(time) { return time.getTime() > Date.now() }

    // ========== 采集逻辑 ==========
    async function loadCollectStatus() { try { const res = await dataAPI.collectStatus(); if (res.code === 200) Object.assign(collectStatus, res.data) } catch (e) {} }
    function startCollectStatusPolling() { if (collectStatusTimer) return; collectStatusTimer = setInterval(async () => { await loadCollectStatus(); if (dashboardLoaded.value) { try { await loadAllCharts() } catch(e) {} } }, 3000) }
    function stopCollectStatusPolling() { if (collectStatusTimer) { clearInterval(collectStatusTimer); collectStatusTimer = null } }

    async function doManualCollect() {
      if (!manualForm.city) { ElMessage.warning('请填写城市'); return }
      collectAllLoading.value = true
      try {
        const res = manualForm.dataType === 'all'
          ? await dataAPI.collectAllWithKeyword(manualForm.city, manualForm.count, '新闻')
          : await dataAPI.collectWithKeyword(manualForm.dataType, manualForm.city, manualForm.count, '新闻')
        if (res.code === 200) ElMessage.success(`采集完成: ${res.data.total || res.data.count}条`)
      } catch (e) { ElMessage.error('采集失败') }
      collectAllLoading.value = false; loadCollectStatus()
    }

    async function startAutoCollect() {
      if (!autoForm.city) { ElMessage.warning('请填写城市'); return }
      autoCollecting.value = true; autoCollectedCount.value = 0; autoCollectedTotal.value = 0
      ElMessage.success(`自动采集已启动,每 ${autoForm.interval}ms 采集 ${autoForm.count} 条`)
      doAutoTick()
      autoTimer = setInterval(doAutoTick, autoForm.interval)
      startCollectStatusPolling()
    }
    async function doAutoTick() { if (!autoCollecting.value) return; try { const res = await dataAPI.collectAllWithKeyword(autoForm.city, autoForm.count, '新闻'); if (res.code === 200) { autoCollectedCount.value++; autoCollectedTotal.value += res.data.total } } catch (e) {}; loadCollectStatus() }
    function stopAutoCollect() { if (autoTimer) clearInterval(autoTimer); autoCollecting.value = false; stopCollectStatusPolling(); ElMessage.info(`已停止,共采集 ${autoCollectedTotal.value} 条`) }

    // ========== 实时流轮询：MongoDB 5秒轮询 (数据源 = streaming_results)
    // selectedDate == 今天 → 持续查询当天模拟采集 + 实时流, 会持续增长
    // selectedDate == 昨天/历史 → 当天快照, 不再轮询
    let streamPollTimer = null
    function startStreamPolling() {
      stopStreamPolling()
      streamConnected.value = true
      streamStatusText.value = '实时流轮询已启动 (数据源: ' + selectedDate.value + ')'
      pollStreamData()
      streamPollTimer = setInterval(() => pollStreamData(), Math.max(2000, streamForm.intervalMs || 5000))
    }
    function stopStreamPolling() {
      if (streamPollTimer) { clearInterval(streamPollTimer); streamPollTimer = null }
      streamConnected.value = false
      streamStatusText.value = '实时流轮询已停止'
    }
    async function pollStreamData() {
      const token = localStorage.getItem('token')
      const headers = token ? { Authorization: 'Bearer ' + token } : {}
      try {
        const r = await fetch('/api/streaming/dashboard?date=' + encodeURIComponent(selectedDate.value), { headers })
        if (!r.ok) return
        const j = await r.json()
        if (j.code === 200 && j.data) applyDashboardData(j.data)
      } catch (e) { /* 静默, 避免刷屏 */ }
    }
    function manualReconnect() {
      stopStreamPolling()
      streamStatusText.value = '手动刷新...'
      startStreamPolling()
    }
    function onIntervalChange(v) {
      stopStreamPolling()
      startStreamPolling()
      streamStatusText.value = '轮询间隔已调整为 ' + v + 'ms'
    }

    async function loadSchedulerConfig() { try { const res = await schedulerAPI.getConfig(); if (res && res.code === 200) { Object.assign(schedulerConfig, res.data); Object.assign(schedulerForm, { autoFixedRateMs: res.data.autoFixedRateMs, autoCount: res.data.autoCount, dailyCron: res.data.dailyCron, dailyCount: res.data.dailyCount }) } } catch (e) {} }
    async function saveSchedulerConfig() { try { const res = await schedulerAPI.updateConfig(schedulerForm.autoFixedRateMs, schedulerForm.autoCount, schedulerForm.dailyCron, schedulerForm.dailyCount); if (res && res.code === 200) { Object.assign(schedulerConfig, res.data); ElMessage.success('配置已保存') } } catch (e) { ElMessage.error('保存失败') } }

    // ========== AI便民建议 ==========
    async function loadSuggestions() {
      aiSuggestionsLoading.value = true
      try {
        const res = await suggestionAPI.get(selectedDate.value)
        if (res && (res.suggestions || res.data)) {
          aiSuggestions.value = res.suggestions || res.data
        } else {
          aiSuggestions.value = ['未能生成建议，请检查' + (res.basedOn || '') + '是否有数据']
        }
      } catch (e) {
        aiSuggestions.value = ['获取建议失败：' + (e.message || e)]
      } finally {
        aiSuggestionsLoading.value = false
      }
    }

    // ========== 大屏紧急事件滚动条 ==========
    async function loadUrgentAlerts() {
      try {
        const res = await urgentAlertsAPI.get(selectedDate.value)
        if (res && res.alerts) {
          urgentAlerts.value = res.alerts
        }
      } catch (e) {}
    }

    // ========== 生活化预测建议 ==========
    async function loadLifeAdvice() {
      lifeAdviceLoading.value = true
      try {
        const res = await lifeAdviceAPI.get(selectedDate.value)
        if (res && res.advice) {
          lifeAdvice.value = res.advice
        } else {
          lifeAdvice.value = '暂无数据，请先采集数据后查看建议。'
        }
      } catch (e) {
        lifeAdvice.value = '获取生活化建议失败：' + (e.message || e)
      } finally {
        lifeAdviceLoading.value = false
      }
    }

    // ========== 挖掘逻辑 ==========
    async function runMiningWithConclusion(taskType) {
      miningSelectedTask.value = taskType; miningLoading[activeMiningType.value] = true; aiConclusionLoading.value = true
      miningSummary.value = { total: 0, algorithm: '', groups: 0, duration: 0, source: '' }
      aiConclusion.value = ''
      // 先拿总数据量
      try { const sr = await streamingAPI.stats(); if (sr && sr.code === 200 && sr.data) { miningSummary.value.total = sr.data.processedRecords || sr.data.total || 0 } } catch (e) {}
      if (charts.miningChart) { charts.miningChart.dispose(); delete charts.miningChart }
      await nextTick()
      try {
        const res = await miningAPI.conclusion(activeMiningType.value, taskType)
        if (res.code === 200 && res.data) {
          const d = res.data
          let r = d.analysisResult || {}
          if (typeof r === 'string') { try { r = JSON.parse(r) } catch (e) { r = {} } }
          miningSummary.value = { total: miningSummary.value.total || (r.data_count || r.totalRecords || r.total || 0), algorithm: taskTypeLabels[taskType] || taskType, groups: r.totalGroups || (r.clusters ? r.clusters.length : 0) || (r.rules ? r.rules.length : 0) || (r.by_district ? r.by_district.length : 0) || r.anomaly_count || r.total_rules || 0, duration: r.duration_ms || r.duration || 0, source: (d.dataType || activeMiningType.value || '') + ' 来自 MongoDB' }
          aiConclusion.value = d.conclusion || '未生成结论（请确认 DeepSeek API Key 有效或网络可达）'
          miningStep.value = 2
          loadSuggestions()
          loadLifeAdvice()
          ElMessage.success((dataTypeLabels[activeMiningType.value] || '') + ' 分析完成')
        } else { ElMessage.error('分析失败: ' + (res.message || res.code || '未知错误')); console.error('mining conclusion failed:', res) }
      } catch (e) { ElMessage.error('分析失败: ' + (e.message || e)); console.error('mining exception:', e) }
      miningLoading[activeMiningType.value] = false; aiConclusionLoading.value = false; loadMiningResults()
    }
    function extractMiningChartData(r, taskType, dataType) {
      // 统计分析: 按区域聚合拥堵指数 + 流量
      if (taskType === 'statistic' && r.by_district) {
        return r.by_district.map(d => ({
          name: d.district || '?',
          value: Math.round(((d.congestion_index && d.congestion_index.avg) || 0) * 1000) / 1000
        }))
      }
      // 聚类: K-Means 输出 clusters 数组, 消费场景优先显示金额 sum, 交通显示 count
      if (taskType === 'cluster' && r.clusters) {
        return r.clusters.map(c => {
          const label = c.label || ('cluster_' + (c.clusterId ?? ''))
          const useSum = dataType === 'consumption' || dataType === 'population' || dataType === 'weather'
          const v = useSum ? (c.sum != null ? c.sum : (c.count ?? 0)) : (c.count ?? c.size ?? 0)
          return { name: label, value: Math.round(v * 100) / 100 }
        })
      }
      // 关联规则: 兼容 from/to (相关系数) 与 antecedent/consequent (Apriori) 两种格式
      if (taskType === 'association' && r.rules && r.rules.length) {
        return r.rules.slice(0, 12).map(r2 => {
          const lhs = r2.antecedent ? r2.antecedent.join('&') : (r2.from || '?')
          const rhs = r2.consequent ? r2.consequent.join('&') : (r2.to || '?')
          const conf = r2.confidence != null
            ? Math.round(r2.confidence * 100)
            : (r2.correlation != null ? Math.round(Math.abs(r2.correlation) * 100) : 0)
          return { name: lhs + ' → ' + rhs, value: conf }
        })
      }
      // 预测: 当前均值 vs 下一周期预测, 附 metric 标签
      if (taskType === 'predict' && r.current_avg != null) {
        const m = r.metric || ''
        const cur = Math.round((r.current_avg || 0) * 100) / 100
        const next = Math.round((r.predicted_next || 0) * 100) / 100
        return [
          { name: '当前均值 (' + m + ')', value: cur },
          { name: '下一周期预测 (' + m + ')', value: next }
        ]
      }
      // 异常检测: 返回丰富数据(所有数据点+正常区间+异常点), 用于绘制散点图
      if (taskType === 'anomaly') {
        return {
          _chartType: 'anomaly_scatter',
          metric: r.metric || '数值',
          q1: r.q1,
          q3: r.q3,
          lower_bound: r.lower_bound,
          upper_bound: r.upper_bound,
          anomaly_count: r.anomaly_count || 0,
          total_count: r.total_count || 0,
          points: r.points || []  // [{district, value, anomaly}]
        }
      }
      return []
    }
    function renderMiningChart(taskType, dataType) {
      if (charts.miningChart) { try { charts.miningChart.dispose() } catch(e) {}; delete charts.miningChart }
      const dom = document.getElementById('miningChart'); if (!dom) return
      const chart = echarts.init(dom, 'dark'); charts.miningChart = chart
      const data = miningChartData.value
      const title = (dataTypeLabels[dataType] || dataType) + ' - ' + (({statistic:'统计',cluster:'聚类',association:'关联',predict:'预测',anomaly:'异常'})[taskType] || taskType)
      const barColors = ['#409eff', '#67c23a', '#e6a23c', '#f56c6c', '#a855f7', '#00d4ff', '#ff6b9d', '#20d4ad']
      // 大数自动转"万/亿"单位
      const fmtLarge = v => {
        const n = Number(v); if (!isFinite(n)) return v
        if (Math.abs(n) >= 1e8) return (n / 1e8).toFixed(2) + '亿'
        if (Math.abs(n) >= 1e4) return (n / 1e4).toFixed(1) + '万'
        return n
      }
      // ===== 异常检测: 散点图 + 正常区间带 =====
      if (taskType === 'anomaly' && data && data._chartType === 'anomaly_scatter') {
        const points = data.points || []
        // X 轴用数据点索引 (若用 district 会重叠), 悬浮显示 district
        const normalData = points.filter(p => !p.anomaly).map(p => [p.district, Number(p.value)])
        const anomalyData = points.filter(p => p.anomaly).map(p => [p.district, Number(p.value)])
        const lower = Number(data.lower_bound) || 0
        const upper = Number(data.upper_bound) || 0
        const q1 = Number(data.q1) || 0
        const q3 = Number(data.q3) || 0
        // X 轴取所有 distinct district 做分类
        const districts = Array.from(new Set(points.map(p => p.district)))
        chart.setOption({
          title: { text: title + ' - 基于 IQR 区间的异常点识别', subtext: '共 ' + data.total_count + ' 条数据, 检测到 ' + data.anomaly_count + ' 个异常点 (指标: ' + data.metric + ')', left: 'center', textStyle: { color: '#fff', fontSize: 14 }, subtextStyle: { color: '#ccc', fontSize: 11 } },
          tooltip: { trigger: 'item', formatter: p => {
            if (p.seriesType === 'scatter') {
              const isAnom = p.seriesName === '异常点'
              return '<b>' + (p.value[0] || '') + '</b><br/>指标值: <b>' + fmtLarge(p.value[1]) + '</b><br/>状态: ' + (isAnom ? '<span style="color:#f56c6c">⚠ 异常</span>' : '<span style="color:#67c23a">✓ 正常</span>')
            }
            return p.seriesName + ': ' + fmtLarge(p.value)
          } },
          legend: { data: ['正常点', '异常点', 'Q1/Q3 正常区间', '上界 (Q3+1.5IQR)', '下界 (Q1-1.5IQR)'], bottom: 0, textStyle: { color: '#ddd', fontSize: 11 } },
          grid: { top: 90, left: 80, right: 40, bottom: 70 },
          xAxis: {
            type: 'category',
            data: districts.length ? districts : ['-'],
            name: '区域',
            nameTextStyle: { color: '#aaa' },
            axisLabel: { color: '#ddd', rotate: 35, fontSize: 10, interval: 0 }
          },
          yAxis: {
            type: 'value',
            name: data.metric,
            nameTextStyle: { color: '#aaa' },
            axisLabel: { color: '#ddd', formatter: fmtLarge },
            splitLine: { lineStyle: { color: 'rgba(255,255,255,0.08)' } }
          },
          series: [
            // Q1~Q3 正常区间带 (markArea)
            {
              name: 'Q1/Q3 正常区间',
              type: 'scatter',
              data: [],
              markArea: {
                silent: true,
                itemStyle: { color: 'rgba(103,194,58,0.12)' },
                label: { show: false },
                data: [[
                  { yAxis: q1, name: 'Q1' },
                  { yAxis: q3, name: 'Q3' }
                ]]
              }
            },
            // 上界 / 下界 (markLine)
            {
              name: '上界 (Q3+1.5IQR)',
              type: 'scatter',
              data: [],
              markLine: {
                silent: true,
                symbol: 'none',
                lineStyle: { color: '#f56c6c', type: 'dashed' },
                label: { color: '#f56c6c', formatter: '上界 ' + fmtLarge(upper) },
                data: [{ yAxis: upper }]
              }
            },
            {
              name: '下界 (Q1-1.5IQR)',
              type: 'scatter',
              data: [],
              markLine: {
                silent: true,
                symbol: 'none',
                lineStyle: { color: '#e6a23c', type: 'dashed' },
                label: { color: '#e6a23c', formatter: '下界 ' + fmtLarge(lower) },
                data: [{ yAxis: lower }]
              }
            },
            // 正常点 (绿色)
            {
              name: '正常点',
              type: 'scatter',
              data: normalData,
              symbolSize: 10,
              itemStyle: { color: '#67c23a', opacity: 0.7 }
            },
            // 异常点 (红色, 大一点, 带标注)
            {
              name: '异常点',
              type: 'scatter',
              data: anomalyData,
              symbolSize: 16,
              itemStyle: { color: '#f56c6c', borderColor: '#fff', borderWidth: 1 },
              label: {
                show: true,
                position: 'top',
                color: '#fff',
                backgroundColor: 'rgba(245,108,108,0.85)',
                padding: [2, 6],
                borderRadius: 3,
                fontSize: 10,
                formatter: p => '⚠ ' + fmtLarge(p.value[1])
              }
            }
          ]
        })
        return
      }
      // ===== 其他分析类型: 柱状图 (保持原状) =====
      if (!data || !data.length) return
      chart.setOption({
        title: { text: title, left: 'center', textStyle: { color: '#fff', fontSize: 14 } },
        tooltip: { trigger: 'axis', formatter: ps => ps.map(p => p.name + ': ' + fmtLarge(p.value)).join('<br/>') },
        grid: { top: 50, left: 70, right: 30, bottom: 60 },
        xAxis: { type: 'category', data: data.map(d => String(d.name)), axisLabel: { color: '#fff', rotate: 20, fontSize: 11, interval: 0 } },
        yAxis: { type: 'value', axisLabel: { color: '#fff', formatter: fmtLarge } },
        series: [{ type: 'bar', data: data.map((d, i) => ({ value: d.value, itemStyle: { color: barColors[i % barColors.length] } })), label: { show: true, position: 'top', color: '#fff', formatter: p => fmtLarge(p.value) } }]
      })
    }
    async function executeAllMining() {
      miningAllLoading.value = true
      try { const res = await miningAPI.executeAll(''); if (res.code === 200) ElMessage.success('全部5种算法完成! 请点单个算法查看图表') } catch (e) { ElMessage.error('一键执行失败: ' + (e.message || e)); console.error('executeAll error:', e) }
      miningAllLoading.value = false; loadMiningResults()
    }
    async function loadMiningResults() { try { const res = await miningAPI.results(''); if (res.code === 200 && res.data) miningResults.value = res.data } catch (e) {} }

    // ========== 大屏图表 ==========
    function initChart(id, option) { const dom = document.getElementById(id); if (!dom) return null; if (charts[id]) charts[id].dispose(); const c = echarts.init(dom, 'dark'); c.setOption(option); charts[id] = c; return c }
    async function loadOverview() { try { const res = await visualAPI.overview(); if (res.code === 200 && res.data) { statCards[0].value = res.data.traffic_count || 0; statCards[1].value = res.data.weather_count || 0; statCards[2].value = res.data.opinion_count || 0; statCards[3].value = res.data.consumption_count || 0; statCards[4].value = res.data.population_count || 0; statCards[5].value = res.data.analysis_count || 0 } } catch (e) {} }
    async function loadCongestionPie() { try { const res = await visualAPI.congestionPie(selectedDate.value); if (res.code === 200 && res.data.data) initChart('chart-congestion', { tooltip: { trigger: 'item' }, legend: { bottom: 0, textStyle: { color: '#8899aa' } }, series: [{ type: 'pie', radius: ['35%', '65%'], center: ['50%', '45%'], label: { color: '#ccc' }, data: res.data.data }] }) } catch (e) {} }
    async function loadPopulationBar() { try { const res = await visualAPI.populationBar(selectedDate.value); if (res.code === 200 && res.data.data) { const d = res.data.data.slice().sort((a, b) => (b.density || 0) - (a.density || 0)).slice(0, 5); const dom = document.getElementById('chart-population'); if (!dom) return; if (charts.population) charts.population.dispose(); const chart = echarts.init(dom); charts.population = chart; const maxV = Math.max(1, ...d.map(i => i.density || 0)); chart.setOption({ title: { text: 'TOP 5 高活跃区域', textStyle: { color: '#00d4ff', fontSize: 13 }, left: 'center', top: 3 }, tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, formatter: p => { const i = p[0].dataIndex; return p[0].name + '<br/>今日事件: <b>' + d[i].density + '</b> 起<br/>累计事件量: <b>' + d[i].resident + '</b> 起' } }, grid: { left: 80, right: 60, top: 45, bottom: 20, containLabel: true }, xAxis: { type: 'value', name: '事件数', nameTextStyle: { color: '#8899aa', fontSize: 10 }, axisLabel: { color: '#8899aa' } }, yAxis: { type: 'category', data: d.map(i => i.district), axisLabel: { color: '#ccc', fontSize: 11 } }, series: [{ name: '今日事件', type: 'bar', data: d.map(i => ({ value: i.density, itemStyle: { color: { type: 'linear', x: 0, y: 0, x2: 1, y2: 0, colorStops: [{ offset: 0, color: '#3b82f6' }, { offset: 1, color: '#22d3ee' }] } } })), label: { show: true, position: 'right', color: '#fff', fontSize: 10, formatter: p => p.value + '起' }, barWidth: '50%', itemStyle: { borderRadius: [0, 4, 4, 0] } }] }) } } catch (e) {} }
    async function loadWeatherLine() { try { const res = await visualAPI.weatherLine(selectedDate.value); if (res.code === 200 && res.data.data) { const d = res.data.data.slice(0, 50); const xl = d.map(i => { const t = new Date(Number(i.time)); return String(t.getHours()).padStart(2,'0') + ':' + String(t.getMinutes()).padStart(2,'0') }); initChart('chart-weather', { tooltip: { trigger: 'axis' }, legend: { data: ['温度','湿度','AQI'], textStyle: { color: '#8899aa' } }, xAxis: { type: 'category', data: xl, axisLabel: { color: '#8899aa' } }, yAxis: { type: 'value', axisLabel: { color: '#8899aa' } }, series: [{ name: '温度', type: 'line', data: d.map(i => i.temperature), smooth: true, itemStyle: { color: '#f56c6c' } }, { name: '湿度', type: 'line', data: d.map(i => i.humidity), smooth: true, itemStyle: { color: '#409eff' } }, { name: 'AQI', type: 'line', data: d.map(i => i.aqi), smooth: true, itemStyle: { color: '#e6a23c' } }] }) } } catch (e) {} }
    async function loadConsumptionScatter() { try { const res = await streamingAPI.dashboard(selectedDate.value); const data = res && res.data && res.data.data ? res.data.data : (res && res.data ? res.data : null); const heatData = data && data.consumptionHeat ? data.consumptionHeat : []; const dom = document.getElementById('chart-consumption'); if (!dom) return; if (charts.consumption) charts.consumption.dispose(); const chart = echarts.init(dom); charts.consumption = chart; if (!heatData.length) return; const allHours = Array.from({ length: 24 }, (_, h) => h + '时'); const districts = heatData.map(d => d.district); const cells = []; let maxVal = 0; heatData.forEach((d, di) => { (d.hourly || []).forEach((v, h) => { cells.push([h, di, v]); if (v > maxVal) maxVal = v; }); }); const fmt = n => n >= 10000 ? (n / 10000).toFixed(1) + '万' : (n >= 1000 ? (n / 1000).toFixed(1) + 'k' : n); chart.setOption({ title: [{ text: '24小时 × 各区 消费交易笔数热力图', left: 'center', top: 0, textStyle: { color: '#00d4ff', fontSize: 14, fontWeight: 'bold' } }, { text: '横轴=0~23小时  纵轴=各区  格子数字=交易笔数\n颜色越红=笔数越多=消费越旺', left: 'center', top: 26, textStyle: { color: '#ccc', fontSize: 11 } }], tooltip: { position: 'top', formatter: p => '<b>' + districts[p.value[1]] + ' · ' + allHours[p.value[0]] + '</b><br/>交易笔数: <b style="color:#00d4ff">' + p.value[2] + ' 笔</b><br/>' + (p.value[2] > 0 ? '✅ 正常' : '💤 无消费') }, grid: { left: 80, right: 30, top: 80, bottom: 60, containLabel: true }, xAxis: { type: 'category', data: allHours, splitArea: { show: true }, axisLabel: { color: '#ccc', fontSize: 9, interval: 0 }, name: '小时', nameLocation: 'middle', nameGap: 30, nameTextStyle: { color: '#00d4ff' } }, yAxis: { type: 'category', data: districts, splitArea: { show: true }, axisLabel: { color: '#ccc', fontSize: 11 }, name: '地区', nameTextStyle: { color: '#00d4ff' } }, visualMap: { min: 0, max: Math.max(1, maxVal), calculable: true, orient: 'horizontal', left: 'center', bottom: 10, text: ['多', '少'], textStyle: { color: '#ccc' }, inRange: { color: ['#1e3a8a', '#3b82f6', '#22d3ee', '#fef08a', '#fb923c', '#dc2626'] } }, series: [{ name: '消费笔数', type: 'heatmap', data: cells, label: { show: true, fontSize: 9, color: '#fff', formatter: p => p.value[2] > 0 ? fmt(p.value[2]) : '' }, itemStyle: { borderColor: '#0f172a', borderWidth: 1 } }] }) } catch (e) { console.error('消费24h×地区热力图加载失败', e) } }
    async function loadOpinionGauge() { try { const res = await visualAPI.opinionGauge(selectedDate.value); if (res.code === 200 && res.data) { const dom = document.getElementById('chart-opinion'); if (!dom) return; if (charts.opinion) charts.opinion.dispose(); const chart = echarts.init(dom); charts.opinion = chart; const posRatio = res.data.positive_ratio || 0; const total = res.data.total_count || 0; const posCnt = res.data.positive_count || 0; const negCnt = res.data.negative_count || 0; const neuCnt = res.data.neutral_count || 0; const posColor = posRatio >= 60 ? '#67c23a' : (posRatio >= 40 ? '#e6a23c' : '#f56c6c'); chart.setOption({ title: { text: '今日舆情: 正面 ' + posRatio + '% | 共 ' + total + ' 条', left: 'center', top: 8, textStyle: { color: posColor, fontSize: 14, fontWeight: 'bold' } }, tooltip: { trigger: 'item', formatter: '{b}: {c} 条 ({d}%)' }, legend: { bottom: 10, textStyle: { color: '#8899aa' } }, series: [{ name: '情感构成', type: 'pie', radius: ['45%', '70%'], center: ['50%', '52%'], avoidLabelOverlap: true, label: { show: true, formatter: '{b}\n{d}%', color: '#ccc', fontSize: 12 }, labelLine: { lineStyle: { color: '#aaa' } }, data: [{ name: '正面', value: posCnt, itemStyle: { color: '#67c23a' } }, { name: '中性', value: neuCnt, itemStyle: { color: '#909399' } }, { name: '负面', value: negCnt, itemStyle: { color: '#f56c6c' } }] }] }) } } catch (e) {} }
    async function loadAnalysisSummary() { try { const res = await visualAPI.analysisSummary(); if (res.code === 200 && res.data.recent) { const types = res.data.recent.map(r => r.taskType).reduce((acc, v) => { acc[v] = (acc[v]||0)+1; return acc }, {}); const TYPE_CN = { 'statistic': '统计分析', 'statistics': '统计分析', 'statistic_analysis': '统计分析', 'cluster': '聚类分析', 'clustering': '聚类分析', 'cluster_analysis': '聚类分析', 'association': '关联分析', 'correlation': '关联分析', 'association_analysis': '关联分析', 'prediction': '预测分析', 'forecast': '预测分析', 'prediction_analysis': '预测分析', 'anomaly': '异常检测', 'outlier': '异常检测', 'anomaly_analysis': '异常检测', 'classification': '分类分级', 'classify': '分类分级', 'classification_analysis': '分类分级' }; const PALETTE = ['#5470c6', '#91cc75', '#fac858', '#ee6666', '#73c0de', '#3ba272']; const data = Object.entries(types).map(([k, v], i) => ({ name: TYPE_CN[k] || k, value: v, itemStyle: { color: PALETTE[i % PALETTE.length] } })); initChart('chart-analysis', { tooltip: { trigger: 'item', formatter: '{b}: {c}次 ({d}%)' }, legend: { bottom: 5, textStyle: { color: '#8899aa' } }, series: [{ type: 'pie', radius: ['40%', '65%'], data: data, label: { color: '#fff', formatter: '{b}\n{d}%' } }] }) } } catch (e) {} }
    async function loadHeatmap() { try { const res = await visualAPI.trafficHeatmap(selectedDate.value); if (res && res.code === 200 && res.data && res.data.data) renderHeatmap(res.data.data) } catch (e) {} }
    function renderHeatmap(data) { const dom = document.getElementById('chart-heatmap'); if (!dom) return; if (charts.heatmap) charts.heatmap.dispose(); const chart = echarts.init(dom); charts.heatmap = chart; if (!data || !data.length) { chart.setOption({ title: { text: '暂无数据', left: 'center', top: 'center', textStyle: { color: '#ccc' } } }); return; } const allHours = Array.from({ length: 24 }, (_, i) => i + '时'); const allDays = []; for (let i = 6; i >= 0; i--) { const d = new Date(); d.setDate(d.getDate() - i); allDays.push((d.getMonth() + 1) + '/' + d.getDate()); } const map = {}; data.forEach(d => { if (!d.hour && d.hour !== 0) return; const dayKey = d.day || ''; const hr = Number(d.hour); if (isNaN(hr)) return; const key = dayKey + '_' + hr; map[key] = Number(d.flow || d.congestion || 0); }); const maxVal = Math.max(1, ...Object.values(map)); const cells = []; for (const dy of allDays) { for (const h of allHours) { const hr = Number(String(h).replace('时', '')); cells.push([hr, dy, map[dy + '_' + hr] || 0]); } } chart.setOption({ title: [{ text: '车流量热力图：颜色越深=车越多=越堵', left: 'center', top: 4, textStyle: { color: '#00d4ff', fontSize: 13, fontWeight: 'bold' } }, { text: '看图说明：\n每行=一天(从上往下是6天前→今天)\n每列=1小时(0~23点)\n格子里的数字=该小时车流量(辆)\n蓝色=通畅，红色=拥堵', left: 'right', top: 30, textStyle: { color: '#8899aa', fontSize: 10, lineHeight: 14 } }], tooltip: { position: 'top', formatter: p => p.value[1] + ' ' + p.value[0] + '时<br/>车流量: <b>' + p.value[2] + ' 辆</b><br/>拥堵状态: <b>' + (p.value[2] >= maxVal * 0.7 ? '拥堵' : (p.value[2] >= maxVal * 0.4 ? '中度' : '通畅')) + '</b>' }, grid: { left: 60, right: 220, top: 35, bottom: 60 }, xAxis: { type: 'category', data: allHours, splitArea: { show: true }, axisLabel: { color: '#8899aa', fontSize: 9 } }, yAxis: { type: 'category', data: allDays, splitArea: { show: true }, axisLabel: { color: '#8899aa', fontSize: 11 } }, visualMap: { min: 0, max: maxVal, calculable: true, orient: 'horizontal', left: 'center', bottom: 10, textStyle: { color: '#ccc' }, inRange: { color: ['#67c23a', '#5ed2a3', '#ffd54f', '#ff8a65', '#f56c6c'] }, text: ['拥堵', '通畅'] }, series: [{ name: '车流辆数', type: 'heatmap', data: cells, label: { show: true, fontSize: 9, color: '#fff', formatter: p => p.value[2] >= maxVal * 0.4 ? Math.round(p.value[2]) : '' } }] }) }
    async function loadAccidentRisk() { try { const res = await visualAPI.accidentRisk(selectedDate.value); if (res && res.code === 200 && res.data && res.data.data) { const dom = document.getElementById('chart-accident'); if (!dom) return; if (charts.accident) charts.accident.dispose(); const chart = echarts.init(dom); charts.accident = chart; const data = res.data.data; const colorMap = { '高风险': '#f56c6c', '中风险': '#e6a23c', '低风险': '#67c23a' }; const riskOrder = { '高风险': 0, '中风险': 1, '低风险': 2 }; const sorted = data.map((d, i) => i).sort((a, b) => (riskOrder[data[a].risk] ?? 3) - (riskOrder[data[b].risk] ?? 3)); const sD = sorted.map(i => data[i].district); const sA = sorted.map(i => data[i].accidents); const sR = sorted.map(i => data[i].risk); chart.setOption({ tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, formatter: p => { const i = p[0].dataIndex; return sD[i] + '<br/>事故数: <b>' + sA[i] + '</b><br/>风险等级: <b style="color:' + colorMap[sR[i]] + '">' + sR[i] + '</b>' } }, legend: { data: ['低风险', '中风险', '高风险'], textStyle: { color: '#ccc' }, top: 5 }, grid: { left: 70, right: 30, top: 45, bottom: 30 }, xAxis: { type: 'value', name: '事故数', axisLabel: { color: '#ccc' } }, yAxis: { type: 'category', data: sD, axisLabel: { color: '#ccc', fontSize: 11 } }, series: [{ name: '事故数', type: 'bar', data: sA.map((v, i) => ({ value: v, itemStyle: { color: colorMap[sR[i]] } })), label: { show: true, position: 'right', color: '#fff', formatter: p => p.value + '起' }, itemStyle: { borderRadius: [0, 4, 4, 0] } }] }) } } catch (e) {} }
    async function loadTravel() { try { const res = await visualAPI.travelPattern(selectedDate.value); if (res && res.code === 200 && res.data && res.data.data) { const dom = document.getElementById('chart-travel'); if (!dom) return; if (charts.travel) charts.travel.dispose(); const chart = echarts.init(dom); charts.travel = chart; chart.setOption({ tooltip: { trigger: 'axis' }, xAxis: { type: 'category', data: res.data.data.map(d => d.hour), axisLabel: { color: '#ccc', fontSize: 9, rotate: 45 } }, yAxis: { type: 'value' }, series: [{ type: 'line', data: res.data.data.map(d => d.avg_flow), smooth: true, areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: 'rgba(0,245,255,0.3)' }, { offset: 1, color: 'rgba(0,245,255,0)' }] } }, lineStyle: { color: '#00f5ff' }, itemStyle: { color: '#00f5ff' } }] }) } } catch (e) {} }
    async function loadTrafficFlow24h() { try { const res = await visualAPI.trafficFlow24h(selectedDate.value); if (res && res.code === 200 && res.data && res.data.data) { trafficFlowRaw.value = res.data.data; const dom = document.getElementById('chart-traffic-flow'); if (!dom) return; if (charts.flow) charts.flow.dispose(); const chart = echarts.init(dom); charts.flow = chart; const hours = res.data.data.map(d => d.hour + ':00'); const flows = res.data.data.map(d => d.flow); const congs = res.data.data.map(d => d.congestion); chart.setOption({ tooltip: { trigger: 'axis' }, legend: { data: ['车流量', '拥堵指数'], textStyle: { color: '#ccc' }, top: 5 }, grid: { left: 55, right: 55, top: 40, bottom: 40 }, xAxis: { type: 'category', data: hours, axisLabel: { color: '#ccc', fontSize: 9, rotate: 45 } }, yAxis: [{ type: 'value', name: '车流量(辆)', nameTextStyle: { color: '#ccc' }, axisLabel: { color: '#ccc', formatter: v => v >= 1000000 ? (v / 1000000).toFixed(1) + 'M' : (v >= 1000 ? (v / 1000).toFixed(0) + 'k' : v) } }, { type: 'value', name: '拥堵指数', min: 0, max: 1, nameTextStyle: { color: '#ccc' }, axisLabel: { color: '#ccc' } }], series: [{ name: '车流量', type: 'bar', data: flows, itemStyle: { color: '#5470c6', borderRadius: [4, 4, 0, 0] }, label: { show: true, position: 'top', color: '#fff', fontSize: 9, formatter: p => p.value >= 1000 ? Math.round(p.value / 100) / 10 + 'k' : p.value } }, { name: '拥堵指数', type: 'line', yAxisIndex: 1, data: congs, smooth: true, symbol: 'circle', symbolSize: 6, lineStyle: { color: '#ee6666', width: 3 }, itemStyle: { color: '#ee6666' }, markLine: { silent: true, data: [{ yAxis: 0.7, name: '拥堵警戒线 0.7', lineStyle: { color: '#ff4757', type: 'dashed' } }] } }] }) } } catch (e) {} }
    async function loadTrafficAnomaly() { try { const res = await visualAPI.trafficAnomaly(selectedDate.value); if (res && res.code === 200 && res.data && res.data.data) { trafficAnomalyRaw.value = res.data.data; const dom = document.getElementById('chart-traffic-anomaly'); if (!dom) return; if (charts.trafficAnomaly) charts.trafficAnomaly.dispose(); const chart = echarts.init(dom); charts.trafficAnomaly = chart; chart.setOption({ tooltip: { trigger: 'axis' }, xAxis: { type: 'category', data: res.data.data.map(d => d.district), axisLabel: { color: '#ccc', fontSize: 9, rotate: 30 } }, yAxis: { type: 'value' }, series: [{ type: 'bar', data: res.data.data.map(d => ({ value: d.accidents, itemStyle: { color: d.anomaly ? '#ff4757' : '#2ed573' } })), markLine: { silent: true, data: [{ yAxis: res.data.avg_accidents, name: '均值', lineStyle: { color: '#ffa502' } }] } }] }) } } catch (e) {} }
    async function loadOpinionAnomaly() { try { const res = await visualAPI.opinionAnomaly(selectedDate.value); if (res && res.code === 200 && res.data && res.data.data) { opinionAnomalyRaw.value = res.data.data; const dom = document.getElementById('chart-opinion-anomaly'); if (!dom) return; if (charts.opinionAnomaly) charts.opinionAnomaly.dispose(); const chart = echarts.init(dom); charts.opinionAnomaly = chart; const data = res.data.data; const districts = data.map(d => d.district); const hotIndex = data.map(d => d.hot_index || 0); const negRatio = data.map(d => Math.round((d.negative_ratio || 0) * 1000) / 10); const anomaly = data.map(d => d.anomaly); const anomalyCnt = anomaly.filter(Boolean).length; const topIdx = negRatio.indexOf(Math.max(...negRatio)); const topDistrict = districts[topIdx]; const topNeg = negRatio[topIdx]; const conclusion = anomalyCnt > 0 ? ('共发现 ' + anomalyCnt + ' 个异常区域, 其中 ' + topDistrict + ' 负面率最高 ' + topNeg + '%') : '所有区域舆情正常, 未发现异常'; chart.setOption({ title: [{ text: '舆情热点与异常识别', left: 'center', top: 0, textStyle: { color: '#00d4ff', fontSize: 14, fontWeight: 'bold' } }], tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, formatter: p => { const i = p[0].dataIndex; const status = anomaly[i] ? '<span style="color:#ff4757;font-weight:bold">⚠ 异常</span>' : '<span style="color:#67c23a">✓ 正常</span>'; return '<b>' + districts[i] + '</b><br/>' + p[0].marker + ' 热度指数: <b>' + hotIndex[i] + '</b><br/>' + p[1].marker + ' 负面比例: <b>' + negRatio[i] + '%</b><br/>状态: ' + status } }, legend: { data: ['热度指数', '负面比例(%)'], textStyle: { color: '#ccc' }, top: 28 }, grid: { left: 60, right: 60, top: 70, bottom: 70, containLabel: true }, xAxis: { type: 'category', data: districts, axisLabel: { color: '#ccc', fontSize: 10, rotate: 25, interval: 0 } }, yAxis: [{ type: 'value', name: '热度指数', nameTextStyle: { color: '#ccc' }, axisLabel: { color: '#ccc' } }, { type: 'value', name: '负面比例(%)', min: 0, max: 100, nameTextStyle: { color: '#ccc' }, axisLabel: { color: '#ccc' } }], series: [{ name: '热度指数', type: 'bar', data: hotIndex.map((v, i) => ({ value: v, itemStyle: { color: anomaly[i] ? '#ff4757' : '#5470c6', borderRadius: [4, 4, 0, 0] } })), barWidth: '30%', label: { show: true, position: 'top', color: '#fff', fontSize: 9, formatter: p => hotIndex[p.dataIndex] } }, { name: '负面比例(%)', type: 'bar', yAxisIndex: 1, data: negRatio.map((v, i) => ({ value: v, itemStyle: { color: anomaly[i] ? '#ff7875' : '#91cc75', borderRadius: [4, 4, 0, 0] } })), barWidth: '30%', label: { show: true, position: 'top', color: '#fff', fontSize: 9, formatter: p => p.value + '%' } }], graphic: [{ type: 'text', left: 20, bottom: 15, style: { text: '⚠ 异常区: ' + anomalyCnt + ' 个', fill: anomalyCnt > 0 ? '#ff4757' : '#67c23a', fontSize: 12, fontWeight: 'bold' } }, { type: 'text', right: 20, bottom: 15, style: { text: conclusion, fill: '#ccc', fontSize: 10 } }, { type: 'line', left: 8, top: 30, bottom: 50, shape: { x1: 0, y1: 0, x2: 0, y2: 1 }, style: { stroke: '#ff4757', lineWidth: 2 } }] }) } } catch (e) {} }

    // ========== 未来24h预测(流量+温度 拆成2个图) ==========
    async function loadNext24hForecast() {
      try {
        const res = await visualAPI.next24hForecast(selectedDate.value)
        if (!res || res.code !== 200 || !res.data || !res.data.data) return
        const data = res.data.data
        const basedOn = res.data.based_on || ''
        const hours = data.map(d => d.hour)
        const domFlow = document.getElementById('chart-next24h-flow')
        const domTemp = document.getElementById('chart-next24h-temp')
        if (charts.next24hFlow) { charts.next24hFlow.dispose(); charts.next24hFlow = null }
        if (charts.next24hTemp) { charts.next24hTemp.dispose(); charts.next24hTemp = null }
        if (domFlow) {
          const chartFlow = echarts.init(domFlow)
          charts.next24hFlow = chartFlow
          chartFlow.setOption({
            title: { text: '基于 ' + basedOn + ' 24h 数据外推', textStyle: { color: '#94a3b8', fontSize: 11 }, right: 10, top: 5 },
            tooltip: { trigger: 'axis', axisPointer: { type: 'cross' } },
            legend: { data: ['当前流量', '预测流量'], textStyle: { color: '#ccc' }, top: 28 },
            grid: { left: 55, right: 25, top: 60, bottom: 30 },
            xAxis: { type: 'category', data: hours, axisLabel: { color: '#ccc', fontSize: 9, rotate: 35 } },
            yAxis: { type: 'value', name: '流量(辆/h)', nameTextStyle: { color: '#409eff', fontSize: 10 }, axisLabel: { color: '#409eff' } },
            series: [
              { name: '当前流量', type: 'line', data: data.map(d => d.flow_now), smooth: true, lineStyle: { color: '#409eff', width: 3 }, itemStyle: { color: '#409eff' }, symbol: 'circle', symbolSize: 6, areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: 'rgba(64,158,255,0.3)' }, { offset: 1, color: 'rgba(64,158,255,0.02)' }] } } },
              { name: '预测流量', type: 'line', data: data.map(d => d.flow_pred), smooth: true, lineStyle: { color: '#409eff', width: 2, type: 'dashed' }, itemStyle: { color: '#409eff' }, symbol: 'diamond', symbolSize: 5 }
            ]
          })
        }
        if (domTemp) {
          const chartTemp = echarts.init(domTemp)
          charts.next24hTemp = chartTemp
          chartTemp.setOption({
            title: { text: '基于 ' + basedOn + ' 24h 数据外推', textStyle: { color: '#94a3b8', fontSize: 11 }, right: 10, top: 5 },
            tooltip: { trigger: 'axis', axisPointer: { type: 'cross' } },
            legend: { data: ['当前温度', '预测温度'], textStyle: { color: '#ccc' }, top: 28 },
            grid: { left: 50, right: 25, top: 60, bottom: 30 },
            xAxis: { type: 'category', data: hours, axisLabel: { color: '#ccc', fontSize: 9, rotate: 35 } },
            yAxis: { type: 'value', name: '温度(°C)', nameTextStyle: { color: '#f56c6c', fontSize: 10 }, axisLabel: { color: '#f56c6c' } },
            series: [
              { name: '当前温度', type: 'line', data: data.map(d => d.temp_now), smooth: true, lineStyle: { color: '#f56c6c', width: 3 }, itemStyle: { color: '#f56c6c' }, symbol: 'circle', symbolSize: 6, areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: 'rgba(245,108,108,0.3)' }, { offset: 1, color: 'rgba(245,108,108,0.02)' }] } } },
              { name: '预测温度', type: 'line', data: data.map(d => d.temp_pred), smooth: true, lineStyle: { color: '#f56c6c', width: 2, type: 'dashed' }, itemStyle: { color: '#f56c6c' }, symbol: 'diamond', symbolSize: 5 }
            ]
          })
        }
      } catch (e) { console.error('未来24h预测加载失败:', e) }
    }

    function onChartDrill(type) {
      if (type === 'trafficFlow') { drillTitle.value = '24h交通流量'; drillCol1.value = '车流量'; drillCol2.value = '拥堵指数'; drillCol3.value = '平均速度'; drillData.value = (trafficFlowRaw.value || []).map(d => ({ district: d.hour, value1: d.flow, value2: d.congestion, value3: d.speed })) }
      else if (type === 'trafficAnomaly') { drillTitle.value = '交通异常'; drillCol1.value = '事故数'; drillCol2.value = '拥堵指数'; drillCol3.value = '异常标记'; drillData.value = (trafficAnomalyRaw.value || []).map(d => ({ district: d.district, value1: d.accidents, value2: d.congestion, value3: d.anomaly ? '是' : '否' })) }
      else if (type === 'opinionAnomaly') { drillTitle.value = '舆情异常'; drillCol1.value = '热度指数'; drillCol2.value = '负面比例'; drillCol3.value = '正面比例'; drillData.value = (opinionAnomalyRaw.value || []).map(d => ({ district: d.district, value1: d.hot_index, value2: d.negative_ratio, value3: d.positive_ratio })) }
      drillVisible.value = true
    }

    // ========== 大屏综合加载 ==========
    async function loadStreamTop() { try { const res = await streamingAPI.recent(5); if (res && res.code === 200 && res.data && res.data.data && res.data.data.length) { const list = res.data.data.slice(0, 5); const dom = document.getElementById('chart-stream-top'); if (!dom) return; if (charts.streamTop) charts.streamTop.dispose(); const chart = echarts.init(dom); charts.streamTop = chart; const getIdx = r => { const c = r.computed || {}; if (typeof c.congestionIndex === 'number') return c.congestionIndex / 100; if (typeof c.congestion === 'number') return c.congestion / 100; if (typeof c.hotness === 'number') return c.hotness / 100; if (typeof c.density === 'number') return c.density / 30000; if (typeof c.amount === 'number') return c.amount / 10000; return 0 }; const getMetric = r => { const c = r.computed || {}; if (typeof c.congestionIndex === 'number') return '拥堵指数 ' + c.congestionIndex; if (typeof c.congestion === 'number') return '拥堵 ' + c.congestion; if (typeof c.hotness === 'number') return '热度 ' + c.hotness; if (typeof c.density === 'number') return '密度 ' + c.density; if (typeof c.amount === 'number') return '消费额 ' + c.amount; return '数据'; }; chart.setOption({ title: { text: '最新 5 条实时流', textStyle: { color: '#00d4ff', fontSize: 13 }, left: 'center', top: 3 }, tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, formatter: p => { const i = p[0].dataIndex; const r = list[i]; const idx = getIdx(r); return '<b>' + (r.district || '未知') + ' · ' + (r.dataType || '-') + '</b><br/>数据时间: ' + (r.ingestTime || r.timestamp || '-') + '<br/>' + getMetric(r) + '<br/>相对值(0~1): <b>' + idx.toFixed(2) + '</b><br/>告警: ' + (r.alert || '-') } }, grid: { left: 130, right: 60, top: 40, bottom: 20, containLabel: true }, xAxis: { type: 'value', name: '相对值', min: 0, max: 1, nameTextStyle: { color: '#8899aa', fontSize: 10 }, axisLabel: { color: '#8899aa' } }, yAxis: { type: 'category', data: list.map((r, i) => '#' + (i + 1) + ' ' + (r.district || '未知')), axisLabel: { color: '#ccc', fontSize: 10 } }, series: [{ type: 'bar', data: list.map(r => { const v = getIdx(r); return { value: v, itemStyle: { color: v >= 0.7 ? '#f56c6c' : (v >= 0.4 ? '#e6a23c' : '#67c23a') } }; }), label: { show: true, position: 'right', color: '#fff', fontSize: 10, formatter: p => p.value.toFixed(2) }, barWidth: '55%', itemStyle: { borderRadius: [0, 4, 4, 0] } }] }) } else if (res && res.code === 200 && res.data && res.data.data) { const dom = document.getElementById('chart-stream-top'); if (!dom) return; if (charts.streamTop) charts.streamTop.dispose(); const chart = echarts.init(dom); charts.streamTop = chart; chart.setOption({ title: { text: '暂无实时流数据', textStyle: { color: '#999', fontSize: 14 }, left: 'center', top: 'middle' } }) } } catch (e) {} }

    async function loadAllCharts() {
      dashboardLoaded.value = true
      dashboardStatus.value = { icon: '⏳', text: '正在加载数据...', color: '#e6a23c' }
      const date = selectedDate.value
      const today = new Date().toISOString().slice(0, 10) // yyyy-MM-dd
      const token = localStorage.getItem('token')
      const headers = token ? { Authorization: 'Bearer ' + token } : {}
      try {
        const url = '/api/streaming/dashboard?date=' + encodeURIComponent(date)
        const r = await fetch(url, { headers })
        if (!r.ok) {
          console.error('[大屏] HTTP错误', r.status, r.statusText)
          dashboardStatus.value = { icon: '❌', text: '后端未启动(端口8088)，请先启动Spring Boot后端', color: '#f56c6c' }
          return
        }
        const j = await r.json()
        if (j.code !== 200) {
          console.error('[大屏] 后端返回错误', j.code, j.message)
          dashboardStatus.value = { icon: '❌', text: '后端错误: ' + (j.message || '未知'), color: '#f56c6c' }
          return
        }
        if (!j.data || Object.keys(j.data).length === 0) {
          console.warn('[大屏] 后端返回空数据 (streaming_results 可能为空)')
          dashboardStatus.value = { icon: '⚠️', text: '数据源为空，请执行数据采集或等待实时流生成', color: '#e6a23c' }
          applyDashboardData(j.data || {})
        } else {
          dashboardStatus.value = { icon: '✅', text: '数据已加载 (' + ((j.data.stats && j.data.stats.total) || '?') + ' 条)', color: '#67c23a' }
          applyDashboardData(j.data)
        }
      } catch (e) {
        console.error('[大屏] 请求失败:', e.message || e)
        dashboardStatus.value = { icon: '❌', text: '网络请求失败: ' + (e.message || e), color: '#f56c6c' }
      }
      // 仅当 selectedDate == 今天时, 开启轮询; 历史日期是一张静态快照, 无需再刷新
      if (date === today) startStreamPolling()
      else stopStreamPolling()
      loadUrgentAlerts()
      loadLifeAdvice()
    }
    // ---------- 把后端 dashboard 接口返回的 JSON 一次性应用到 UI ----------
    function applyDashboardData(data) {
      if (!data) return
      // 1) KPI
      if (data.stats) {
        statCards[0].value = data.stats.traffic || 0
        statCards[1].value = data.stats.weather || 0
        statCards[2].value = data.stats.opinion || 0
        statCards[3].value = data.stats.consumption || 0
        statCards[4].value = data.stats.population || 0
        statCards[5].value = data.stats.total || 0
      }
      // 2) 拥堵等级饼图
      if (data.congestionPie) initChart('chart-congestion', {
        tooltip: { trigger: 'item' },
        legend: { bottom: 0, textStyle: { color: '#8899aa' } },
        series: [{ type: 'pie', radius: ['35%', '65%'], center: ['50%', '45%'], label: { color: '#ccc' }, data: data.congestionPie }]
      })
      // 3) 事故风险
      if (data.accidentRisk && data.accidentRisk.length) {
        const dom = document.getElementById('chart-accident')
        if (dom) {
          if (charts.accident) charts.accident.dispose()
          const chart = echarts.init(dom); charts.accident = chart
          const colorMap = { '高风险': '#f56c6c', '中风险': '#e6a23c', '低风险': '#67c23a' }
          chart.setOption({
            tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' },
              formatter: ps => { const i = ps[0].dataIndex; return data.accidentRisk[i].district + '<br/>事故数: <b>' + data.accidentRisk[i].accidents + '</b><br/>风险等级: <b style="color:' + colorMap[data.accidentRisk[i].risk] + '">' + data.accidentRisk[i].risk + '</b>' } },
            legend: { data: ['低风险', '中风险', '高风险'], textStyle: { color: '#ccc' }, top: 5 },
            grid: { left: 70, right: 30, top: 45, bottom: 30 },
            xAxis: { type: 'value', name: '事故数', axisLabel: { color: '#ccc' } },
            yAxis: { type: 'category', data: data.accidentRisk.map(d => d.district), axisLabel: { color: '#ccc', fontSize: 11 } },
            series: [{ name: '事故数', type: 'bar', data: data.accidentRisk.map(d => ({ value: d.accidents, itemStyle: { color: colorMap[d.risk] } })), label: { show: true, position: 'right', color: '#fff', formatter: p => p.value + '起' }, itemStyle: { borderRadius: [0, 4, 4, 0] } }]
          })
        }
      }
      // 4) 24h 流量 + 拥堵
      if (data.traffic24h && data.traffic24h.length) {
        trafficFlowRaw.value = data.traffic24h
        const dom = document.getElementById('chart-traffic-flow')
        if (dom) {
          if (charts.flow) charts.flow.dispose()
          const chart = echarts.init(dom); charts.flow = chart
          chart.setOption({
            tooltip: { trigger: 'axis' },
            legend: { data: ['车流量', '拥堵指数'], textStyle: { color: '#ccc' }, top: 5 },
            grid: { left: 55, right: 55, top: 40, bottom: 40 },
            xAxis: { type: 'category', data: data.traffic24h.map(d => d.hour + ':00'), axisLabel: { color: '#ccc', fontSize: 9, rotate: 45 } },
            yAxis: [{ type: 'value', name: '车流量(辆)', nameTextStyle: { color: '#ccc' }, axisLabel: { color: '#ccc', formatter: v => v >= 1000000 ? (v / 1000000).toFixed(1) + 'M' : (v >= 1000 ? (v / 1000).toFixed(0) + 'k' : v) } }, { type: 'value', name: '拥堵指数', min: 0, max: 1, nameTextStyle: { color: '#ccc' }, axisLabel: { color: '#ccc' } }],
            series: [
              { name: '车流量', type: 'bar', data: data.traffic24h.map(d => d.flow), itemStyle: { color: '#5470c6', borderRadius: [4, 4, 0, 0] }, label: { show: true, position: 'top', color: '#fff', fontSize: 9, formatter: p => p.value >= 1000 ? Math.round(p.value / 100) / 10 + 'k' : p.value } },
              { name: '拥堵指数', type: 'line', yAxisIndex: 1, data: data.traffic24h.map(d => d.congestion), smooth: true, symbol: 'circle', symbolSize: 6, lineStyle: { color: '#ee6666', width: 3 }, itemStyle: { color: '#ee6666' }, markLine: { silent: true, data: [{ yAxis: 0.7, name: '警戒线', lineStyle: { color: '#ff4757', type: 'dashed' } }] } }
            ]
          })
        }
      }
      // 5) 舆情情感仪表盘
      if (data.opinionGauge) {
        const dom = document.getElementById('chart-opinion')
        if (dom) {
          if (charts.opinion) charts.opinion.dispose()
          const chart = echarts.init(dom); charts.opinion = chart
        const g = data.opinionGauge
        const total = (g.positive_count || 0) + (g.neutral_count || 0) + (g.negative_count || 0)
        const hourly = Array.isArray(g.hourly_positive) ? g.hourly_positive : new Array(24).fill(0)
        const firstAvg = hourly.slice(0, 12).filter(v => v > 0)
        const secondAvg = hourly.slice(12).filter(v => v > 0)
        const trend = secondAvg.length && firstAvg.length ? (secondAvg.reduce((a, b) => a + b, 0) / secondAvg.length) - (firstAvg.reduce((a, b) => a + b, 0) / firstAvg.length) : 0
        const trendText = trend > 5 ? '上升 ' + Math.round(trend) + '%' : (trend < -5 ? '下降 ' + Math.round(-trend) + '%' : '持平')
        const trendColor = trend > 5 ? '#67c23a' : (trend < -5 ? '#f56c6c' : '#e6a23c')
        const ratio = g.positive_ratio || 0
        const conclusion = ratio >= 60 ? '舆情健康, 正面情绪占主导' : (ratio >= 40 ? '舆情中性, 需关注潜在负面' : '舆情偏负面, 建议加强引导')
        const posColor = ratio >= 60 ? '#67c23a' : (ratio >= 40 ? '#e6a23c' : '#f56c6c')
        chart.setOption({
          title: { text: '今日舆情: 正面 ' + ratio + '% | 共 ' + total + ' 条', left: 'center', top: 8, textStyle: { color: posColor, fontSize: 14, fontWeight: 'bold' } },
          tooltip: { trigger: 'item', formatter: '{b}: {c} 条 ({d}%)' },
          legend: { bottom: 10, textStyle: { color: '#8899aa' } },
          series: [{ name: '情感构成', type: 'pie', radius: ['45%', '70%'], center: ['50%', '52%'], avoidLabelOverlap: true, label: { show: true, formatter: '{b}\n{d}%', color: '#ccc', fontSize: 12 }, labelLine: { lineStyle: { color: '#aaa' } }, data: [{ name: '正面', value: g.positive_count || 0, itemStyle: { color: '#67c23a' } }, { name: '中性', value: g.neutral_count || 0, itemStyle: { color: '#909399' } }, { name: '负面', value: g.negative_count || 0, itemStyle: { color: '#f56c6c' } }] }]
        })
        }
      }
      // 6) 舆情热点异常
      if (data.opinionAnomaly && data.opinionAnomaly.length) {
        opinionAnomalyRaw.value = data.opinionAnomaly
        const dom = document.getElementById('chart-opinion-anomaly')
        if (dom) {
          if (charts.opinionAnomaly) charts.opinionAnomaly.dispose()
          const chart = echarts.init(dom); charts.opinionAnomaly = chart
          const anomaly = data.opinionAnomaly.map(d => d.anomaly)
          const anomalyCnt = anomaly.filter(Boolean).length
          const topIdx = data.opinionAnomaly.reduce((bestIdx, cur, idx, arr) => (cur.negative_ratio > arr[bestIdx].negative_ratio ? idx : bestIdx), 0)
          const topDistrict = data.opinionAnomaly[topIdx].district
          const topNeg = data.opinionAnomaly[topIdx].negative_ratio
          const conclusion = anomalyCnt > 0 ? ('共发现 ' + anomalyCnt + ' 个异常区域, 其中 ' + topDistrict + ' 负面率最高 ' + topNeg + '%') : '所有区域舆情正常, 未发现异常'
          const fmt = n => n >= 10000 ? (n / 10000).toFixed(1) + '万' : (n >= 1000 ? (n / 1000).toFixed(1) + 'k' : n)
          chart.setOption({
            title: [{ text: '舆情热点与异常识别', left: 'center', top: 0, textStyle: { color: '#00d4ff', fontSize: 14, fontWeight: 'bold' } }],
            tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' },
              formatter: ps => { const i = ps[0].dataIndex; const status = anomaly[i] ? '<span style="color:#ff4757;font-weight:bold">⚠ 异常</span>' : '<span style="color:#67c23a">✓ 正常</span>'; return '<b>' + data.opinionAnomaly[i].district + '</b><br/>' + ps[0].marker + ' 累计评论数: <b>' + data.opinionAnomaly[i].comment_count + ' 条</b><br/>' + ps[1].marker + ' 负面比例: <b>' + data.opinionAnomaly[i].negative_ratio + '%</b><br/>状态: ' + status } },
            legend: { data: ['累计评论数', '负面比例(%)'], textStyle: { color: '#ccc' }, top: 28 },
            grid: { left: 70, right: 60, top: 70, bottom: 70, containLabel: true },
            xAxis: { type: 'category', data: data.opinionAnomaly.map(d => d.district), axisLabel: { color: '#ccc', fontSize: 10, rotate: 25, interval: 0 } },
            yAxis: [{ type: 'value', name: '累计评论数(条)', nameTextStyle: { color: '#ccc', fontSize: 11 }, axisLabel: { color: '#ccc', formatter: v => v >= 10000 ? (v / 10000).toFixed(1) + '万' : (v >= 1000 ? (v / 1000).toFixed(0) + 'k' : v) } }, { type: 'value', name: '负面比例(%)', min: 0, max: 100, nameTextStyle: { color: '#ccc' }, axisLabel: { color: '#ccc' } }],
            series: [{ name: '累计评论数', type: 'bar', data: data.opinionAnomaly.map(d => ({ value: d.comment_count || 0, itemStyle: { color: d.anomaly ? '#ff4757' : '#5470c6', borderRadius: [4, 4, 0, 0] } })), barWidth: '30%', label: { show: true, position: 'top', color: '#fff', fontSize: 9, formatter: p => fmt(p.value) } },
              { name: '负面比例(%)', type: 'bar', yAxisIndex: 1, data: data.opinionAnomaly.map(d => ({ value: d.negative_ratio, itemStyle: { color: d.anomaly ? '#ff7875' : '#91cc75', borderRadius: [4, 4, 0, 0] } })), barWidth: '30%', label: { show: true, position: 'top', color: '#fff', fontSize: 9, formatter: p => p.value + '%' } }],
            graphic: [{ type: 'text', left: 20, bottom: 15, style: { text: '⚠ 异常区: ' + anomalyCnt + ' 个', fill: anomalyCnt > 0 ? '#ff4757' : '#67c23a', fontSize: 12, fontWeight: 'bold' } }, { type: 'text', right: 20, bottom: 15, style: { text: conclusion, fill: '#ccc', fontSize: 10 } }]
          })
        }
      }
      // 7) 人口密度 TOP5 / 区域事件 TOP3
      if (data.populationTop3 && data.populationTop3.length) {
        const dom = document.getElementById('chart-population')
        if (dom) {
          if (charts.population) charts.population.dispose()
          const chart = echarts.init(dom); charts.population = chart
          chart.setOption({
            title: { text: '区域事件 TOP5', textStyle: { color: '#00d4ff', fontSize: 13 }, left: 'center', top: 3 },
            tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, formatter: p => { const i = p[0].dataIndex; return data.populationTop3[i].district + '<br/>今日事件: <b>' + data.populationTop3[i].density + '</b> 起<br/>累计: <b>' + data.populationTop3[i].resident + '</b> 起' } },
            grid: { left: 80, right: 60, top: 45, bottom: 20, containLabel: true },
            xAxis: { type: 'value', name: '事件数', nameTextStyle: { color: '#8899aa', fontSize: 10 }, axisLabel: { color: '#8899aa' } },
            yAxis: { type: 'category', data: data.populationTop3.map(d => d.district), axisLabel: { color: '#ccc', fontSize: 11 } },
            series: [{ type: 'bar', data: data.populationTop3.map(d => ({ value: d.density, itemStyle: { color: { type: 'linear', x: 0, y: 0, x2: 1, y2: 0, colorStops: [{ offset: 0, color: '#3b82f6' }, { offset: 1, color: '#22d3ee' }] } } })), label: { show: true, position: 'right', color: '#fff', fontSize: 10, formatter: p => p.value + '起' }, barWidth: '50%', itemStyle: { borderRadius: [0, 4, 4, 0] } }]
          })
        }
      }
      // 8) 24h × 各区 消费热力图 (X=小时 0~23, Y=上海市各区) — 不论后端返回什么字段都按这个渲染
      {
        const dom = document.getElementById('chart-consumption')
        if (dom) {
          if (charts.consumption) charts.consumption.dispose()
          const chart = echarts.init(dom); charts.consumption = chart
          // 各区固定 5 个(上海市)
          const districts = ['浦东新区', '徐汇区', '闵行区', '嘉定区', '宝山区']
          const allHours = Array.from({ length: 24 }, (_, h) => h + '时')
          // 优先用后端 consumptionHeat(数组格式) 真实数据, 无则用 consumptionHourData + 各区均分
          let cells = [], maxVal = 0
          const heatArr = Array.isArray(data.consumptionHeat) ? data.consumptionHeat : null
          if (heatArr && heatArr.length) {
            heatArr.forEach((d, di) => {
              (d.hourly || []).forEach((v, h) => {
                cells.push([h, di, v])
                if (v > maxVal) maxVal = v
              })
            })
          } else {
            // 用 consumptionHourData 推算: 每个区均分该小时笔数
            const hourData = data.consumptionHourData || []
            for (let di = 0; di < districts.length; di++) {
              for (let h = 0; h < 24; h++) {
                const v = hourData[h] ? Math.round((hourData[h].count || 0) / districts.length) : 0
                cells.push([h, di, v])
                if (v > maxVal) maxVal = v
              }
            }
          }
          const fmt = n => n >= 10000 ? (n / 10000).toFixed(1) + '万' : (n >= 1000 ? (n / 1000).toFixed(1) + 'k' : n)
          chart.setOption({
            title: [
              { text: '24小时 × 各区 消费交易笔数热力图', left: 'center', top: 0, textStyle: { color: '#00d4ff', fontSize: 14, fontWeight: 'bold' } },
              { text: '横轴=0~23小时  纵轴=上海市各区  格子数字=交易笔数  颜色越红=笔数越多', left: 'center', top: 26, textStyle: { color: '#94a3b8', fontSize: 11 } }
            ],
            tooltip: { position: 'top', formatter: p => '<b>' + districts[p.value[1]] + ' · ' + allHours[p.value[0]] + '</b><br/>交易笔数: <b style="color:#00d4ff">' + p.value[2] + ' 笔</b><br/>' + (p.value[2] > 0 ? '✅ 正常' : '💤 无消费') },
            grid: { left: 80, right: 30, top: 80, bottom: 60, containLabel: true },
            xAxis: { type: 'category', data: allHours, splitArea: { show: true }, axisLabel: { color: '#ccc', fontSize: 9, interval: 1 }, name: '小时', nameLocation: 'middle', nameGap: 30, nameTextStyle: { color: '#00d4ff' } },
            yAxis: { type: 'category', data: districts, splitArea: { show: true }, axisLabel: { color: '#ccc', fontSize: 11 }, name: '地区', nameTextStyle: { color: '#00d4ff' } },
            visualMap: { min: 0, max: Math.max(1, maxVal), calculable: true, orient: 'horizontal', left: 'center', bottom: 10, text: ['多', '少'], textStyle: { color: '#ccc' }, inRange: { color: ['#1e3a8a', '#3b82f6', '#22d3ee', '#fef08a', '#fb923c', '#dc2626'] } },
            series: [{ name: '消费笔数', type: 'heatmap', data: cells, label: { show: true, fontSize: 8, color: '#fff', formatter: p => p.value[2] > 0 ? fmt(p.value[2]) : '' }, itemStyle: { borderColor: '#0f172a', borderWidth: 1 } }]
          })
        }
      }
      // 9) 未来 24h 流量预测 (当前小时之前=实际流量, 之后=仅预测)
      if (data.next24hFlow && data.next24hFlow.length) {
        const dom = document.getElementById('chart-next24h-flow')
        if (dom) {
          if (charts.next24hFlow) charts.next24hFlow.dispose()
          const chart = echarts.init(dom); charts.next24hFlow = chart
          const nowH = new Date().getHours()
          chart.setOption({
            title: { text: '基于实时流 24h 数据分析', textStyle: { color: '#94a3b8', fontSize: 11 }, right: 10, top: 5 },
            tooltip: { trigger: 'axis', axisPointer: { type: 'cross' }, formatter: ps => { const h = ps[0].axisValue; const hh = parseInt(h); let tip = '<b>' + h + '</b>'; ps.forEach(p => { if (p.seriesName === '当前流量' && hh > nowH) tip += '<br/>当前流量: <span style="color:#888">暂无数据</span>'; else tip += '<br/>' + p.marker + p.seriesName + ': <b>' + (p.value != null ? p.value : '-') + '</b>' }); return tip } },
            legend: { data: ['当前流量', '预测流量'], textStyle: { color: '#ccc' }, top: 28 },
            grid: { left: 55, right: 25, top: 60, bottom: 30 },
            xAxis: { type: 'category', data: data.next24hFlow.map(d => d.hour), axisLabel: { color: '#ccc', fontSize: 9, rotate: 35 } },
            yAxis: { type: 'value', name: '流量(辆/h)', nameTextStyle: { color: '#409eff', fontSize: 10 }, axisLabel: { color: '#409eff' } },
            series: [
              { name: '当前流量', type: 'line', data: data.next24hFlow.map(d => { const hh = parseInt(d.hour); return hh > nowH ? null : d.flow_now }), connectNulls: false, smooth: true, lineStyle: { color: '#409eff', width: 3 }, itemStyle: { color: '#409eff' }, symbol: 'circle', symbolSize: 6, areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: 'rgba(64,158,255,0.3)' }, { offset: 1, color: 'rgba(64,158,255,0.02)' }] } } },
              { name: '预测流量', type: 'line', data: data.next24hFlow.map(d => d.flow_pred), smooth: true, lineStyle: { color: '#409eff', width: 2, type: 'dashed' }, itemStyle: { color: '#409eff' }, symbol: 'diamond', symbolSize: 5 }
            ]
          })
        }
      }
      // 10) 未来 24h 温度预测 (当前小时之前=实际温度, 之后=仅预测)
      if (data.next24hTemp && data.next24hTemp.length) {
        const dom = document.getElementById('chart-next24h-temp')
        if (dom) {
          if (charts.next24hTemp) charts.next24hTemp.dispose()
          const chart = echarts.init(dom); charts.next24hTemp = chart
          const nowH = new Date().getHours()
          chart.setOption({
            title: { text: '基于实时流 24h 数据分析', textStyle: { color: '#94a3b8', fontSize: 11 }, right: 10, top: 5 },
            tooltip: { trigger: 'axis', axisPointer: { type: 'cross' }, formatter: ps => { const h = ps[0].axisValue; const hh = parseInt(h); let tip = '<b>' + h + '</b>'; ps.forEach(p => { if (p.seriesName === '当前温度' && hh > nowH) tip += '<br/>当前温度: <span style="color:#888">暂无数据</span>'; else tip += '<br/>' + p.marker + p.seriesName + ': <b>' + (p.value != null ? p.value + '°C' : '-') + '</b>' }); return tip } },
            legend: { data: ['当前温度', '预测温度'], textStyle: { color: '#ccc' }, top: 28 },
            grid: { left: 50, right: 25, top: 60, bottom: 30 },
            xAxis: { type: 'category', data: data.next24hTemp.map(d => d.hour), axisLabel: { color: '#ccc', fontSize: 9, rotate: 35 } },
            yAxis: { type: 'value', name: '温度(°C)', nameTextStyle: { color: '#f56c6c', fontSize: 10 }, axisLabel: { color: '#f56c6c' } },
            series: [
              { name: '当前温度', type: 'line', data: data.next24hTemp.map(d => { const hh = parseInt(d.hour); return hh > nowH ? null : d.temp_now }), connectNulls: false, smooth: true, lineStyle: { color: '#f56c6c', width: 3 }, itemStyle: { color: '#f56c6c' }, symbol: 'circle', symbolSize: 6, areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: 'rgba(245,108,108,0.3)' }, { offset: 1, color: 'rgba(245,108,108,0.02)' }] } } },
              { name: '预测温度', type: 'line', data: data.next24hTemp.map(d => d.temp_pred), smooth: true, lineStyle: { color: '#f56c6c', width: 2, type: 'dashed' }, itemStyle: { color: '#f56c6c' }, symbol: 'diamond', symbolSize: 5 }
            ]
          })
        }
      }
      // 10.5) 区域拥堵热力图（7天×24h）
      if (data.trafficHeatmap && data.trafficHeatmap.length) {
        renderHeatmap(data.trafficHeatmap)
      }
      // 11) 分析任务概览 (饼图)
      if (data.analysisSummary && data.analysisSummary.length) {
        const dom = document.getElementById('chart-analysis')
        if (dom) {
          if (charts.analysis) charts.analysis.dispose()
          const chart = echarts.init(dom); charts.analysis = chart
          const palette = ['#5470c6', '#91cc75', '#fac858', '#ee6666', '#73c0de']
          chart.setOption({
            tooltip: { trigger: 'item', formatter: '{b}: {c}次 ({d}%)' },
            legend: { bottom: 5, textStyle: { color: '#8899aa' } },
            series: [{ type: 'pie', radius: ['40%', '65%'], data: data.analysisSummary.map((d, i) => ({ name: d.name, value: d.value, itemStyle: { color: palette[i % palette.length] } })), label: { color: '#fff', formatter: '{b}\n{d}%' } }]
          })
        }
      }
      // 12) 最新 5 条实时流
      if (data.recentTop && data.recentTop.length) {
        const dom = document.getElementById('chart-stream-top')
        if (dom) {
          if (charts.streamTop) charts.streamTop.dispose()
          const chart = echarts.init(dom); charts.streamTop = chart
          const ys = data.recentTop.map((r, i) => '#' + (i + 1) + ' ' + (r.district || '未知'))
          const getIdx = r => {
            if (typeof r.value === 'number') return Math.max(0, Math.min(1, r.value / 100))
            return 0.3
          }
          chart.setOption({
            title: { text: '最新 5 条实时流', textStyle: { color: '#00d4ff', fontSize: 13 }, left: 'center', top: 3 },
            tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, formatter: ps => { const i = ps[0].dataIndex; const r = data.recentTop[i]; return '<b>' + (r.district || '未知') + ' · ' + (r.dataType || '-') + '</b><br/>数据时间: ' + (r.timestamp || '-') + '<br/>数值: <b>' + r.value + '</b><br/>告警: ' + (r.alert || '-') } },
            grid: { left: 130, right: 60, top: 40, bottom: 20, containLabel: true },
            xAxis: { type: 'value', name: '相对值', min: 0, max: 1, nameTextStyle: { color: '#8899aa', fontSize: 10 }, axisLabel: { color: '#8899aa' } },
            yAxis: { type: 'category', data: ys, axisLabel: { color: '#ccc', fontSize: 10 } },
            series: [{ type: 'bar', data: data.recentTop.map(r => { const v = getIdx(r); return { value: v, itemStyle: { color: v >= 0.7 ? '#f56c6c' : (v >= 0.4 ? '#e6a23c' : '#67c23a') } } }), label: { show: true, position: 'right', color: '#fff', fontSize: 10, formatter: p => p.value.toFixed(2) }, barWidth: '55%', itemStyle: { borderRadius: [0, 4, 4, 0] } }]
          })
        }
      }
      // 13) 交通异常检测 (事故风险 → 按区域柱状图 + 均值基准线)
      if (data.accidentRisk && data.accidentRisk.length) {
        trafficAnomalyRaw.value = data.accidentRisk
        const dom = document.getElementById('chart-traffic-anomaly')
        if (dom) {
          if (charts.trafficAnomaly) charts.trafficAnomaly.dispose()
          const chart = echarts.init(dom); charts.trafficAnomaly = chart
          const total = data.accidentRisk.reduce((s, d) => s + (d.accidents || 0), 0)
          const avgAcc = Math.round(total / data.accidentRisk.length * 100) / 100
          chart.setOption({
            tooltip: { trigger: 'axis', formatter: ps => { const i = ps[0].dataIndex; return data.accidentRisk[i].district + '<br/>事故数: <b>' + data.accidentRisk[i].accidents + '</b><br/>风险: <b>' + data.accidentRisk[i].risk + '</b>' } },
            xAxis: { type: 'category', data: data.accidentRisk.map(d => d.district), axisLabel: { color: '#ccc', fontSize: 9, rotate: 30 } },
            yAxis: { type: 'value', name: '事故数', nameTextStyle: { color: '#ccc' }, axisLabel: { color: '#ccc' } },
            series: [{
              type: 'bar',
              data: data.accidentRisk.map(d => ({ value: d.accidents, itemStyle: { color: d.risk === '高风险' ? '#ff4757' : (d.risk === '中风险' ? '#ffa502' : '#2ed573') } })),
              markLine: { silent: true, data: [{ yAxis: avgAcc, name: '均值', lineStyle: { color: '#ffa502' }, label: { color: '#ffa502', formatter: '均值 ' + avgAcc } }] },
              label: { show: true, position: 'top', color: '#fff', fontSize: 10 }
            }]
          })
        }
      }
      // 12) 人口商圈饼图 + 出行方式（静态昨日数据）
      renderPopulationPie(data)
      renderTravelMode()
    }
    // ---------- 人口商圈饼图 & 出行方式柱状 ----------
    function renderPopulationPie(data) {
      const dom = document.getElementById('chart-population-pie')
      if (!dom) return
      if (charts.populationPie) charts.populationPie.dispose()
      const chart = echarts.init(dom); charts.populationPie = chart
      const pieData = (data && data.populationTop3 && data.populationTop3.length)
        ? data.populationTop3.map(d => ({ name: d.district, value: d.density || d.resident || 100 }))
        : [{ name: '浦东', value: 35 }, { name: '徐汇', value: 25 }, { name: '闵行', value: 20 }, { name: '嘉定', value: 12 }, { name: '宝山', value: 8 }]
      chart.setOption({
        tooltip: { trigger: 'item', formatter: '{b}: {c}起 ({d}%)' },
        legend: { bottom: 0, textStyle: { color: '#8899aa', fontSize: 10 } },
        series: [{
          type: 'pie', radius: ['45%', '72%'], center: ['50%', '43%'],
          label: { color: '#ccc', formatter: '{b}\n{d}%' },
          data: pieData,
          itemStyle: { borderRadius: 4, borderColor: '#1a1a2e', borderWidth: 2 }
        }]
      })
    }
    function renderTravelMode() {
      const dom = document.getElementById('chart-travel-mode')
      if (!dom) return
      if (charts.travelMode) charts.travelMode.dispose()
      const chart = echarts.init(dom); charts.travelMode = chart
      chart.setOption({
        tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
        grid: { left: 55, right: 25, top: 20, bottom: 35 },
        xAxis: { type: 'category', data: ['公交', '自驾', '骑行', '步行'], axisLabel: { color: '#ccc', fontSize: 11 } },
        yAxis: { type: 'value', name: '占比%', nameTextStyle: { color: '#8899aa', fontSize: 10 }, axisLabel: { color: '#8899aa' }, max: 100 },
        series: [{
          type: 'bar', barWidth: '50%',
          data: [
            { value: 42, itemStyle: { color: '#409eff' } },
            { value: 33, itemStyle: { color: '#67c23a' } },
            { value: 17, itemStyle: { color: '#e6a23c' } },
            { value: 8, itemStyle: { color: '#909399' } }
          ],
          label: { show: true, position: 'top', color: '#fff', fontSize: 11, formatter: '{c}%' },
          itemStyle: { borderRadius: [4, 4, 0, 0] }
        }]
      })
    }
    function toggleRefresh() {
      refreshEnabled.value = !refreshEnabled.value
      if (refreshEnabled.value) { ElMessage.success('自动刷新已开启，每30秒刷新'); refreshTimer = setInterval(loadAllCharts, 30000) }
      else { ElMessage.info('自动刷新已关闭'); if (refreshTimer) { clearInterval(refreshTimer); refreshTimer = null } }
    }
    function onDateChange(val) { selectedDate.value = val; loadAllCharts(); loadUrgentAlerts(); loadLifeAdvice() }
    async function showSparkStatus() { try { const res = await visualAPI.sparkStatus(); if (res && res.code === 200) { sparkStatus.value = res.data; sparkDialogVisible.value = true } } catch(e) { ElMessage.error('获取Spark状态失败') } }
    async function exportDashboard() { try { const res = await visualAPI.exportData(selectedDate.value); if (res && res.code === 200) { const blob = new Blob([JSON.stringify(res.data,null,2)],{type:'application/json'}); const url = URL.createObjectURL(blob); const a = document.createElement('a'); a.href = url; a.download = `dashboard-${selectedDate.value}.json`; a.click(); URL.revokeObjectURL(url); ElMessage.success('导出成功') } } catch(e) { ElMessage.error('导出失败') } }

    // ========== HDFS ==========
    async function loadHdfsStatus() { try { const res = await hdfsAPI.status(); if (res.code === 200 && res.data) { hdfsStatus.value = res.data; hdfsFiles.value = res.data.files || [] } } catch (e) {} }
    async function archiveResultsToHdfs() { hdfsLoading.value = true; try { const res = await hdfsAPI.archiveResults(); if (res.code === 200) { ElMessage.success(res.message || '归档成功'); loadHdfsStatus() } else ElMessage.error(res.message || '归档失败') } catch (e) { ElMessage.error('网络错误') }; hdfsLoading.value = false }
    async function archiveDataToHdfs() { hdfsLoading.value = true; try { const res = await hdfsAPI.archiveData(); if (res.code === 200) { ElMessage.success(res.message || '归档成功'); loadHdfsStatus() } else ElMessage.error(res.message || '归档失败') } catch (e) { ElMessage.error('网络错误') }; hdfsLoading.value = false }
    async function restoreFromHdfs(row) {
      try { await ElMessageBox.confirm('确定从 HDFS 恢复 [' + row.name + '] 到 MongoDB?', '恢复确认', { type: 'warning' }) } catch { return }
      hdfsLoading.value = true
      try { const collection = row.name.includes('analysis_results') ? 'analysis_results' : 'city_data'; const res = await hdfsAPI.restore(row.name, collection); if (res.code === 200) ElMessage.success(res.message || '恢复成功'); else ElMessage.error(res.message || '恢复失败') } catch (e) { ElMessage.error('网络错误') }
      hdfsLoading.value = false
    }

    // ========== 用户管理 ==========
    async function loadUsers() { if (userRole.value !== 'admin') return; try { const res = await authAPI.listUsers(); if (res.code === 200 && res.data) users.value = res.data } catch (e) {} }
    function openAddUser() { newUser.username = ''; newUser.password = ''; newUser.role = 'user'; addUserVisible.value = true }
    async function doAddUser() {
      if (!newUser.username || newUser.username.length < 3) { ElMessage.warning('用户名至少3位'); return }
      if (!newUser.password || newUser.password.length < 4) { ElMessage.warning('密码至少4位'); return }
      try { const res = await authAPI.addUser(newUser.username, newUser.password, newUser.role); if (res.code === 200) { ElMessage.success('新增成功'); addUserVisible.value = false; loadUsers() } else ElMessage.error(res.message) } catch (e) { ElMessage.error('网络错误') }
    }
    function openChangePassword(row) { pwdForm.userId = row.id; pwdForm.username = row.username; pwdForm.newPassword = ''; pwdVisible.value = true }
    async function doChangePassword() {
      if (!pwdForm.newPassword || pwdForm.newPassword.length < 4) { ElMessage.warning('密码至少4位'); return }
      try { const res = await authAPI.changePassword(pwdForm.userId, pwdForm.newPassword); if (res.code === 200) { ElMessage.success('密码已修改'); pwdVisible.value = false } else ElMessage.error(res.message) } catch (e) { ElMessage.error('网络错误') }
    }
    async function changeRole(row) {
      const newRole = row.role === 'admin' ? 'user' : 'admin'
      try { await ElMessageBox.confirm(`确定将 [${row.username}] ${newRole === 'admin' ? '升为管理员' : '降为普通用户'}？`, '提示', { type: 'warning' }) } catch { return }
      try { const res = await authAPI.updateRole(row.id, newRole); if (res.code === 200) { ElMessage.success('角色已修改'); loadUsers() } else ElMessage.error(res.message) } catch (e) { ElMessage.error('网络错误') }
    }
    async function deleteUser(row) {
      try { await ElMessageBox.confirm(`确定删除用户 [${row.username}]？此操作不可恢复！`, '危险', { type: 'error' }) } catch { return }
      try { const res = await authAPI.deleteUser(row.id); if (res.code === 200) { ElMessage.success('已删除'); loadUsers() } else ElMessage.error(res.message) } catch (e) { ElMessage.error('网络错误') }
    }
    async function loadLogs() { try { const res = await authAPI.logs(50); if (res.code === 200 && res.data) logs.value = res.data } catch (e) {} }

    // ========== 历史回溯 ==========
    function onDimensionChange() {
      if (historyForm.dimension === 'day') historyForm.dateValue = new Date().toISOString().substring(0, 10)
      else if (historyForm.dimension === 'month') historyForm.dateValue = new Date().toISOString().substring(0, 7)
      else if (historyForm.dimension === 'year') historyForm.dateValue = new Date().getFullYear().toString()
    }
    function disabledMonth(time) { const now = new Date(); return time.getFullYear() > now.getFullYear() || (time.getFullYear() === now.getFullYear() && time.getMonth() > now.getMonth()) }
    function disabledYear(time) { return time.getFullYear() > new Date().getFullYear() }
    async function queryHistory() {
      if (!historyForm.dateValue) { ElMessage.warning('请先选择时间值'); return }
      historyLoading.value = true
      try { const res = await miningAPI.history(historyForm.dimension, historyForm.dateValue, historyForm.dataType, historyForm.taskType); if (res && res.code === 200) { historyResults.value = res.data.data || []; ElMessage.success(`查询到 ${historyResults.value.length} 条`) } else ElMessage.error(res.message || '查询失败') } catch (e) { ElMessage.error('查询失败') }
      historyLoading.value = false
    }
    function exportHistory() {
      if (!historyResults.value.length) return
      const headers = ['任务ID', '数据类型', '分析类型', '分析日期', '状态', '耗时(ms)', '创建时间']
      const rows = historyResults.value.map(r => [r.taskId, dataTypeLabels[r.dataType] || r.dataType, taskTypeLabels[r.taskType] || r.taskType, r.dateStr, r.status === 'success' ? '成功' : '失败', r.duration, r.createTime])
      const csv = '\uFEFF' + [headers, ...rows].map(r => r.map(c => `"${String(c).replace(/"/g, '""')}"`).join(',')).join('\r\n')
      const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' }); const url = URL.createObjectURL(blob); const a = document.createElement('a'); a.href = url; a.download = `history_${historyForm.dimension}_${historyForm.dateValue}.csv`; a.click(); URL.revokeObjectURL(url)
      ElMessage.success('已导出 ' + historyResults.value.length + ' 条')
    }

    // ========== 监控 ==========
    const monitorLoading = ref(false)
    const collectBar = ref(null)
    let collectBarChart = null
    let monitorTimer = null
    let sparkStreamTimer = null
    function openMonitor() {
      monitorVisible.value = true
      nextTick(() => { if (!collectBarChart && collectBar.value) collectBarChart = echarts.init(collectBar.value); loadMonitor(); loadSparkStreamStatus(); startMonitorTimer(); startSparkStreamTimer() })
    }
    function startMonitorTimer() { stopMonitorTimer(); monitorTimer = setInterval(() => { if (monitorVisible.value) loadMonitor() }, 3000) }
    function stopMonitorTimer() { if (monitorTimer) { clearInterval(monitorTimer); monitorTimer = null } }
    function startSparkStreamTimer() { stopSparkStreamTimer(); sparkStreamTimer = setInterval(() => { if (monitorVisible.value) loadSparkStreamStatus() }, 1000) }
    function stopSparkStreamTimer() { if (sparkStreamTimer) { clearInterval(sparkStreamTimer); sparkStreamTimer = null } }
    watch(monitorVisible, (v) => { if (!v) { stopMonitorTimer(); stopSparkStreamTimer() } })
    async function loadMonitor() {
      monitorLoading.value = true
      try { const res = await monitorAPI.overview(); if (res && res.code === 200 && res.data) { Object.assign(monitorData, res.data); await nextTick(); if (collectBarChart) { const labels = Object.keys(monitorData.collectStats.byType || {}); const values = Object.values(monitorData.collectStats.byType || {}); collectBarChart.setOption({ tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } }, grid: { left: 60, right: 30, top: 20, bottom: 30 }, xAxis: { type: 'category', data: labels, axisLabel: { color: '#a0aec0' } }, yAxis: { type: 'value', axisLabel: { color: '#a0aec0' } }, series: [{ type: 'bar', data: values, itemStyle: { color: p => ['#00d4ff','#67c23a','#e6a23c','#f56c6c','#9b59b6'][p.dataIndex % 5] }, label: { show: true, position: 'top', color: '#a0aec0' } }] }) } } } catch (e) {}
      monitorLoading.value = false
    }

    // ========== MapReduce ==========
    async function runMapReduce() {
      mrLoading.value = true
      try { const res = await streamingAPI.mapreduce(mrForm.dataType, mrForm.dimension, mrForm.dateStr); if (res && res.code === 200) { mrResult.value = res.data; ElMessage.success(`MapReduce完成: ${res.data.totalGroups}个分组, ${res.data.totalRecords}条记录`) } else ElMessage.error(res.message || '执行失败') } catch (e) { ElMessage.error('执行失败') }
      mrLoading.value = false
    }

    // ========== 挖掘切换 ==========
    function onStepClick(step) { miningStep.value = step }
    function onMiningTypeChange() {
      miningSummary.value = { total: 0, algorithm: '', groups: 0, duration: 0, source: '' }; miningChartData.value = []; aiConclusion.value = ''; miningStep.value = 1
      if (charts.miningChart) { charts.miningChart.dispose(); delete charts.miningChart }
      nextTick(() => { const dom = document.getElementById('miningChart'); if (dom) dom.innerHTML = '' })
    }

    // ========== Tab切换 ==========
    function onTabChange(tab) {
      const name = typeof tab === 'string' ? tab : tab?.props?.name || tab?.paneName
      if (name === 'dashboard') nextTick(loadAllCharts)
      else if (name === 'collect') { loadCollectStatus(); loadSchedulerConfig() }
      else if (name === 'history') { if (historyResults.value.length === 0) queryHistory() }
      else if (name === 'mining') { loadMiningResults() }
      else if (name === 'admin' || name === 'report') { loadUsers(); loadHdfsStatus(); loadLogs() }
    }

    // ========== 生命周期 ==========
    onMounted(async () => {
      if (localStorage.getItem('token')) {
        try { const res = await authAPI.userinfo(); if (res.code === 200) { username.value = res.data.username; userRole.value = res.data.role } } catch (e) { logout() }
        const d = new Date(); selectedDate.value = `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`
        nextTick(loadAllCharts)
        startStreamPolling()
      }
      window.addEventListener('resize', () => Object.values(charts).forEach(c => c.resize()))
    })

    onUnmounted(() => {
      if (refreshTimer) { clearInterval(refreshTimer); refreshTimer = null }
      stopMonitorTimer()
      stopSparkStreamTimer()
      if (autoTimer) { clearInterval(autoTimer); autoTimer = null }
      stopStreamPolling()
      stopCollectStatusPolling()
      Object.values(charts).forEach(c => { try { c.dispose() } catch(e) {} })
    })

    return {
      username, userRole, logout,
      activeTab, onTabChange, dashboardLoaded,
      streamForm, streamConnected, streamStatusText, streamReceived, streamTypes,
      startStreamPolling, stopStreamPolling, manualReconnect, onIntervalChange,
      statCards, selectedDate, dashboardStatus, refreshEnabled, toggleRefresh, onDateChange,
      sparkStatus, sparkStreamStatus, sparkDialogVisible, showSparkStatus, exportDashboard,
      drillVisible, drillTitle, drillCol1, drillCol2, drillCol3, drillData, onChartDrill,
      collectSubTab, collectStatus, collectAllLoading,
      collectIcons, collectColors, collectBgs, collectCardStyle, statusTagType,
      manualForm, doManualCollect,
      autoForm, autoCollecting, autoCollectedCount, autoCollectedTotal, startAutoCollect, stopAutoCollect,
      schedulerConfig, schedulerForm, loadSchedulerConfig, saveSchedulerConfig,
      dataTypes, dataTypeMap, dataTypeLabels, taskTypeLabels,
      miningStep, activeMiningType, miningSelectedTask, miningLoading, miningAllLoading, miningResults,
      miningSummary, miningChartData, aiConclusion, aiConclusionLoading,
      aiSuggestions, aiSuggestionsLoading, loadSuggestions,
      urgentAlerts, loadUrgentAlerts,
      lifeAdvice, lifeAdviceLoading, loadLifeAdvice,
      advancedCollapse, miningTaskOptions,
      runMiningWithConclusion, onStepClick, onMiningTypeChange, executeAllMining,
      historyForm, historyResults, historyLoading,
      onDimensionChange, queryHistory, exportHistory,
      disabledDate, disabledMonth, disabledYear,
      monitorVisible, monitorData, monitorLoading, collectBar, openMonitor, loadMonitor,
      mrForm, mrResult, mrLoading, runMapReduce,
      hdfsStatus, hdfsFiles, hdfsLoading, loadHdfsStatus, archiveResultsToHdfs, archiveDataToHdfs, restoreFromHdfs,
      users, addUserVisible, newUser, openAddUser, doAddUser,
      pwdVisible, pwdForm, openChangePassword, doChangePassword,
      changeRole, deleteUser, loadUsers, loadLogs, logs,
      loadAllCharts
    }
  }
}
</script>

<style scoped>
#main-container { min-height: 100vh; background: #0a1929; }
.top-bar {
  display: flex; justify-content: space-between; align-items: center;
  padding: 12px 24px; background: linear-gradient(90deg, #0d2137, #132942);
  border-bottom: 1px solid rgba(0, 212, 255, 0.25);
}
.top-bar .title { font-size: 18px; font-weight: 600; color: #00d4ff; letter-spacing: 1px; }
.top-bar .user-info { display: flex; align-items: center; gap: 12px; color: #a0aec0; font-size: 14px; }
.stat-cards { margin-bottom: 12px; }
.urgent-alert-bar {
  display: flex; align-items: center; gap: 10px;
  background: linear-gradient(90deg, #7a1a1a, #c53030);
  color: #fff; padding: 8px 16px; border-radius: 6px;
  margin-bottom: 12px; box-shadow: 0 2px 8px rgba(197,48,48,0.3);
}
.urgent-alert-bar .alert-icon { font-size: 22px; animation: pulse 1.5s infinite; }
.urgent-alert-bar .alert-label { font-weight: 600; font-size: 14px; white-space: nowrap; }
.urgent-alert-bar .alert-scroll { flex: 1; min-width: 0; }
.urgent-alert-bar .alert-level-tag {
  display: inline-block; padding: 2px 10px; border-radius: 12px;
  font-size: 12px; font-weight: 600; margin-right: 6px;
}
.urgent-alert-bar .alert-level-tag.urgent { background: #feb2b2; color: #742a2a; }
.urgent-alert-bar .alert-level-tag.warning { background: #fbd38d; color: #7c2d12; }
.urgent-alert-bar .alert-level-tag.info { background: #90cdf4; color: #2a4365; }
@keyframes pulse { 0%,100%{transform:scale(1);} 50%{transform:scale(1.15);} }
.stat-value { font-size: 32px; font-weight: 700; text-align: center; }
.stat-label { font-size: 13px; color: #8899aa; text-align: center; margin-top: 4px; }
.el-card { background: rgba(15, 30, 50, 0.85) !important; border: 1px solid rgba(0, 212, 255, 0.15) !important; }
.el-card__header { color: #b0c4de; border-bottom-color: rgba(255,255,255,0.08) !important; }
.el-tabs { background: transparent; }
.el-tabs__content { padding: 16px; }
/* === 配色方案B: 青蓝主色调 (历史分析表格) === */
:deep(.el-table) { background: #0a2a4a !important; color: #e0eaf5 !important; }
:deep(.el-table th.el-table__cell) { background: #00d4ff !important; color: #fff !important; font-weight: bold; }
:deep(.el-table tr) { background: #0e3252 !important; }
:deep(.el-table tr td.el-table__cell) { background: #0e3252 !important; }
:deep(.el-table--striped .el-table__body tr.el-table__row--striped td.el-table__cell) { background: #0a2a4a !important; }
:deep(.el-table tr:hover > td) { background: #14467a !important; }
:deep(.el-table td.el-table__cell) { color: #e0eaf5 !important; border-bottom-color: rgba(0,212,255,0.15) !important; }
:deep(.el-tag) { border: 1px solid #00d4ff !important; }
</style>