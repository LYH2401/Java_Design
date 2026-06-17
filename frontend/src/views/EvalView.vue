<template>
  <div class="eval-container">
    <!-- 顶部标题栏 -->
    <div class="eval-header">
      <h2><el-icon><DataAnalysis /></el-icon> 评估面板</h2>
      <div class="header-actions">
        <el-button type="primary" @click="refreshAll" :loading="loading">
          <el-icon><Refresh /></el-icon> 刷新全部
        </el-button>
      </div>
    </div>

    <!-- ========== 统计卡片行 ========== -->
    <div class="stats-cards">
      <el-card class="stat-card" shadow="hover">
        <div class="stat-icon" style="background: var(--stat-blue-bg); color: var(--stat-blue-text);">
          <el-icon size="28"><ChatDotSquare /></el-icon>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ stats.totalEvaluations ?? '--' }}</div>
          <div class="stat-label">总评估次数</div>
        </div>
      </el-card>

      <el-card class="stat-card" shadow="hover">
        <div class="stat-icon" style="background: var(--stat-green-bg); color: var(--stat-green-text);">
          <el-icon size="28"><CircleCheck /></el-icon>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ formatPercent(stats.hitRate) }}</div>
          <div class="stat-label">RAG 命中率</div>
        </div>
      </el-card>

      <el-card class="stat-card" shadow="hover">
        <div class="stat-icon" style="background: var(--stat-orange-bg); color: var(--stat-orange-text);">
          <el-icon size="28"><Timer /></el-icon>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ stats.avgSimilarity ?? '--' }}</div>
          <div class="stat-label">平均相似度</div>
        </div>
      </el-card>

      <el-card class="stat-card" shadow="hover">
        <div class="stat-icon" style="background: var(--stat-red-bg); color: var(--stat-red-text);">
          <el-icon size="28"><Warning /></el-icon>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ stats.ragRejectedCount ?? '--' }}</div>
          <div class="stat-label">RAG 拒绝次数</div>
        </div>
      </el-card>
    </div>

    <!-- ========== 图表行 ========== -->
    <el-row :gutter="16" class="charts-row">
      <!-- Tool 调用频率饼图 -->
      <el-col :xs="24" :md="12">
        <el-card shadow="hover">
          <template #header>
            <span><el-icon><PieChart /></el-icon> Tool 调用频率</span>
          </template>
          <div ref="toolPieRef" class="chart-box"></div>
        </el-card>
      </el-col>

      <!-- Agent 意图分类柱状图 -->
      <el-col :xs="24" :md="12">
        <el-card shadow="hover">
          <template #header>
            <span><el-icon><Histogram /></el-icon> Agent 意图分类</span>
          </template>
          <div ref="intentBarRef" class="chart-box"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- ========== RAG vs Non-RAG 对比 ========== -->
    <el-card shadow="hover" class="compare-card">
      <template #header>
        <div class="compare-header-inner">
          <span><el-icon><Connection /></el-icon> RAG vs Non-RAG 对比</span>
          <el-input
            v-model="compareQuestion"
            placeholder="输入问题，如：图书馆周末开放时间？"
            class="compare-input"
            @keyup.enter="runCompare"
            clearable
          >
            <template #append>
              <el-button @click="runCompare" :loading="compareLoading" :disabled="!compareQuestion.trim()">
                对比
              </el-button>
            </template>
          </el-input>
        </div>
      </template>

      <!-- 对比结果 -->
      <div v-if="compareResult" class="compare-result">
        <!-- 性能指标 -->
        <el-row :gutter="12" class="compare-metrics">
          <el-col :span="6">
            <div class="metric-item">
              <span class="metric-label">RAG 耗时</span>
              <span class="metric-value">{{ compareResult.ragElapsedMs }}ms</span>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="metric-item">
              <span class="metric-label">Non-RAG 耗时</span>
              <span class="metric-value">{{ compareResult.nonRagElapsedMs }}ms</span>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="metric-item">
              <span class="metric-label">RAG 命中</span>
              <el-tag :type="compareResult.ragHit ? 'success' : 'danger'" size="small">
                {{ compareResult.ragHit ? '是' : '否' }}
              </el-tag>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="metric-item">
              <span class="metric-label">相似度</span>
              <span class="metric-value">{{ compareResult.topSimilarity }}</span>
            </div>
          </el-col>
        </el-row>

        <!-- 左右两栏回答对比 -->
        <el-row :gutter="16" class="answer-compare">
          <el-col :xs="24" :md="12">
            <div class="answer-panel rag-panel">
              <div class="answer-panel-header">
                <el-tag type="success" effect="dark">RAG 增强回答</el-tag>
                <el-button link size="small" @click="copyText(compareResult.ragAnswer)">复制</el-button>
              </div>
              <div class="answer-content" v-html="renderMarkdown(compareResult.ragAnswer || '')"></div>
            </div>
          </el-col>
          <el-col :xs="24" :md="12">
            <div class="answer-panel nonrag-panel">
              <div class="answer-panel-header">
                <el-tag type="info" effect="dark">纯 LLM 回答</el-tag>
                <el-button link size="small" @click="copyText(compareResult.nonRagAnswer)">复制</el-button>
              </div>
              <div class="answer-content" v-html="renderMarkdown(compareResult.nonRagAnswer || '')"></div>
            </div>
          </el-col>
        </el-row>

        <!-- 差异分析 -->
        <div v-if="compareResult.answerDiff" class="diff-analysis">
          <h4>差异分析</h4>
          <el-descriptions :column="4" border size="small">
            <el-descriptions-item label="RAG 长度">{{ compareResult.answerDiff.ragLength }}</el-descriptions-item>
            <el-descriptions-item label="Non-RAG 长度">{{ compareResult.answerDiff.nonRagLength }}</el-descriptions-item>
            <el-descriptions-item label="长度比">{{ compareResult.answerDiff.lengthRatio }}</el-descriptions-item>
            <el-descriptions-item label="Jaccard 相似度">{{ compareResult.answerDiff.jaccardSimilarity }}</el-descriptions-item>
            <el-descriptions-item label="RAG 独有词">{{ compareResult.answerDiff.ragUniqueWords }}</el-descriptions-item>
            <el-descriptions-item label="Non-RAG 独有词">{{ compareResult.answerDiff.nonRagUniqueWords }}</el-descriptions-item>
            <el-descriptions-item label="共享词">{{ compareResult.answerDiff.sharedWords }}</el-descriptions-item>
            <el-descriptions-item label="—">—</el-descriptions-item>
          </el-descriptions>
        </div>

        <!-- 检索文档 -->
        <div v-if="compareResult.retrievedDocs && compareResult.retrievedDocs.length > 0" class="retrieved-docs">
          <h4>检索到的知识文档</h4>
          <el-table :data="compareResult.retrievedDocs" size="small" stripe>
            <el-table-column prop="title" label="标题" />
            <el-table-column prop="category" label="分类" width="100" />
            <el-table-column prop="snippet" label="摘要" show-overflow-tooltip />
          </el-table>
        </div>
      </div>

      <el-empty v-else description="输入问题后点击「对比」开始 RAG vs Non-RAG 效果评估" />
    </el-card>

    <!-- ============ 报修服务统计 ============ -->
    <el-card shadow="hover" class="repair-section">
      <template #header>
        <span><el-icon><Tools /></el-icon> 校园报修服务统计</span>
      </template>

      <el-skeleton :loading="repairChartsLoading" animated :count="2">
        <el-row :gutter="16">
          <!-- 状态分布饼图 -->
          <el-col :xs="24" :md="12">
            <el-card shadow="none" class="chart-card">
              <template #header><span>报修状态分布</span></template>
              <div ref="statusPieRef" class="chart-box"></div>
            </el-card>
          </el-col>

          <!-- 紧急程度柱状图 -->
          <el-col :xs="24" :md="12">
            <el-card shadow="none" class="chart-card">
              <template #header><span>紧急程度分布</span></template>
              <div ref="urgencyBarRef" class="chart-box"></div>
            </el-card>
          </el-col>
        </el-row>

        <el-row :gutter="16" style="margin-top: 16px">
          <!-- 近30天趋势折线图 -->
          <el-col :span="24">
            <el-card shadow="none" class="chart-card">
              <template #header><span>近 30 天报修趋势</span></template>
              <div ref="trendLineRef" class="chart-box-wide"></div>
            </el-card>
          </el-col>
        </el-row>
      </el-skeleton>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick, inject, watch } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import { marked } from 'marked'
