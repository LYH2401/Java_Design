<template>
  <div class="chat-view">
    <aside class="sidebar" :class="{ collapsed: sidebarCollapsed }">
      <div class="sidebar-header">
        <h3>校园智能服务小助手</h3>
        <el-button
          type="primary"
          size="small"
          circle
          @click="newConversation"
          title="新建对话"
        >
          <el-icon><Plus /></el-icon>
        </el-button>
      </div>

      <!-- API 配置区域 -->
      <div class="api-section">
        <template v-if="apiConfigured">
          <div class="api-status">
            <el-tag type="success" size="small" effect="plain">API 已配置</el-tag>
            <span v-if="userModelName" class="api-model">{{ userModelName }}</span>
            <el-button size="small" text type="primary" @click="openApiDialog?.()" title="修改配置">
              <el-icon><EditPen /></el-icon>
            </el-button>
            <el-button size="small" text type="danger" @click="clearApiConfig?.()" title="删除配置">
              <el-icon><Delete /></el-icon>
            </el-button>
          </div>
        </template>
        <template v-else>
          <div class="api-setup-card" @click="openApiDialog?.()">
            <span class="api-setup-icon">⚙️</span>
            <span>配置 API Key 以开始对话</span>
          </div>
        </template>
      </div>

      <div class="mode-switch">
        <el-segmented v-model="chatMode" :options="modeOptions" size="small" block />
      </div>

      <div class="conversation-list">
        <div
          v-for="conv in conversations"
          :key="conv.id"
          class="conv-item"
          :class="{ active: conv.id === currentConversationId }"
          @click="switchConversation(conv)"
        >
          <span class="conv-title">{{ conv.title || '新对话' }}</span>
          <el-tag v-if="conv.conversationMode === 'INCOGNITO'" size="small" type="danger" class="incognito-tag">无痕</el-tag>
          <el-button
            class="conv-delete"
            size="small"
            text
            type="danger"
            :icon="Delete"
            @click.stop="handleDeleteConversation(conv.id)"
          />
        </div>
        <el-empty v-if="conversations.length === 0" description="暂无对话" :image-size="60" />
      </div>

      <div class="sidebar-footer">
        <el-button text @click="sidebarCollapsed = !sidebarCollapsed">
          <el-icon><Fold /></el-icon>
        </el-button>
      </div>
    </aside>

    <main class="chat-main">
      <header class="chat-header">
        <el-button text @click="sidebarCollapsed = !sidebarCollapsed" class="toggle-btn" :class="{ 'always-show': sidebarCollapsed }">
          <el-icon><Expand /></el-icon>
        </el-button>
        <span class="current-title" v-if="currentConversationId">{{ currentConversationTitle }}</span>
        <span class="current-title" v-else>校园智能服务小助手</span>
        <el-tag v-if="chatMode === 'agent'" type="warning" size="small">Agent 模式</el-tag>
        <el-tag v-else-if="chatMode === 'rag-agent'" type="success" size="small">RAG+Agent</el-tag>
        <el-tag v-else-if="chatMode === 'rag'" type="info" size="small">RAG 模式</el-tag>
      </header>

      <div class="message-area" ref="messageArea">
        <div v-if="!apiConfigured && !currentConversationId" class="welcome">
          <div class="welcome-icon">🔑</div>
          <h1>欢迎使用校园智能服务小助手</h1>
          <p>请先在右上角或左侧配置您的 API Key，即可开始对话</p>
          <el-button type="primary" size="small" round @click="openApiDialog?.()">
            立即配置 API Key
          </el-button>
        </div>

        <div v-else-if="!currentConversationId" class="welcome">
          <h1>校园智能服务小助手 🎓</h1>
          <p>你好！我可以帮你查询课表、找空教室、校园导航、办事流程等~</p>
          <div class="quick-actions">
            <el-button
              v-for="q in quickQuestions"
              :key="q"
              size="small"
              round
              @click="sendMessage(q)"
            >{{ q }}</el-button>
          </div>
        </div>

        <div
          v-for="(msg, idx) in messages"
          :key="idx"
          class="message"
          :class="msg.role === 'USER' ? 'user-msg' : 'assistant-msg'"
        >
          <div class="msg-avatar">
            <el-avatar
              :size="32"
              :icon="msg.role === 'USER' ? UserFilled : Service"
            />
          </div>
          <div class="msg-content">
            <div class="msg-text" v-html="renderMsg(msg)"></div>
            <div v-if="getRepairConfirm(msg)" class="repair-confirm-card">
              <div class="repair-confirm-header">🛠️ 报修确认</div>
              <div class="repair-confirm-item" v-if="getRepairConfirm(msg).title">
                <span class="repair-confirm-label">标题：</span>{{ getRepairConfirm(msg).title }}
              </div>
              <div class="repair-confirm-item" v-if="getRepairConfirm(msg).location">
                <span class="repair-confirm-label">地点：</span>{{ getRepairConfirm(msg).location }}
              </div>
              <div class="repair-confirm-item" v-if="getRepairConfirm(msg).urgencyLevel">
                <span class="repair-confirm-label">紧急程度：</span>
                <el-tag :type="repairUrgencyTag(getRepairConfirm(msg).urgencyLevel)" size="small">
                  {{ getRepairConfirm(msg).urgencyLevel }}
                </el-tag>
              </div>
              <div class="repair-confirm-actions">
                <el-button type="primary" size="small" @click="confirmRepair(msg)">
                  确认提交
                </el-button>
                <el-button size="small" @click="cancelRepair()">取消</el-button>
              </div>
            </div>
            <div v-if="msg.sources && msg.sources.length" class="msg-sources">
              <span class="source-label">📚 参考来源：</span>
              <el-tag
                v-for="(s, i) in msg.sources"
                :key="i"
                size="small"
                type="info"
              >{{ s }}</el-tag>
            </div>
          </div>
        </div>

        <div v-if="streaming" class="message assistant-msg">
          <div class="msg-avatar">
            <el-avatar :size="32" :icon="Service" />
          </div>
          <div class="msg-content">
            <div class="msg-text streaming-text" v-html="renderMsg({ content: streamContent })"></div>
            <div v-if="toolCallingHint" class="tool-hint">
              <el-icon class="is-loading"><Loading /></el-icon>
              {{ toolCallingHint }}
            </div>
          </div>
        </div>
      </div>

      <footer class="input-area">
        <div class="input-top">
          <div class="repair-chips">
            <span class="chips-label">快捷报修：</span>
            <el-tag
              v-for="c in repairChips"
              :key="c.label"
              :type="c.type"
              size="small"
              class="repair-chip"
              @click="sendMessage(c.prompt)"
            >{{ c.icon }} {{ c.label }}</el-tag>
          </div>
        </div>
        <div class="input-row">
        <el-input
          v-model="inputText"
          type="textarea"
          :rows="2"
          placeholder="输入你的问题，Enter 发送，Shift+Enter 换行..."
          resize="none"
          :disabled="loading"
          @keydown.enter.exact.prevent="sendMessage(inputText)"
        />
        <el-button
          type="primary"
          :loading="loading"
          :disabled="!inputText.trim()"
          @click="sendMessage(inputText)"
        >
          <el-icon><Promotion /></el-icon>
        </el-button>
        </div>
      </footer>
    </main>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted, onUnmounted, inject, computed } from 'vue'
