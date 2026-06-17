<template>
  <div class="solver-view">
    <!-- 左侧会话列表 -->
    <aside class="sidebar" :class="{ collapsed: sidebarCollapsed }">
      <div class="sidebar-header">
        <h3>📝 问题求解</h3>
        <el-button type="primary" size="small" circle @click="newConversation" title="新建问题">
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
            <span>配置 API Key 以开始使用</span>
          </div>
        </template>
      </div>

      <div class="conversation-list">
        <div
          v-for="conv in conversations"
          :key="conv.id"
          class="conv-item"
          :class="{ active: conv.id === currentConversationId }"
          @click="switchConversation(conv)"
        >
          <span class="conv-title">{{ conv.title || '新问题' }}</span>
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
        <el-empty v-if="conversations.length === 0" description="暂无问题" :image-size="60" />
      </div>

      <div class="sidebar-footer">
        <el-button text @click="sidebarCollapsed = !sidebarCollapsed">
          <el-icon><Fold /></el-icon>
        </el-button>
      </div>
    </aside>

    <!-- 右侧聊天区域 -->
    <main class="chat-main">
      <header class="chat-header">
        <el-button text @click="sidebarCollapsed = !sidebarCollapsed" class="toggle-btn" :class="{ 'always-show': sidebarCollapsed }">
          <el-icon><Expand /></el-icon>
        </el-button>
        <span class="current-title">问题求解助手</span>
        <el-tag type="success" size="small">解题模式</el-tag>
        <el-tag size="small" style="margin-left: 6px">{{ selectedModel === 'deepseek' ? 'DeepSeek' : '通义千问' }}</el-tag>
      </header>

      <div class="message-area" ref="messageArea">
        <div v-if="!currentConversationId" class="welcome">
          <h1>📝 问题求解助手</h1>
          <p>你好！我是你的学习辅导老师，我会引导你<b>独立思考和分步解题</b>，而不是直接给出答案~</p>
          <div class="tips">
            <h4>💡 使用建议</h4>
            <ul>
              <li>直接输入你的问题，我会引导你逐步思考</li>
              <li>可以上传题目截图（描述题目内容即可）</li>
              <li>跟随引导步骤，尝试自己解答</li>
              <li>支持数学、物理、编程等各学科</li>
            </ul>
          </div>
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
            <el-avatar :size="32" :icon="msg.role === 'USER' ? UserFilled : Service" />
          </div>
          <div class="msg-content">
            <div class="msg-text" v-html="renderMsg(msg)"></div>
          </div>
        </div>

        <div v-if="streaming" class="message assistant-msg">
          <div class="msg-avatar">
            <el-avatar :size="32" :icon="Service" />
          </div>
          <div class="msg-content">
            <div class="msg-text streaming-text" v-html="renderMsg({ content: streamContent })"></div>
          </div>
        </div>
      </div>

      <footer class="input-area">
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
const sidebarCollapsed = ref(false)
const selectedModel = ref('dashscope')

const quickQuestions = [
  '解方程：2x² + 3x - 5 = 0',
  '什么是牛顿第二定律？',
  '写一个 Python 函数判断素数',
  '如何写好英语作文的开头？'
]

onMounted(async () => {
  await loadConversations()
  if (conversations.value.length > 0) {
    switchConversation(conversations.value[0])
  } else {
    await newConversation()
  }
  window.addEventListener('beforeunload', cleanupIncognitoOnClose)
})

onUnmounted(() => {
  window.removeEventListener('beforeunload', cleanupIncognitoOnClose)
})

function cleanupIncognitoOnClose() {
  conversations.value.forEach(c => {
    if (c.conversationMode === 'INCOGNITO' || c.threadId === 'INCOGNITO') {
      try {
        navigator.sendBeacon(`/api/solver/conversations/${c.id}`, new Blob())
      } catch (_) {}
    }
  })
}

async function loadConversations() {
  try {
    const res = await fetch('/api/solver/conversations')
    const data = await res.json()
    conversations.value = data?.data || []
  } catch (e) {
    conversations.value = []
  }
}

async function newConversation() {
  try {
    const res = await fetch('/api/solver/conversations?firstMessage=新问题&mode=NORMAL', { method: 'POST' })
    const data = await res.json()
    const conv = data?.data
    if (!conv) {
      ElMessage.error('创建会话失败')
      return
    }
    conversations.value.unshift(conv)
    switchConversation(conv)
  } catch (e) {
    ElMessage.error('创建会话失败: ' + e.message)
  }
}

async function switchConversation(conv) {
  if (!conv || !conv.id) return
  currentConversationId.value = conv.id
  try {
    const res = await fetch(`/api/solver/conversations/${conv.id}/messages`)
    const data = await res.json()
    messages.value = (data?.data || []).map(m => ({ ...m, sources: [] }))
  } catch (e) {
    messages.value = []
  }
  await nextTick()
  scrollToBottom()
}