import hljs from 'highlight.js'
import { compareRagVsNonRag, getEvalStats } from '../api/eval'
import { getAgentStats, getExecutionLogs } from '../api/agent'
import { getRepairStats, getRepairTrend, getStatusDistribution, getUrgencyDistribution } from '../api/repair'

const isDark = inject('isDark')

// ==================== 状态 ====================
const loading = ref(false)
const compareLoading = ref(false)
const compareQuestion = ref('')
const compareResult = ref(null)

const stats = ref({
  totalEvaluations: 0,
  hitRate: 0,
  avgSimilarity: 0,
  ragRejectedCount: 0
})

// 图表引用
const toolPieRef = ref(null)
const intentBarRef = ref(null)
const statusPieRef = ref(null)
const urgencyBarRef = ref(null)
const trendLineRef = ref(null)
let toolPieChart = null
let intentBarChart = null
let statusPieChart = null
let urgencyBarChart = null
let trendLineChart = null
const repairChartsLoading = ref(true)

// ==================== Markdown 渲染 ====================
marked.setOptions({
  highlight: (code, lang) => {
    if (lang && hljs.getLanguage(lang)) {
      return hljs.highlight(code, { language: lang }).value
    }
    return hljs.highlightAuto(code).value
  }
})

function renderMarkdown(text) {
  if (!text) return ''
  return marked.parse(text)
}