import { Plus, Delete, Fold, Expand, Promotion, Loading, UserFilled, Service, EditPen } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getConversations, createConversation, getMessages, deleteConversation } from '../api/chat'
import { renderMarkdown } from '../utils/markdown'

const apiForm = inject('apiConfig')
const apiConfigured = inject('apiConfigured')
const openApiDialog = inject('openApiDialog')
const clearApiConfig = inject('clearApiConfig')

function getApiConfig() {
  if (!apiConfigured || !apiConfigured.value || !apiForm) return null
  const saved = localStorage.getItem('campus_api_config')
  if (!saved) return null
  try {
    const config = JSON.parse(saved)
    if (config.apiKey && config.baseUrl) return config
  } catch (_) {}
  return null
}

const userModelName = computed(() => {
  const config = getApiConfig()
  return config?.model || ''
})

// ---- 状态 ----
const conversations = ref([])
const currentConversationId = ref(null)
const messages = ref([])
const inputText = ref('')
const loading = ref(false)
const streaming = ref(false)
const streamContent = ref('')
const toolCallingHint = ref('')
const sidebarCollapsed = ref(false)
const chatMode = ref('agent')
const selectedModel = ref('dashscope')

const modeOptions = [
  { value: 'normal', label: '普通' },
  { value: 'rag', label: 'RAG' },
  { value: 'agent', label: 'Agent' },
  { value: 'rag-agent', label: 'RAG+Agent' }
]

