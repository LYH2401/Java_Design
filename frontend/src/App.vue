<template>
  <div id="app-container" :class="{ dark: isDark }">
    <nav class="nav-bar">
      <div class="nav-left">
        <router-link to="/" class="nav-item" active-class="nav-active">
          <span>💬 校园助手</span>
        </router-link>
        <router-link to="/solver" class="nav-item" active-class="nav-active">
          <span>📝 问题求解</span>
        </router-link>
        <router-link to="/repair" class="nav-item" active-class="nav-active">
          <span>🛠️ 校园报修</span>
        </router-link>
        <router-link to="/eval" class="nav-item" active-class="nav-active">
          <span>📊 评估对比</span>
        </router-link>
      </div>
      <div class="nav-right">
        <template v-if="campusToken">
          <span class="user-name">{{ userName }}</span>
          <el-button
            size="small"
            text
            @click="handleLogout"
            class="logout-btn"
          >
            退出
          </el-button>
          <el-button
            :type="apiConfigured ? 'success' : 'warning'"
            size="small"
            @click="showApiDialog = true"
            class="api-btn"
          >
            🔑 {{ apiConfigured ? 'API 已配置' : '配置 API Key' }}
          </el-button>
        </template>
        <el-button
          size="small"
          text
          @click="toggleDarkMode"
          class="dark-toggle"
          :title="isDark ? '切换到亮色模式' : '切换到暗黑模式'"
        >
          {{ isDark ? '☀️' : '🌙' }}
        </el-button>
      </div>
    </nav>

    <el-dialog
      v-model="showApiDialog"
      title="API 配置"
      width="480px"
      :close-on-click-modal="false"
    >
      <el-form :model="apiForm" label-width="100px" label-position="top">
        <el-alert
          type="info"
          :closable="false"
          show-icon
          style="margin-bottom: 16px"
        >
          请输入您自己的 API Key，系统将使用您的 API 服务进行对话。配置仅保存在您的浏览器本地。
        </el-alert>

        <el-form-item label="API Key（必填）">
          <el-input
            v-model="apiForm.apiKey"
            type="password"
            show-password
            placeholder="sk-xxxxxxxxxxxxxxxx"
          />
        </el-form-item>

        <el-form-item label="Base URL（必填）">
          <el-input
            v-model="apiForm.baseUrl"
            placeholder="https://api.openai.com/v1"
          />
          <div class="form-tip">
            常用：OpenAI <code>https://api.openai.com/v1</code>，
            百炼 <code>https://dashscope.aliyuncs.com/compatible-mode/v1</code>，
            DeepSeek <code>https://api.deepseek.com</code>
          </div>
        </el-form-item>

        <el-form-item label="模型名称">
          <el-input
            v-model="apiForm.model"
            placeholder="gpt-4o-mini / qwen-turbo / deepseek-chat"
          />
          <div class="form-tip">留空则使用默认模型</div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="clearApiConfig" type="danger" plain>
          清除配置
        </el-button>
        <el-button @click="showApiDialog = false">取消</el-button>
        <el-button type="primary" @click="saveApiConfig" :disabled="!apiForm.apiKey.trim() || !apiForm.baseUrl.trim()">
          保存配置
        </el-button>
      </template>
    </el-dialog>

    <router-view />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, provide, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

const router = useRouter()
const showApiDialog = ref(false)
const apiConfigured = ref(false)
const isDark = ref(false)
const campusToken = ref(localStorage.getItem('campus_token'))
const campusUser = ref(null)

const apiForm = ref({
  apiKey: '',
  baseUrl: '',
  model: ''
})

const userName = computed(() => {
  if (!campusUser.value) return ''
  if (campusUser.value.username) return campusUser.value.username
  if (campusUser.value.name) return campusUser.value.name
  return '用户'
})

async function loadUserInfo() {
  if (!campusToken.value) return
  try {
    const res = await fetch('/api/auth/me', {
      headers: { 'Authorization': `Bearer ${campusToken.value}` }
    })
    if (!res.ok) throw new Error('获取用户信息失败')
    const data = await res.json()
    if (data?.data) {
      campusUser.value = data.data
      localStorage.setItem('campus_user', JSON.stringify(data.data))
    }
  } catch (_) {}
}