// ==================== 数据加载 ====================
async function loadStats() {
  try {
    const res = await getEvalStats()
    // axios 拦截器已提取 res.data，res 即为 R 对象 {code, message, data}
    if (res?.data) {
      stats.value = res.data
    }
  } catch (e) {
    console.error('加载评估统计失败', e)
  }
}

async function loadToolChart() {
  try {
    const res = await getAgentStats()
    // axios 拦截器已提取 res.data，res 即为 R 对象 {code, message, data}
    const data = res?.data
    if (!data?.tools || data.tools.length === 0) return

    const names = data.tools.map(t => t.toolName)
    const values = data.tools.map(t => t.callCount)

    const option = {
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      legend: { bottom: 0, type: 'scroll' },
      series: [{
        type: 'pie',
        radius: ['40%', '70%'],
        center: ['50%', '45%'],
        avoidLabelOverlap: true,
        itemStyle: { borderRadius: 4, borderColor: isDark?.value ? '#1f1f2e' : '#fff', borderWidth: 2 },
        label: { show: true, formatter: '{b}\n{d}%' },
        emphasis: { label: { fontSize: 16, fontWeight: 'bold' } },
        data: names.map((n, i) => ({ name: n, value: values[i] }))
      }]
    }
    toolPieChart?.setOption(option, true)
  } catch (e) {
    console.error('加载 Tool 统计失败', e)
  }
}

async function loadIntentChart() {
  try {
    const res = await getExecutionLogs(1, 200)
    // axios 拦截器已提取 res.data，res 即为 R 对象 {code, message, data}
    const data = res?.data
    if (!data?.records || data.records.length === 0) return

    // 按 agentIntent 分组统计
    const intentMap = {}
    for (const log of data.records) {
      const intent = log.agentIntent || '未分类'
      intentMap[intent] = (intentMap[intent] || 0) + 1
    }

    const categories = Object.keys(intentMap)
    const counts = Object.values(intentMap)

    const option = {
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      grid: { left: '3%', right: '4%', bottom: '8%', top: '5%', containLabel: true },
      xAxis: {
        type: 'category',
        data: categories,
        axisLabel: { rotate: 20, fontSize: 11 }
      },
      yAxis: { type: 'value', name: '次数', minInterval: 1 },
      series: [{
        type: 'bar',
        data: counts,
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#409eff' },
            { offset: 1, color: '#79bbff' }
          ]),
          borderRadius: [6, 6, 0, 0]
        },
        barMaxWidth: 50,
        label: { show: true, position: 'top' }
      }]
    }
    intentBarChart?.setOption(option, true)
  } catch (e) {
    console.error('加载意图统计失败', e)
  }
}

// ==================== 报修图表 ====================
async function loadRepairCharts() {
  repairChartsLoading.value = true
  try {
    await Promise.all([loadStatusPie(), loadUrgencyBar(), loadTrendLine()])
  } finally {
    repairChartsLoading.value = false
  }
}