const quickQuestions = [
  '图书馆在哪里？',
  '查一下课表',
  '校园卡怎么充值？',
  '从教学楼到食堂怎么走？'
]

const repairChips = [
  { icon: '🏠', label: '宿舍报修', type: '', prompt: '我宿舍的水龙头漏水了，需要报修。' },
  { icon: '💡', label: '电器维修', type: 'warning', prompt: '教室的灯坏了，需要维修。' },
  { icon: '💧', label: '水电问题', type: '', prompt: '卫生间水管堵了，需要疏通。' },
  { icon: '🌐', label: '网络故障', type: '', prompt: '宿舍网络一直掉线，需要报修。' }
]

const currentConversationTitle = ref('')

// ---- 生命周期 ----
onMounted(async () => {
  await loadConversations()
  if (conversations.value.length > 0) {
    switchConversation(conversations.value[0])
  } else {
    await newConversation()
  }
  // 页面关闭时清理无痕会话
  window.addEventListener('beforeunload', cleanupIncognitoOnClose)
})

onUnmounted(() => {
  window.removeEventListener('beforeunload', cleanupIncognitoOnClose)
})

// ---- 页面关闭清理 ----
function cleanupIncognitoOnClose() {
  conversations.value.forEach(c => {
    if (c.conversationMode === 'INCOGNITO' || c.threadId === 'INCOGNITO') {
      try {
        navigator.sendBeacon(`/api/conversations/${c.id}`, new Blob())
      } catch (_) {}
    }
  })
}

// ---- 方法 ----
async function loadConversations() {
  try {
    const res = await getConversations()
    conversations.value = res?.data || []
  } catch (e) {
    ElMessage.error('加载会话列表失败: ' + e.message)
    conversations.value = []
  }
}

async function newConversation() {
  try {
    const res = await createConversation('NORMAL')
    const conv = res?.data
    if (!conv) {
      ElMessage.error('创建会话失败：返回数据为空')
      return
    }
    conversations.value.unshift(conv)
    switchConversation(conv)
  } catch (e) {
    ElMessage.error('创建会话失败: ' + e.message)
  }
}

async function switchConversation(conv) {
  if (!conv || !conv.id) {
    console.warn('switchConversation: conv 无效', conv)
    return
  }
  currentConversationId.value = conv.id
  currentConversationTitle.value = conv.title || '新对话'
  try {
    const res = await getMessages(conv.id)
    messages.value = (res?.data || []).map(m => ({
      ...m,
      sources: m.metadata ? parseSources(m.metadata) : []
    }))
  } catch (e) {
    messages.value = []
  }
  await nextTick()
  scrollToBottom()
}

async function handleDeleteConversation(id) {
  await deleteConversation(id)
  conversations.value = conversations.value.filter(c => c.id !== id)
  if (currentConversationId.value === id) {
    if (conversations.value.length > 0) {
      switchConversation(conversations.value[0])
    } else {
      currentConversationId.value = null
      messages.value = []
    }
  }
}