function handleLogout() {
  localStorage.removeItem('campus_token')
  localStorage.removeItem('campus_user')
  campusToken.value = null
  campusUser.value = null
  router.push('/login')
}

function loadApiConfig() {
  const saved = localStorage.getItem('campus_api_config')
  if (saved) {
    try {
      const config = JSON.parse(saved)
      apiForm.value = config
      apiConfigured.value = !!(config.apiKey && config.baseUrl)
    } catch (_) {}
  }
}

function saveApiConfig() {
  const config = {
    apiKey: apiForm.value.apiKey.trim(),
    baseUrl: apiForm.value.baseUrl.trim().replace(/\/+$/, ''),
    model: apiForm.value.model.trim()
  }
  if (!config.apiKey || !config.baseUrl) {
    ElMessage.warning('请填写 API Key 和 Base URL')
    return
  }
  localStorage.setItem('campus_api_config', JSON.stringify(config))
  apiConfigured.value = true
  showApiDialog.value = false
  ElMessage.success('API 配置已保存')
}

function clearApiConfig() {
  localStorage.removeItem('campus_api_config')
  apiForm.value = { apiKey: '', baseUrl: '', model: '' }
  apiConfigured.value = false
  showApiDialog.value = false
  ElMessage.info('API 配置已清除')
}

function toggleDarkMode() {
  isDark.value = !isDark.value
  localStorage.setItem('campus_dark_mode', isDark.value ? '1' : '0')
  document.documentElement.classList.toggle('dark', isDark.value)
}

function loadDarkMode() {
  const saved = localStorage.getItem('campus_dark_mode')
  if (saved === '1') {
    isDark.value = true
    document.documentElement.classList.add('dark')
  }
}

// Watch for token changes from other tabs or manual set
watch(campusToken, (val) => {
  if (!val) {
    campusUser.value = null
  } else {
    loadUserInfo()
  }
})

provide('apiConfig', apiForm)
provide('apiConfigured', apiConfigured)
provide('isDark', isDark)
function openApi() {
  showApiDialog.value = true
}
provide('openApiDialog', openApi)

onMounted(() => {
  loadApiConfig()
  loadDarkMode()
  if (campusToken.value) {
    loadUserInfo()
  }
})
</script>

<style>
:root {
  --bg-primary: #f5f7fa;
  --bg-secondary: #ffffff;
  --bg-hover: #f0f2f5;
  --bg-active: #ecf5ff;
  --text-primary: #303133;
  --text-secondary: #606266;
  --text-tertiary: #909399;
  --border-color: #e4e7ed;
  --border-light: #eeeeee;
  --brand-color: #409eff;
  --shadow-light: 0 1px 3px rgba(0,0,0,0.08);
  --code-bg: rgba(0,0,0,0.06);
  --table-border: #dddddd;
  --table-header-bg: #f5f7fa;
  --api-card-bg-start: #fff7e6;
  --api-card-bg-end: #fff3d6;
  --api-card-border: #e6a23c;
  --api-card-text: #8b6914;
  --api-card-hover-bg-start: #fff3d6;
  --api-card-hover-bg-end: #ffecd0;
  --api-card-hover-border: #d48806;
  --tool-hint-bg: #fdf6ec;
  --tool-hint-color: #e6a23c;
  --repair-card-bg: #fef9e7;
  --repair-card-border: #e6a23c;
  --welcome-text: #909399;
  --sidebar-bg: #ffffff;
  --chat-bg: #f5f7fa;
  --input-bg: #ffffff;
  --header-bg: #ffffff;
  --assistant-msg-bg: #ffffff;
  --assistant-msg-text: #303133;
  --user-msg-bg: #409eff;
  --user-msg-text: #ffffff;
  --blockquote-border: #409eff;
  --blockquote-color: #606266;
  --nav-bg: #ffffff;
  --nav-item-color: #606266;
  --nav-item-hover-bg: #f0f2f5;
  --nav-item-hover-color: #303133;
  --nav-active-bg: #ecf5ff;
  --nav-active-color: #409eff;
  --form-tip-color: #909399;
  --form-tip-code-bg: #f5f7fa;
  --source-label-color: #909399;
  --solver-accent: #16a34a;
  --solver-bg: #f0fdf4;
  --solver-accent-light: #dcfce7;
  --stat-blue-bg: #ecf5ff;
  --stat-orange-bg: #fdf6ec;
  --stat-green-bg: #f0f9eb;
  --stat-yellow-bg: #fef9e7;
  --stat-blue-text: #409eff;
  --stat-orange-text: #e6a23c;
  --stat-green-text: #67c23a;
  --stat-yellow-text: #e6a23c;
  --eval-panel-border: #ebeef5;
  --eval-panel-header-bg: #fafafa;
  --eval-rag-border: #b3e19d;
  --eval-nonrag-border: #d3d6db;
  --stat-red-bg: #fef0f0;
  --stat-red-text: #f56c6c;
}