async function loadStatusPie() {
  try {
    const res = await getStatusDistribution()
    const data = res?.data
    if (!data) return

    const names = ['待派单', '已派单', '维修中', '已完成', '已取消']
    const keys = ['PENDING', 'ASSIGNED', 'REPAIRING', 'COMPLETED', 'CANCELLED']
    const colors = ['#909399', '#409eff', '#e6a23c', '#67c23a', '#f56c6c']
    const pieData = keys.map((k, i) => ({ name: names[i], value: data[k] || 0, itemStyle: { color: colors[i] } }))

    const option = {
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      legend: { bottom: 0 },
      series: [{
        type: 'pie', radius: ['40%', '70%'], center: ['50%', '45%'],
        label: { formatter: '{b}\n{d}%' },
        data: pieData
      }]
    }
    statusPieChart?.setOption(option, true)
  } catch (e) { console.error('加载状态分布失败', e) }
}

async function loadUrgencyBar() {
  try {
    const res = await getUrgencyDistribution()
    const data = res?.data
    if (!data) return

    const names = ['紧急', '高', '普通', '低']
    const keys = ['URGENT', 'HIGH', 'NORMAL', 'LOW']
    const values = keys.map(k => data[k] || 0)

    const option = {
      tooltip: { trigger: 'axis' },
      grid: { left: '3%', right: '4%', bottom: '8%', top: '5%', containLabel: true },
      xAxis: { type: 'category', data: names },
      yAxis: { type: 'value', minInterval: 1, name: '数量' },
      series: [{
        type: 'bar', data: values, barMaxWidth: 50,
        itemStyle: {
          borderRadius: [6, 6, 0, 0],
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#f56c6c' }, { offset: 1, color: '#fab6b6' }
          ])
        },
        label: { show: true, position: 'top' }
      }]
    }
    urgencyBarChart?.setOption(option, true)
  } catch (e) { console.error('加载紧急程度分布失败', e) }
}

async function loadTrendLine() {
  try {
    const res = await getRepairTrend(30)
    const list = res?.data || []
    const dates = list.map(d => d.date)
    const counts = list.map(d => d.count)

    const option = {
      tooltip: { trigger: 'axis' },
      grid: { left: '3%', right: '4%', bottom: '3%', top: '5%', containLabel: true },
      xAxis: { type: 'category', data: dates, axisLabel: { rotate: 30, fontSize: 10 } },
      yAxis: { type: 'value', minInterval: 1, name: '报修数' },
      series: [{
        type: 'line', data: counts, smooth: true, symbol: 'circle', symbolSize: 6,
        itemStyle: { color: '#409eff' },
        areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(64,158,255,0.25)' }, { offset: 1, color: 'rgba(64,158,255,0.02)' }
        ])}
      }]
    }
    trendLineChart?.setOption(option, true)
  } catch (e) { console.error('加载趋势失败', e) }
}

// ==================== RAG 对比 ====================
async function runCompare() {
  const q = compareQuestion.value.trim()
  if (!q) return

  compareLoading.value = true
  compareResult.value = null
  try {
    const res = await compareRagVsNonRag(q)
    // axios 拦截器已提取 res.data，res 即为 R 对象 {code, message, data}
    if (res?.data) {
      compareResult.value = res.data
      // 对比完成后刷新统计
      await loadStats()
    } else {
      ElMessage.error(res?.message || '对比失败')
    }
  } catch (e) {
    ElMessage.error('对比请求失败: ' + (e.message || '未知错误'))
    console.error(e)
  } finally {
    compareLoading.value = false
  }
}

// ==================== 工具函数 ====================
function formatPercent(val) {
  if (val == null || val === undefined) return '--'
  return (parseFloat(val) * 100).toFixed(1) + '%'
}

function copyText(text) {
  if (!text) return
  navigator.clipboard?.writeText(text).then(() => {
    ElMessage.success('已复制到剪贴板')
  }).catch(() => {
    ElMessage.warning('复制失败，请手动复制')
  })
}

async function refreshAll() {
  loading.value = true
  try {
    await Promise.all([loadStats(), loadToolChart(), loadIntentChart(), loadRepairCharts()])
  } finally {
    loading.value = false
  }
}

// ==================== 图表生命周期 ====================
function getChartTheme() {
  return isDark?.value ? 'dark' : undefined
}