async function sendMessage(text) {
  const msg = text?.trim()
  if (!msg || loading.value) return

  if (!currentConversationId.value) {
    await newConversation()
  }

  inputText.value = ''
  loading.value = true
  toolCallingHint.value = chatMode.value.includes('agent') ? '思考中...' : ''

  // 添加用户消息
  messages.value.push({ role: 'USER', content: msg })
  await nextTick()
  scrollToBottom()

  // 开始流式输出
  streaming.value = true
  streamContent.value = ''
  toolCallingHint.value = ''
  const isNewConversation = messages.value.length <= 1
  await nextTick()

  const url = getStreamUrl()
  try {
    const apiConfig = getApiConfig()
    const body = {
      conversationId: currentConversationId.value,
      message: msg,
      model: apiConfig ? (userModelName.value || '') : selectedModel.value
    }
    if (apiConfig) {
      body.apiKey = apiConfig.apiKey
      body.baseUrl = apiConfig.baseUrl
    }
    const response = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    })

    if (!response.ok) {
      const errText = await response.text()
      throw new Error(`HTTP ${response.status}: ${errText}`)
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })

      // 解析 SSE 数据
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      for (const line of lines) {
        if (line.startsWith('data:')) {
          const data = line.slice(5).trim()
          if (data && data !== '[DONE]') {
            streamContent.value += data
            // 检测工具调用状态
            if (chatMode.value.includes('agent')) {
              if (data.includes('工具调用') || data.includes('Tool调用')) {
                toolCallingHint.value = '正在查询相关信息...'
              } else if (data.length > 50) {
                toolCallingHint.value = ''
              }
            }
          }
        }
      }
    }

    // 完成：保存 AI 消息
    if (streamContent.value) {
      messages.value.push({
        role: 'ASSISTANT',
        content: streamContent.value,
        sources: []
      })
    }
    // 新对话首条消息：刷新列表获取后端自动生成的标题
    if (isNewConversation) {
      await loadConversations()
      const conv = conversations.value.find(c => c.id === currentConversationId.value)
      if (conv) currentConversationTitle.value = conv.title || '新对话'
    }
  } catch (e) {
    ElMessage.error('请求失败: ' + e.message)
    if (streamContent.value) {
      messages.value.push({
        role: 'ASSISTANT',
        content: streamContent.value + '\n\n[连接中断]',
        sources: []
      })
    }
  } finally {
    streaming.value = false
    streamContent.value = ''
    toolCallingHint.value = ''
    loading.value = false
    await nextTick()
    scrollToBottom()
  }
}

function getStreamUrl() {
  switch (chatMode.value) {
    case 'agent': return '/api/agent/chat'
    case 'rag-agent': return '/api/agent/chat/with-rag'
    case 'rag': return '/api/rag/chat'
    default: return '/api/chat/stream'
  }
}

function renderMsg(msg) {
  if (msg.role === 'USER') {
    return escapeHtml(msg.content)
  }
  return renderMarkdown(msg.content || '')
}

function escapeHtml(text) {
  if (!text) return ''
  return text
    .replace(/&/g, '&')
    .replace(/</g, '<')
    .replace(/>/g, '>')
    .replace(/\n/g, '<br>')
}

function parseSources(meta) {
  if (!meta) return []
  try {
    const obj = typeof meta === 'string' ? JSON.parse(meta) : meta
    if (obj.ragSources) return obj.ragSources
    if (obj.sources) return obj.sources
  } catch (_) {}
  return []
}

function getRepairConfirm(msg) {
  if (!msg || !msg.content) return null
  const match = msg.content.match(/\[REPAIR_CONFIRM\]([\s\S]*?)\[\/REPAIR_CONFIRM\]/)
  if (!match) return null
  const body = match[1]
  const fields = {}
  const lines = body.trim().split('\n')
  for (const line of lines) {
    const kv = line.match(/^(.+?)[：:]\s*(.+)$/)
    if (kv) {
      const key = kv[1].trim()
      const val = kv[2].trim()
      if (key.includes('标题')) fields.title = val
      else if (key.includes('地点')) fields.location = val
      else if (key.includes('描述')) fields.description = val
      else if (key.includes('紧急')) fields.urgencyLevel = val
    }
  }
  return Object.keys(fields).length > 0 ? fields : null
}

function repairUrgencyTag(level) {
  const map = { '紧急': 'danger', 'URGENT': 'danger', '高': 'warning', 'HIGH': 'warning', '普通': '', 'NORMAL': '', '低': 'info', 'LOW': 'info' }
  return map[level] || ''
}