.dark {
  --bg-primary: #0f0f1a;
  --bg-secondary: #1a1a2e;
  --bg-hover: #252540;
  --bg-active: #1e3050;
  --text-primary: #e0e0e0;
  --text-secondary: #b0b0b8;
  --text-tertiary: #707080;
  --border-color: #2a2a40;
  --border-light: #2a2a40;
  --brand-color: #5b9cf5;
  --shadow-light: 0 1px 3px rgba(0,0,0,0.3);
  --code-bg: rgba(255,255,255,0.08);
  --table-border: #3a3a50;
  --table-header-bg: #1e1e35;
  --api-card-bg-start: #2a2010;
  --api-card-bg-end: #251c08;
  --api-card-border: #5a4200;
  --api-card-text: #d4a810;
  --api-card-hover-bg-start: #352a10;
  --api-card-hover-bg-end: #302408;
  --api-card-hover-border: #8a6200;
  --tool-hint-bg: #2a2515;
  --tool-hint-color: #d4a810;
  --repair-card-bg: #2a2215;
  --repair-card-border: #8a6200;
  --welcome-text: #808090;
  --sidebar-bg: #1a1a2e;
  --chat-bg: #0f0f1a;
  --input-bg: #1a1a2e;
  --header-bg: #1a1a2e;
  --assistant-msg-bg: #1a1a2e;
  --assistant-msg-text: #e0e0e0;
  --user-msg-bg: #3b6fc7;
  --user-msg-text: #ffffff;
  --blockquote-border: #5b9cf5;
  --blockquote-color: #b0b0b8;
  --nav-bg: #1a1a2e;
  --nav-item-color: #b0b0b8;
  --nav-item-hover-bg: #252540;
  --nav-item-hover-color: #e0e0e0;
  --nav-active-bg: #1e3050;
  --nav-active-color: #5b9cf5;
  --form-tip-color: #707080;
  --form-tip-code-bg: #252540;
  --source-label-color: #707080;
  --solver-accent: #22c55e;
  --solver-bg: #0a1a10;
  --solver-accent-light: #1a4020;
  --stat-blue-bg: #1a3050;
  --stat-orange-bg: #2a2010;
  --stat-green-bg: #1a2a1a;
  --stat-yellow-bg: #2a2210;
  --stat-blue-text: #5b9cf5;
  --stat-orange-text: #d4a810;
  --stat-green-text: #52c41a;
  --stat-yellow-text: #d4a810;
  --eval-panel-border: #2a2a40;
  --eval-panel-header-bg: #1e1e35;
  --eval-rag-border: #2a4a2a;
  --eval-nonrag-border: #3a3a50;
  --stat-red-bg: #301a1a;
  --stat-red-text: #f56c6c;
}

* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html, body, #app, #app-container {
  height: 100%;
  width: 100%;
  overflow: hidden;
  font-family: 'Helvetica Neue', Helvetica, 'PingFang SC', 'Microsoft YaHei', Arial, sans-serif;
}

#app-container {
  background: var(--bg-primary);
  color: var(--text-primary);
  transition: background 0.3s, color 0.3s;
}

.nav-bar {
  display: flex;
  background: var(--nav-bg);
  border-bottom: 1px solid var(--border-color);
  padding: 0 16px;
  height: 44px;
  align-items: center;
  justify-content: space-between;
  z-index: 100;
  position: relative;
  transition: background 0.3s, border-color 0.3s;
}