async function handleDeleteConversation(id) {
  await fetch(`/api/solver/conversations/${id}`, { method: 'DELETE' })
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

  if (!currentConversationId.value) await newConversation()

  inputText.value = ''
  loading.value = true

  messages.value.push({ role: 'USER', content: msg })
  await nextTick()
  scrollToBottom()

  streaming.value = true
  streamContent.value = ''
  const isNewConversation = messages.value.length === 1

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
    const response = await fetch('/api/solver/solve', {
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

      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      for (const line of lines) {
        if (line.startsWith('data:')) {
          const data = line.slice(5).trim()
          if (data && data !== '[DONE]') {
            streamContent.value += data
          }
        }
      }
    }

    if (streamContent.value) {
      messages.value.push({ role: 'ASSISTANT', content: streamContent.value, sources: [] })
    }
    if (isNewConversation) {
      await loadConversations()
      const conv = conversations.value.find(c => c.id === currentConversationId.value)
      if (conv) currentConversationTitle.value = conv.title || '新对话'
    }
  } catch (e) {
    ElMessage.error('请求失败: ' + e.message)
    if (streamContent.value) {
      messages.value.push({ role: 'ASSISTANT', content: streamContent.value + '\n\n[连接中断]', sources: [] })
    }
  } finally {
    streaming.value = false
    streamContent.value = ''
    loading.value = false
    await nextTick()
    scrollToBottom()
  }
}

function renderMsg(msg) {
  if (msg.role === 'USER') return escapeHtml(msg.content)
  return renderMarkdown(msg.content || '')
}

function escapeHtml(text) {
  if (!text) return ''
  return text.replace(/&/g, '&').replace(/</g, '<').replace(/>/g, '>').replace(/\n/g, '<br>')
}

function scrollToBottom() {
  nextTick(() => {
    const el = document.querySelector('.solver-view .message-area')
    if (el) el.scrollTop = el.scrollHeight
  })
}
</script>

<style scoped>
.solver-view {
  display: flex;
  height: calc(100vh - 44px);
  width: 100vw;
  background: var(--solver-bg);
  transition: background 0.3s;
}

.sidebar {
  width: 280px;
  min-width: 280px;
  background: var(--bg-secondary);
  border-right: 1px solid var(--border-color);
  display: flex;
  flex-direction: column;
  transition: all 0.3s, background 0.3s;
  overflow: hidden;
}

.sidebar.collapsed { width: 0; min-width: 0; }

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

.api-setup-icon { font-size: 18px; flex-shrink: 0; }

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

.conversation-list { flex: 1; overflow-y: auto; padding: 8px; }

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

.conv-item:hover { background: var(--bg-hover); }
.conv-item.active { background: var(--solver-accent-light); color: var(--solver-accent); }

.conv-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
}

.incognito-tag { flex-shrink: 0; }

.conv-delete { opacity: 0; transition: opacity 0.2s; flex-shrink: 0; }
.conv-item:hover .conv-delete { opacity: 1; }

.sidebar-footer {
  padding: 12px;
  border-top: 1px solid var(--border-light);
  display: flex;
  justify-content: center;
}

.chat-main { flex: 1; display: flex; flex-direction: column; min-width: 0; }

.chat-header {
  display: flex;
  align-items: center;
  padding: 12px 20px;
  background: var(--bg-secondary);
  border-bottom: 1px solid var(--solver-accent-light);
  gap: 10px;
  transition: background 0.3s;
}

.toggle-btn { flex-shrink: 0; }
.always-show { display: inline-flex !important; }

.current-title {
  flex: 1;
  font-size: 16px;
  font-weight: 500;
  color: var(--text-primary);
}

.message-area { flex: 1; overflow-y: auto; padding: 20px; }

.welcome { text-align: center; padding-top: 80px; }

.welcome h1 {
  font-size: 28px;
  color: var(--solver-accent);
  margin-bottom: 12px;
}

.welcome p {
  color: var(--text-secondary);
  margin-bottom: 24px;
  line-height: 1.6;
}

.welcome .tips {
  text-align: left;
  background: var(--bg-secondary);
  border-radius: 12px;
  padding: 20px 24px;
  margin: 0 auto 24px;
  max-width: 480px;
  box-shadow: var(--shadow-light);
}

.welcome .tips h4 { margin-bottom: 10px; color: var(--solver-accent); }

.welcome .tips ul {
  padding-left: 20px;
  line-height: 2;
  color: var(--text-secondary);
}

.quick-actions { display: flex; gap: 8px; justify-content: center; flex-wrap: wrap; }

.message { display: flex; gap: 12px; margin-bottom: 20px; }
.user-msg { flex-direction: row-reverse; }
.msg-avatar { flex-shrink: 0; }

.msg-content {
  max-width: 75%;
  background: var(--assistant-msg-bg);
  border-radius: 12px;
  padding: 12px 16px;
  box-shadow: var(--shadow-light);
  color: var(--text-primary);
}

.user-msg .msg-content {
  background: var(--solver-accent);
  color: #fff;
}

.msg-text { font-size: 14px; line-height: 1.7; word-break: break-word; }

.msg-text :deep(p) { margin: 4px 0; }
.msg-text :deep(pre) {
  background: var(--bg-primary);
  border-radius: 8px;
  padding: 12px;
  overflow-x: auto;
  font-size: 13px;
  color: var(--text-primary);
}

.streaming-text { color: var(--text-tertiary); }

.input-area {
  display: flex;
  gap: 10px;
  padding: 16px 20px;
  background: var(--bg-secondary);
  border-top: 1px solid var(--solver-accent-light);
  align-items: flex-end;
  transition: background 0.3s;
}

.input-area :deep(.el-textarea__inner) { border-radius: 10px; }
</style>