function confirmRepair(msg) {
  sendMessage('确认提交报修')
}

function cancelRepair() {
  sendMessage('取消报修')
}

function scrollToBottom() {
  nextTick(() => {
    const el = document.querySelector('.message-area')
    if (el) el.scrollTop = el.scrollHeight
  })
}
</script>

<style scoped>
.chat-view {
  display: flex;
  height: calc(100vh - 44px);
  width: 100vw;
  background: var(--bg-primary);
  transition: background 0.3s;
}

/* ======== 左侧边栏 ======== */
.sidebar {
  width: 280px;
  min-width: 280px;
  background: var(--sidebar-bg);
  border-right: 1px solid var(--border-color);
  display: flex;
  flex-direction: column;
  transition: all 0.3s;
  overflow: hidden;
}

.sidebar.collapsed {
  width: 0;
  min-width: 0;
}

.sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  border-bottom: 1px solid var(--border-light);
}

.sidebar-header h3 {
  font-size: 15px;
  color: var(--text-primary);
  white-space: nowrap;
}

/* API 配置区域 */
.api-section {
  padding: 10px 16px;
  border-bottom: 1px solid var(--border-light);
}

.api-setup-card {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  background: linear-gradient(135deg, var(--api-card-bg-start), var(--api-card-bg-end));
  border: 1px dashed var(--api-card-border);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.api-setup-card:hover {
  background: linear-gradient(135deg, var(--api-card-hover-bg-start), var(--api-card-hover-bg-end));
  border-color: var(--api-card-hover-border);
}

.api-setup-icon {
  font-size: 18px;
  flex-shrink: 0;
}

.api-setup-card > span:last-child {
  font-size: 13px;
  color: var(--api-card-text);
  font-weight: 500;
}

.api-status {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
}

.api-status .el-button {
  padding: 2px 4px;
  min-height: auto;
}

.api-model {
  font-size: 12px;
  color: #67c23a;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mode-switch {
  padding: 10px 16px;
  border-bottom: 1px solid var(--border-light);
}

.conversation-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.conv-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.2s;
  margin-bottom: 4px;
  gap: 6px;
  color: var(--text-primary);
}

.conv-item:hover {
  background: var(--bg-hover);
}

.conv-item.active {
  background: var(--bg-active);
  color: var(--brand-color);
}

.conv-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
}

.incognito-tag {
  flex-shrink: 0;
}

.conv-delete {
  opacity: 0;
  transition: opacity 0.2s;
  flex-shrink: 0;
}

.conv-item:hover .conv-delete {
  opacity: 1;
}

.sidebar-footer {
  padding: 10px;
  border-top: 1px solid var(--border-light);
  text-align: center;
}

/* ======== 右侧聊天区域 ======== */
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.chat-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 20px;
  background: var(--header-bg);
  border-bottom: 1px solid var(--border-color);
  flex-shrink: 0;
  color: var(--text-primary);
  transition: background 0.3s, border-color 0.3s;
}

.current-title {
  flex: 1;
  font-size: 15px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--text-primary);
}

.toggle-btn {
  display: inline-flex;
}

.message-area {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}

.welcome {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  text-align: center;
}

.welcome h1 {
  font-size: 28px;
  color: var(--text-primary);
  margin-bottom: 12px;
}

.welcome-icon {
  font-size: 48px;
  margin-bottom: 12px;
}

.welcome p {
  color: var(--welcome-text);
  margin-bottom: 24px;
}

.quick-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
  justify-content: center;
}

.quick-actions .el-button,
.quick-questions-row .el-button {
  padding: 10px 18px !important;
  font-size: 14px;
  height: auto;
}

/* ======== 消息气泡 ======== */
.message {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
  animation: fadeIn 0.3s;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}

.user-msg {
  flex-direction: row-reverse;
}

.msg-content {
  max-width: 70%;
}

.msg-text {
  padding: 10px 14px;
  border-radius: 12px;
  line-height: 1.6;
  font-size: 14px;
  word-break: break-word;
}

.user-msg .msg-text {
  background: var(--user-msg-bg);
  color: var(--user-msg-text);
  border-bottom-right-radius: 4px;
}