function initCharts() {
  const theme = getChartTheme()
  if (toolPieRef.value) toolPieChart = echarts.init(toolPieRef.value, theme)
  if (intentBarRef.value) intentBarChart = echarts.init(intentBarRef.value, theme)
  if (statusPieRef.value) statusPieChart = echarts.init(statusPieRef.value, theme)
  if (urgencyBarRef.value) urgencyBarChart = echarts.init(urgencyBarRef.value, theme)
  if (trendLineRef.value) trendLineChart = echarts.init(trendLineRef.value, theme)
  loadToolChart()
  loadIntentChart()
  loadRepairCharts()
}

function reloadCharts() {
  disposeCharts()
  nextTick(() => initCharts())
}

watch(isDark, () => {
  reloadCharts()
})

function resizeCharts() {
  toolPieChart?.resize()
  intentBarChart?.resize()
  statusPieChart?.resize()
  urgencyBarChart?.resize()
  trendLineChart?.resize()
}

function disposeCharts() {
  toolPieChart?.dispose()
  intentBarChart?.dispose()
  statusPieChart?.dispose()
  urgencyBarChart?.dispose()
  trendLineChart?.dispose()
  toolPieChart = null
  intentBarChart = null
  statusPieChart = null
  urgencyBarChart = null
  trendLineChart = null
}

onMounted(() => {
  loadStats()
  nextTick(() => initCharts())
  window.addEventListener('resize', resizeCharts)
})

onUnmounted(() => {
  window.removeEventListener('resize', resizeCharts)
  disposeCharts()
})
</script>

<style scoped>
.eval-container {
  max-width: 1400px;
  margin: 0 auto;
  padding: 20px;
}

.eval-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.eval-header h2 {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  font-size: 22px;
  color: var(--text-primary);
}

/* ---- 统计卡片 ---- */
.stats-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}

.stat-card :deep(.el-card__body) {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
}

.stat-icon {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: var(--text-primary);
  line-height: 1.2;
}

.stat-label {
  font-size: 13px;
  color: var(--text-tertiary);
  margin-top: 4px;
}

/* ---- 图表 ---- */
.charts-row { margin-bottom: 20px; }

.chart-box { width: 100%; height: 320px; }
.chart-box-wide { width: 100%; height: 300px; }

/* ---- 对比区域 ---- */
.compare-card { margin-bottom: 20px; }

.compare-header-inner {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}

.compare-header-inner > span {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
}

.compare-input { width: 400px; }

.compare-metrics { margin-bottom: 16px; }

.metric-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 8px;
  background: var(--bg-primary);
  border-radius: 8px;
}

.metric-label {
  font-size: 12px;
  color: var(--text-tertiary);
  margin-bottom: 4px;
}

.metric-value {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.answer-compare { margin-bottom: 16px; }

.answer-panel {
  border: 1px solid var(--eval-panel-border);
  border-radius: 8px;
  overflow: hidden;
  height: 100%;
}

.answer-panel.rag-panel { border-color: var(--eval-rag-border); }
.answer-panel.nonrag-panel { border-color: var(--eval-nonrag-border); }

.answer-panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 14px;
  background: var(--eval-panel-header-bg);
  border-bottom: 1px solid var(--eval-panel-border);
}

.answer-content {
  padding: 14px;
  max-height: 400px;
  overflow-y: auto;
  font-size: 14px;
  line-height: 1.7;
  color: var(--text-primary);
}

.answer-content :deep(pre) {
  background: var(--bg-primary);
  padding: 12px;
  border-radius: 6px;
  overflow-x: auto;
}

.answer-content :deep(code) {
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 13px;
}

.answer-content :deep(table) {
  border-collapse: collapse;
  width: 100%;
}

.answer-content :deep(th),
.answer-content :deep(td) {
  border: 1px solid var(--border-color);
  padding: 6px 10px;
  text-align: left;
}

.answer-content :deep(th) { background: var(--table-header-bg); }

.diff-analysis { margin-bottom: 16px; }

.diff-analysis h4,
.retrieved-docs h4 {
  margin: 0 0 10px 0;
  font-size: 15px;
  color: var(--text-primary);
  display: flex;
  align-items: center;
  gap: 6px;
}

.retrieved-docs { margin-top: 16px; }

/* ---- 响应式 ---- */
@media (max-width: 992px) {
  .stats-cards { grid-template-columns: repeat(2, 1fr); }
  .compare-input { width: 100%; }
}

@media (max-width: 576px) {
  .stats-cards { grid-template-columns: 1fr; }
  .compare-header-inner { flex-direction: column; align-items: stretch; }
}
</style>