.nav-left {
  display: flex;
  align-items: center;
  gap: 4px;
}

.nav-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.user-name {
  font-size: 13px;
  color: var(--text-secondary);
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.logout-btn {
  font-size: 12px !important;
  color: var(--text-tertiary) !important;
}

.logout-btn:hover {
  color: #f56c6c !important;
}

.dark-toggle {
  font-size: 16px;
  padding: 4px 8px !important;
  min-width: auto !important;
}

.api-btn {
  font-weight: 600;
  animation: apiPulse 2s ease-in-out infinite;
}

@keyframes apiPulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.7; }
}

.nav-item {
  padding: 8px 16px;
  border-radius: 8px;
  text-decoration: none;
  color: var(--nav-item-color);
  font-size: 14px;
  font-weight: 500;
  transition: all 0.2s;
  display: flex;
  align-items: center;
  gap: 6px;
}

.nav-item:hover {
  background: var(--nav-item-hover-bg);
  color: var(--nav-item-hover-color);
}

.nav-active {
  background: var(--nav-active-bg);
  color: var(--nav-active-color);
}

.form-tip {
  font-size: 12px;
  color: var(--form-tip-color);
  margin-top: 4px;
  line-height: 1.5;
}

.form-tip code {
  background: var(--form-tip-code-bg);
  padding: 1px 5px;
  border-radius: 3px;
  font-size: 11px;
}

/* 暗黑模式 Element Plus 组件覆盖 */
#app-container.dark .el-button--default.is-plain {
  --el-button-bg-color: transparent;
  --el-button-border-color: #404060;
  --el-button-text-color: #c0c0d0;
  --el-button-hover-bg-color: #252540;
  --el-button-hover-border-color: #5b9cf5;
  --el-button-hover-text-color: #5b9cf5;
}

#app-container.dark .el-button--primary.is-plain {
  --el-button-bg-color: transparent;
  --el-button-border-color: #2a5080;
  --el-button-text-color: #6aacf5;
}

#app-container.dark .el-dialog {
  --el-dialog-bg-color: #1a1a2e;
  --el-dialog-title-font-size: 18px;
}

#app-container.dark .el-textarea__inner {
  background: #252540;
  border-color: #3a3a50;
  color: #e0e0e0;
}

#app-container.dark .el-textarea__inner::placeholder {
  color: #606080;
}

#app-container.dark .el-card {
  --el-card-bg-color: #1a1a2e;
  --el-card-border-color: #2a2a40;
}

#app-container.dark .el-table {
  --el-table-bg-color: #1a1a2e;
  --el-table-tr-bg-color: #1a1a2e;
  --el-table-header-bg-color: #252540;
  --el-table-border-color: #2a2a40;
  --el-table-row-hover-bg-color: #252540;
  --el-table-text-color: #e0e0e0;
  --el-table-header-text-color: #c0c0d0;
}

#app-container.dark .el-table th.el-table__cell {
  background-color: #252540;
}

#app-container.dark .el-table td.el-table__cell {
  background-color: #1a1a2e;
}

#app-container.dark .el-table--striped .el-table__body tr.el-table__row--striped td.el-table__cell {
  background-color: #1e1e35;
}

#app-container.dark .el-pagination {
  --el-pagination-bg-color: #1a1a2e;
  --el-pagination-text-color: #c0c0d0;
  --el-pagination-button-bg-color: #252540;
}

#app-container.dark .el-select-dropdown,
#app-container.dark .el-dropdown__popper {
  --el-bg-color-overlay: #1a1a2e;
  --el-border-color-light: #2a2a40;
}

#app-container.dark .el-select-dropdown__item:hover,
#app-container.dark .el-dropdown-menu__item:hover {
  background-color: #252540;
}

#app-container.dark .el-tabs__header {
  border-bottom-color: #2a2a40;
}

#app-container.dark .el-tabs__item {
  color: #8080a0;
}

#app-container.dark .el-tabs__item.is-active {
  color: #5b9cf5;
}

#app-container.dark .el-descriptions {
  --el-descriptions-item-bordered-label-background: #252540;
}

#app-container.dark .el-timeline-item__node {
  background-color: #3a3a50;
}