.assistant-msg .msg-text {
  background: var(--assistant-msg-bg);
  color: var(--assistant-msg-text);
  border-bottom-left-radius: 4px;
  box-shadow: var(--shadow-light);
}

/* Markdown 样式深作用域 */
.msg-text :deep(p) { margin: 0 0 8px; }
.msg-text :deep(p:last-child) { margin-bottom: 0; }
.msg-text :deep(code) {
  background: var(--code-bg);
  padding: 2px 6px;
  border-radius: 3px;
  font-size: 13px;
}
.msg-text :deep(pre) {
  background: #282c34;
  color: #abb2bf;
  padding: 14px;
  border-radius: 8px;
  overflow-x: auto;
  margin: 8px 0;
}
.msg-text :deep(pre code) {
  background: none;
  padding: 0;
  color: inherit;
}
.msg-text :deep(ul), .msg-text :deep(ol) { padding-left: 20px; margin: 8px 0; }
.msg-text :deep(li) { margin-bottom: 4px; }
.msg-text :deep(blockquote) {
  border-left: 3px solid var(--blockquote-border);
  padding-left: 12px;
  color: var(--blockquote-color);
  margin: 8px 0;
}
.msg-text :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 8px 0;
}
.msg-text :deep(th), .msg-text :deep(td) {
  border: 1px solid var(--table-border);
  padding: 6px 10px;
  text-align: left;
}
.msg-text :deep(th) { background: var(--table-header-bg); }

.streaming-text::after {
  content: '\258C';
  animation: blink 1s infinite;
}

@keyframes blink {
  0%, 50% { opacity: 1; }
  51%, 100% { opacity: 0; }
}

.tool-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 6px;
  padding: 6px 12px;
  background: var(--tool-hint-bg);
  color: var(--tool-hint-color);
  border-radius: 8px;
  font-size: 13px;
}

.msg-sources {
  margin-top: 8px;
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.source-label {
  font-size: 12px;
  color: var(--source-label-color);
}

/* ======== 底部输入 ======== */
.input-area {
  display: flex;
  flex-direction: column;
  padding: 10px 20px 14px;
  background: var(--input-bg);
  border-top: 1px solid var(--border-color);
  flex-shrink: 0;
  gap: 8px;
  transition: background 0.3s, border-color 0.3s;
}

.input-top {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.repair-chips {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.chips-label {
  font-size: 12px;
  color: var(--text-tertiary);
}

.repair-chip {
  cursor: pointer;
  transition: opacity 0.2s;
}

.repair-chip:hover {
  opacity: 0.8;
}

.quick-questions-row {
  display: flex;
  gap: 18px;
  flex-wrap: wrap;
}

.input-row {
  display: flex;
  align-items: flex-end;
  gap: 10px;
}

.input-row :deep(.el-textarea__inner) {
  border-radius: 12px;
}

.input-row .el-button {
  border-radius: 12px;
  height: 40px;
  width: 40px;
}

/* ======== 报修确认卡片 ======== */
.repair-confirm-card {
  margin-top: 10px;
  border: 1px solid var(--repair-card-border);
  border-radius: 10px;
  padding: 14px;
  background: var(--repair-card-bg);
}

.repair-confirm-header {
  font-weight: 600;
  font-size: 14px;
  color: var(--tool-hint-color);
  margin-bottom: 10px;
}

.repair-confirm-item {
  font-size: 13px;
  color: var(--text-primary);
  margin-bottom: 6px;
}

.repair-confirm-label {
  color: var(--text-tertiary);
}

.repair-confirm-actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}

.input-area .el-button {
  border-radius: 12px;
  height: 40px;
  width: 40px;
}

/* ======== 响应式 ======== */
@media (max-width: 768px) {
  .sidebar {
    position: fixed;
    left: 0;
    top: 0;
    bottom: 0;
    z-index: 100;
    box-shadow: 2px 0 8px rgba(0,0,0,0.1);
  }

  .sidebar.collapsed {
    width: 0;
    min-width: 0;
  }

  .msg-content {
    max-width: 85%;
  }
}
</style>