#app-container.dark .el-timeline-item__tail {
  border-left-color: #3a3a50;
}

#app-container.dark .el-collapse-item__header {
  background-color: #1a1a2e;
  border-bottom-color: #2a2a40;
  color: #e0e0e0;
}

#app-container.dark .el-collapse-item__wrap {
  background-color: #1a1a2e;
  border-bottom-color: #2a2a40;
}

#app-container.dark .el-collapse-item__content {
  color: #c0c0d0;
}

#app-container.dark .el-empty__description p {
  color: #707080;
}

#app-container.dark .el-image-viewer__close {
  color: #c0c0d0;
  background-color: #1a1a2e;
}

#app-container.dark .el-rate__icon {
  color: #3a3a50;
}

#app-container.dark .el-form-item__label {
  color: #c0c0d0;
}

#app-container.dark .el-tag--info {
  --el-tag-bg-color: #252540;
  --el-tag-border-color: #3a3a50;
  --el-tag-text-color: #a0a0b0;
}

#app-container.dark .el-input__wrapper {
  background-color: #252540;
  box-shadow: 0 0 0 1px #3a3a50 inset;
}

#app-container.dark .el-input__inner {
  color: #e0e0e0;
}

#app-container.dark .el-popper__arrow::before {
  background: #1a1a2e;
  border-color: #2a2a40;
}

#app-container.dark .el-divider__text {
  background-color: #1a1a2e;
  color: #8080a0;
}

#app-container.dark .el-timeline-item__content {
  color: #c0c0d0;
}

#app-container.dark .el-statistic__number {
  color: #e0e0e0;
}

#app-container.dark .el-tag {
  --el-tag-border-color: transparent;
}

#app-container.dark .el-segmented {
  --el-segmented-bg-color: #252540;
}

#app-container.dark .el-switch__label {
  color: #c0c0d0;
}

#app-container.dark .el-upload-list__item {
  background-color: #252540;
  border-color: #3a3a50;
}

#app-container.dark .el-skeleton__item {
  background: linear-gradient(90deg, #252540 25%, #333350 37%, #252540 63%);
}

#app-container.dark .el-radio-button__inner {
  background: #252540;
  border-color: #3a3a50;
  color: #c0c0d0;
}

#app-container.dark .el-radio-button__inner:hover {
  color: #5b9cf5;
}

#app-container.dark .el-radio-button__orig-radio:checked + .el-radio-button__inner {
  background: var(--brand-color);
  border-color: var(--brand-color);
  color: #fff;
}

#app-container.dark .el-tooltip__popper.is-dark {
  background: #303050;
  color: #e0e0e0;
}

#app-container.dark .el-descriptions__title {
  color: #e0e0e0;
}

#app-container.dark .el-descriptions__body .el-descriptions__table.is-bordered .el-descriptions__cell {
  background: #1a1a2e;
}

#app-container.dark .el-statistic__title {
  color: #8080a0;
}

#app-container.dark .el-progress-bar__outer {
  background-color: #252540;
}

#app-container.dark .el-avatar {
  --el-avatar-bg-color: #3a3a50;
}

#app-container.dark .el-image__error {
  background: #252540;
  color: #8080a0;
}

#app-container.dark .el-image-viewer__canvas {
  background: rgba(0,0,0,0.8);
}

#app-container.dark .el-switch.is-checked .el-switch__core {
  border-color: var(--brand-color);
  background-color: var(--brand-color);
}

#app-container.dark .el-switch__core {
  border-color: #4a4a60;
  background-color: #3a3a50;
}

#app-container.dark .el-empty__image svg {
  fill: #3a3a50;
}

#app-container.dark .el-table__empty-text,
#app-container.dark .el-table__empty-block {
  --el-table-text-color: #707080;
  background-color: #1a1a2e;
}

#app-container.dark .el-tabs__active-bar {
  background-color: #5b9cf5;
}

#app-container.dark .el-tabs__nav-wrap::after {
  background-color: #2a2a40;
}

#app-container.dark .el-loading-mask {
  background-color: rgba(15, 15, 26, 0.8);
}

#app-container.dark .el-overlay {
  background-color: rgba(0, 0, 0, 0.6);
}
</style>
